package io.github.kreza6173pixel.pulsebattery.ui.appops

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.kreza6173pixel.pulsebattery.R
import io.github.kreza6173pixel.pulsebattery.appops.AppOpMode
import io.github.kreza6173pixel.pulsebattery.appops.AppOpsRepository
import io.github.kreza6173pixel.pulsebattery.appops.AppOpsSnapshot
import io.github.kreza6173pixel.pulsebattery.appops.BatteryAppOp
import io.github.kreza6173pixel.pulsebattery.appops.OpResult
import io.github.kreza6173pixel.pulsebattery.diag.DiagResult
import io.github.kreza6173pixel.pulsebattery.exec.ConnectionState
import io.github.kreza6173pixel.pulsebattery.exec.ExecBridge
import io.github.kreza6173pixel.pulsebattery.ui.common.CopyShareButtons
import io.github.kreza6173pixel.pulsebattery.ui.common.LtrMonoText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Per-app wakelock and background-work control through `cmd appops`.
 *
 * Pick an app, then set each op. The write is always followed by a read-back and the result
 * card reports exactly what the system answered.
 */
@Composable
fun AppOpsScreen(bridge: ExecBridge, modifier: Modifier = Modifier) {
    val repo = remember(bridge) { AppOpsRepository(bridge) }
    val scope = rememberCoroutineScope()
    var packages by remember { mutableStateOf<DiagResult<List<String>>?>(null) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<String?>(null) }
    var detail by remember { mutableStateOf<DiagResult<AppOpsSnapshot>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var last by remember { mutableStateOf<OpResult?>(null) }
    var reloadTick by remember { mutableStateOf(0) }
    val connected = bridge.connectionState == ConnectionState.CONNECTED

    LaunchedEffect(connected) {
        if (!connected) return@LaunchedEffect
        packages = withContext(Dispatchers.IO) { repo.packages() }
    }

    LaunchedEffect(selected, reloadTick, connected) {
        val pkg = selected
        if (!connected || pkg == null) {
            detail = null
            return@LaunchedEffect
        }
        detail = withContext(Dispatchers.IO) { repo.load(pkg) }
    }

    fun act(block: () -> OpResult) {
        if (busy) return
        scope.launch {
            busy = true
            last = withContext(Dispatchers.IO) { block() }
            busy = false
            reloadTick++
        }
    }

    val list = packages
    val needle = query.trim()
    val rows: List<String> = if (list is DiagResult.Ok) {
        list.value.filter { needle.isEmpty() || it.contains(needle, ignoreCase = true) }
    } else {
        emptyList()
    }
    val enabled = connected && !busy
    val result = last
    val chosen = selected
    val snapshot = detail

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!connected) {
            item { Text(stringResource(R.string.diag_waiting)) }
        }
        item {
            Text(
                text = stringResource(R.string.appops_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (busy) {
            item { Text(stringResource(R.string.appops_working)) }
        }
        if (result != null) {
            item { ResultCard(result) }
        }
        if (chosen == null) {
            item { Text(stringResource(R.string.appops_pick)) }
        } else {
            item {
                OpsCard(
                    pkg = chosen,
                    snapshot = snapshot,
                    enabled = enabled,
                    onSet = { op, mode -> act { repo.setMode(chosen, op, mode) } },
                    onReset = { act { repo.resetAll(chosen) } },
                    onClear = { selected = null },
                )
            }
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                label = { Text(stringResource(R.string.appops_search)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Text(
                text = stringResource(R.string.appops_apps, rows.size),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        when (list) {
            null -> item { Text(stringResource(R.string.diag_loading)) }
            is DiagResult.Error -> item {
                Text(
                    text = stringResource(R.string.diag_error, list.message),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            is DiagResult.Ok -> Unit
        }
        items(rows, key = { it }) { pkg ->
            TextButton(
                onClick = { selected = pkg },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                LtrMonoText(text = pkg, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun ResultCard(result: OpResult) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)) {
            Text(
                text = stringResource(
                    if (result.ok) R.string.appops_applied else R.string.appops_not_applied,
                ),
                style = MaterialTheme.typography.titleSmall,
                color = if (result.ok) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
            LtrMonoText(result.message)
            CopyShareButtons(result.message)
        }
    }
}

@Composable
private fun OpsCard(
    pkg: String,
    snapshot: DiagResult<AppOpsSnapshot>?,
    enabled: Boolean,
    onSet: (BatteryAppOp, AppOpMode) -> Unit,
    onReset: () -> Unit,
    onClear: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LtrMonoText(
                text = stringResource(R.string.appops_selected, pkg),
                style = MaterialTheme.typography.titleSmall,
            )
            when (snapshot) {
                null -> Text(stringResource(R.string.diag_loading))
                is DiagResult.Error -> Text(
                    text = stringResource(R.string.diag_error, snapshot.message),
                    color = MaterialTheme.colorScheme.error,
                )
                is DiagResult.Ok -> {
                    val snap = snapshot.value
                    BatteryAppOp.entries.forEach { op ->
                        OpRow(
                            op = op,
                            current = snap.modeOf(op),
                            enabled = enabled,
                            onSet = { mode -> onSet(op, mode) },
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = onReset, enabled = enabled, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.appops_reset))
                }
                TextButton(onClick = onClear) { Text(stringResource(R.string.action_close)) }
            }
        }
    }
}

@Composable
private fun OpRow(
    op: BatteryAppOp,
    current: AppOpMode,
    enabled: Boolean,
    onSet: (AppOpMode) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(opRes(op)),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Box {
            OutlinedButton(onClick = { menuOpen = true }, enabled = enabled) {
                Text(stringResource(R.string.appops_mode_label, stringResource(modeRes(current))))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                AppOpMode.OFFERED.forEach { mode ->
                    DropdownMenuItem(
                        text = { Text(stringResource(modeRes(mode))) },
                        onClick = {
                            menuOpen = false
                            if (mode != current) onSet(mode)
                        },
                    )
                }
            }
        }
    }
}

private fun opRes(op: BatteryAppOp): Int = when (op) {
    BatteryAppOp.WAKE_LOCK -> R.string.appops_op_wake_lock
    BatteryAppOp.RUN_IN_BACKGROUND -> R.string.appops_op_run_in_background
    BatteryAppOp.RUN_ANY_IN_BACKGROUND -> R.string.appops_op_run_any_in_background
}

private fun modeRes(mode: AppOpMode): Int = when (mode) {
    AppOpMode.ALLOW -> R.string.appops_mode_allow
    AppOpMode.IGNORE -> R.string.appops_mode_ignore
    AppOpMode.DENY -> R.string.appops_mode_deny
    AppOpMode.DEFAULT -> R.string.appops_mode_default
    AppOpMode.FOREGROUND -> R.string.appops_mode_foreground
}

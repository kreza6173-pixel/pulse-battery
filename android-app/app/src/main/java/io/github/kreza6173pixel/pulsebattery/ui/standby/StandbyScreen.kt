package io.github.kreza6173pixel.pulsebattery.ui.standby

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
import androidx.compose.material3.Switch
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
import io.github.kreza6173pixel.pulsebattery.diag.DiagResult
import io.github.kreza6173pixel.pulsebattery.exec.ConnectionState
import io.github.kreza6173pixel.pulsebattery.exec.ExecBridge
import io.github.kreza6173pixel.pulsebattery.standby.ActionResult
import io.github.kreza6173pixel.pulsebattery.standby.AppStandbyRow
import io.github.kreza6173pixel.pulsebattery.standby.ExemptSource
import io.github.kreza6173pixel.pulsebattery.standby.StandbyBucket
import io.github.kreza6173pixel.pulsebattery.standby.StandbyRepository
import io.github.kreza6173pixel.pulsebattery.standby.StandbyRootPolicy
import io.github.kreza6173pixel.pulsebattery.standby.StandbySnapshot
import io.github.kreza6173pixel.pulsebattery.ui.common.CopyShareButtons
import io.github.kreza6173pixel.pulsebattery.ui.common.LtrMonoText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** M4: standby buckets, Doze whitelist and forced idle. Every write is read back. */
@Composable
fun StandbyScreen(
    bridge: ExecBridge,
    rootAvailable: Boolean,
    modifier: Modifier = Modifier,
) {
    val repo = remember(bridge) { StandbyRepository(bridge) }
    val scope = rememberCoroutineScope()
    var snapshot by remember { mutableStateOf<DiagResult<StandbySnapshot>?>(null) }
    var includeSystem by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var lastAction by remember { mutableStateOf<ActionResult?>(null) }
    var reloadTick by remember { mutableStateOf(0) }
    val connected = bridge.connectionState == ConnectionState.CONNECTED

    LaunchedEffect(connected, includeSystem, reloadTick) {
        if (!connected) return@LaunchedEffect
        snapshot = withContext(Dispatchers.IO) { repo.load(includeSystem) }
    }

    fun act(block: () -> ActionResult) {
        if (busy) return
        scope.launch {
            busy = true
            lastAction = withContext(Dispatchers.IO) { block() }
            busy = false
            reloadTick++
        }
    }

    val snap = snapshot
    val needle = query.trim()
    val rows: List<AppStandbyRow> = if (snap is DiagResult.Ok) {
        snap.value.rows.filter { needle.isEmpty() || it.pkg.contains(needle, ignoreCase = true) }
    } else {
        emptyList()
    }
    val action = lastAction
    val enabled = connected && !busy

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!connected) {
            item { Text(stringResource(R.string.diag_waiting)) }
        }
        item {
            DozeCard(
                snap = snap,
                enabled = enabled,
                onForce = { act { repo.forceIdle() } },
                onUnforce = { act { repo.unforce() } },
            )
        }
        if (rootAvailable) {
            item {
                Text(
                    text = stringResource(R.string.standby_force_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (busy) {
            item { Text(stringResource(R.string.standby_working)) }
        }
        if (action != null) {
            item {
                ActionCard(
                    a = action,
                    enabled = enabled,
                    onRevert = { pkg, prev -> act { repo.setBucket(pkg, prev) } },
                )
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.standby_show_system),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = includeSystem,
                    onCheckedChange = { includeSystem = it },
                    enabled = enabled,
                )
            }
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                label = { Text(stringResource(R.string.standby_search)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.standby_apps, rows.size),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (snap != null) CopyShareButtons(snap.raw)
            }
        }
        when (snap) {
            null -> item { Text(stringResource(R.string.diag_loading)) }
            is DiagResult.Error -> item {
                Text(
                    text = stringResource(R.string.diag_error, snap.message),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            is DiagResult.Ok -> Unit
        }
        items(rows, key = { it.pkg }) { row ->
            AppRow(
                row = row,
                enabled = enabled,
                rootAvailable = rootAvailable,
                onBucket = { b -> act { repo.setBucket(row.pkg, b) } },
                onForceBucket = { b -> act { repo.setBucketForced(row, b, rootAvailable) } },
                onWhitelist = { on -> act { repo.setUserWhitelisted(row.pkg, on) } },
            )
        }
    }
}

@Composable
private fun DozeCard(
    snap: DiagResult<StandbySnapshot>?,
    enabled: Boolean,
    onForce: () -> Unit,
    onUnforce: () -> Unit,
) {
    val deep = if (snap is DiagResult.Ok) snap.value.deepState else null
    val light = if (snap is DiagResult.Ok) snap.value.lightState else null
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.standby_doze_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.standby_doze_state, deep ?: "?", light ?: "?"),
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = onForce, enabled = enabled, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.standby_force_idle))
                }
                OutlinedButton(onClick = onUnforce, enabled = enabled, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.standby_unforce))
                }
            }
            Text(
                text = stringResource(R.string.standby_doze_hint),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ActionCard(
    a: ActionResult,
    enabled: Boolean,
    onRevert: (String, StandbyBucket) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)) {
            Text(
                text = stringResource(if (a.ok) R.string.standby_applied else R.string.standby_not_applied),
                style = MaterialTheme.typography.titleSmall,
                color = if (a.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
            LtrMonoText(a.message)
            CopyShareButtons(a.message)
            val pkg = a.pkg
            val prev = a.previousBucket
            if (a.ok && pkg != null && prev != null && prev.settable) {
                TextButton(onClick = { onRevert(pkg, prev) }, enabled = enabled) {
                    Text(stringResource(R.string.standby_revert_to, stringResource(bucketRes(prev))))
                }
            }
        }
    }
}

@Composable
private fun AppRow(
    row: AppStandbyRow,
    enabled: Boolean,
    rootAvailable: Boolean,
    onBucket: (StandbyBucket) -> Unit,
    onForceBucket: (StandbyBucket) -> Unit,
    onWhitelist: (Boolean) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var forceMenuOpen by remember { mutableStateOf(false) }
    val current = row.bucket
    val code = row.bucketCode
    // A code outside the seven framework constants (or none at all) is shown raw and stays
    // changeable; only EXEMPTED and NEVER are locked by the system.
    val bucketText = when {
        current != null -> stringResource(bucketRes(current))
        code != null -> stringResource(R.string.standby_bucket_code, code)
        else -> stringResource(R.string.diag_unknown)
    }
    val changeable = current == null || current.settable
    val source = StandbyRootPolicy.exemptSource(row)
    val held = source != ExemptSource.NONE
    val showForce = rootAvailable && (!changeable || held)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 10.dp)) {
            LtrMonoText(text = row.pkg, style = MaterialTheme.typography.bodyMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { menuOpen = true },
                        enabled = enabled && changeable,
                    ) {
                        Text(stringResource(R.string.standby_bucket_label, bucketText))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        StandbyBucket.SETTABLE.forEach { b ->
                            DropdownMenuItem(
                                text = { Text(stringResource(bucketRes(b))) },
                                onClick = {
                                    menuOpen = false
                                    if (b != current) onBucket(b)
                                },
                            )
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = stringResource(R.string.standby_whitelist),
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Switch(
                        checked = row.userWhitelisted || row.systemWhitelisted,
                        onCheckedChange = onWhitelist,
                        enabled = enabled && !row.systemWhitelisted,
                    )
                }
            }
            if (showForce) {
                Box {
                    OutlinedButton(onClick = { forceMenuOpen = true }, enabled = enabled) {
                        Text(stringResource(R.string.standby_force_bucket))
                    }
                    DropdownMenu(
                        expanded = forceMenuOpen,
                        onDismissRequest = { forceMenuOpen = false },
                    ) {
                        StandbyBucket.SETTABLE.forEach { b ->
                            DropdownMenuItem(
                                text = { Text(stringResource(bucketRes(b))) },
                                onClick = {
                                    forceMenuOpen = false
                                    onForceBucket(b)
                                },
                            )
                        }
                    }
                }
            }
            if (held) {
                Text(
                    text = stringResource(sourceRes(source)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!changeable && !rootAvailable) {
                Text(
                    text = stringResource(R.string.standby_locked_no_root),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (row.systemWhitelisted) {
                Text(
                    text = stringResource(R.string.standby_whitelist_system),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun sourceRes(s: ExemptSource): Int = when (s) {
    ExemptSource.USER_WHITELIST -> R.string.standby_source_user
    ExemptSource.SYSTEM_WHITELIST -> R.string.standby_source_system
    ExemptSource.FRAMEWORK -> R.string.standby_source_framework
    ExemptSource.NONE -> R.string.standby_source_none
}

private fun bucketRes(b: StandbyBucket?): Int = when (b) {
    StandbyBucket.EXEMPTED -> R.string.bucket_exempted
    StandbyBucket.ACTIVE -> R.string.bucket_active
    StandbyBucket.WORKING_SET -> R.string.bucket_working_set
    StandbyBucket.FREQUENT -> R.string.bucket_frequent
    StandbyBucket.RARE -> R.string.bucket_rare
    StandbyBucket.RESTRICTED -> R.string.bucket_restricted
    StandbyBucket.NEVER -> R.string.bucket_never
    null -> R.string.diag_unknown
}

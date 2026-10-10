package io.github.kreza6173pixel.pulsebattery.ui.charge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.kreza6173pixel.pulsebattery.R
import io.github.kreza6173pixel.pulsebattery.charge.ChargeActionResult
import io.github.kreza6173pixel.pulsebattery.charge.ChargeMode
import io.github.kreza6173pixel.pulsebattery.charge.ChargeRepository
import io.github.kreza6173pixel.pulsebattery.charge.ChargeState
import io.github.kreza6173pixel.pulsebattery.charge.ChargeSupport
import io.github.kreza6173pixel.pulsebattery.diag.DiagResult
import io.github.kreza6173pixel.pulsebattery.exec.ConnectionState
import io.github.kreza6173pixel.pulsebattery.exec.ExecBridge
import io.github.kreza6173pixel.pulsebattery.ui.common.CopyShareButtons
import io.github.kreza6173pixel.pulsebattery.ui.common.LtrMonoText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ROM charging mode. Shell access is enough, so this screen behaves the same in shell and in
 * root mode. Every change is read back before it is called applied.
 */
@Composable
fun ChargeScreen(bridge: ExecBridge, modifier: Modifier = Modifier) {
    val repo = remember(bridge) { ChargeRepository(bridge) }
    val scope = rememberCoroutineScope()
    var probe by remember { mutableStateOf<DiagResult<ChargeState>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var lastAction by remember { mutableStateOf<ChargeActionResult?>(null) }
    var reloadTick by remember { mutableStateOf(0) }
    val connected = bridge.connectionState == ConnectionState.CONNECTED

    LaunchedEffect(connected, reloadTick) {
        if (!connected) return@LaunchedEffect
        probe = withContext(Dispatchers.IO) { repo.read() }
    }

    fun apply(target: ChargeMode) {
        if (busy) return
        scope.launch {
            busy = true
            lastAction = withContext(Dispatchers.IO) { repo.setMode(target) }
            busy = false
            reloadTick++
        }
    }

    val current = probe
    val state = (current as? DiagResult.Ok)?.value
    val enabled = connected && !busy
    val action = lastAction

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!connected) {
            item { Text(stringResource(R.string.diag_waiting)) }
        }
        when (current) {
            null -> item { Text(stringResource(R.string.diag_loading)) }
            is DiagResult.Error -> item {
                Text(
                    text = stringResource(R.string.diag_error, current.message),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            is DiagResult.Ok -> Unit
        }
        if (state != null) {
            item { CurrentCard(state) }
            if (state.support == ChargeSupport.UNSUPPORTED) {
                item { UnsupportedCard() }
            } else {
                if (busy) item { Text(stringResource(R.string.charge_working)) }
                if (action != null) {
                    item {
                        ResultCard(
                            action = action,
                            enabled = enabled,
                            onRevert = { previous -> apply(previous) },
                        )
                    }
                }
                for (mode in ChargeMode.entries) {
                    item(key = mode.name) {
                        ModeCard(
                            mode = mode,
                            isCurrent = state.mode == mode,
                            enabled = enabled,
                            onApply = { apply(mode) },
                        )
                    }
                }
                item { NotesCard(state) }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.charge_raw),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    LtrMonoText(current.raw.trim())
                    CopyShareButtons(current.raw)
                }
            }
        }
    }
}

@Composable
private fun CurrentCard(state: ChargeState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(
                    R.string.charge_current,
                    stringResource(state.mode.labelRes()),
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(
                    R.string.charge_setting_value,
                    state.rawKeyValue.ifEmpty { stringResource(R.string.charge_mode_unknown) },
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            if (state.romMarker.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.charge_rom_marker, state.romMarker),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (state.support == ChargeSupport.FACTORY_DEFAULT) {
                Text(
                    text = stringResource(R.string.charge_unconfirmed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun UnsupportedCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.charge_unsupported_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.charge_unsupported_body),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ModeCard(
    mode: ChargeMode,
    isCurrent: Boolean,
    enabled: Boolean,
    onApply: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(mode.labelRes()),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = stringResource(mode.hintRes()),
                style = MaterialTheme.typography.bodySmall,
            )
            if (isCurrent) {
                OutlinedButton(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.charge_applied))
                }
            } else {
                Button(
                    onClick = onApply,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(mode.labelRes()))
                }
            }
        }
    }
}

@Composable
private fun ResultCard(
    action: ChargeActionResult,
    enabled: Boolean,
    onRevert: (ChargeMode) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)) {
            Text(
                text = stringResource(
                    if (action.ok) R.string.charge_applied else R.string.charge_not_applied,
                ),
                style = MaterialTheme.typography.titleSmall,
                color = if (action.ok) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
            LtrMonoText(action.message)
            CopyShareButtons(action.message)
            val previous = action.previous
            if (action.ok && previous != null) {
                TextButton(onClick = { onRevert(previous) }, enabled = enabled) {
                    Text(
                        stringResource(
                            R.string.charge_revert,
                            stringResource(previous.labelRes()),
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun NotesCard(state: ChargeState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.charge_shell_note),
                style = MaterialTheme.typography.bodySmall,
            )
            if (state.support != ChargeSupport.UNSUPPORTED) {
                Text(
                    text = stringResource(R.string.charge_scheduled_note),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

private fun ChargeMode?.labelRes(): Int = when (this) {
    ChargeMode.CHARGE_FULLY -> R.string.charge_mode_full
    ChargeMode.INTELLIGENT -> R.string.charge_mode_intelligent
    ChargeMode.PROTECTED -> R.string.charge_mode_protected
    null -> R.string.charge_mode_unknown
}

private fun ChargeMode.hintRes(): Int = when (this) {
    ChargeMode.CHARGE_FULLY -> R.string.charge_mode_full_hint
    ChargeMode.INTELLIGENT -> R.string.charge_mode_intelligent_hint
    ChargeMode.PROTECTED -> R.string.charge_mode_protected_hint
}

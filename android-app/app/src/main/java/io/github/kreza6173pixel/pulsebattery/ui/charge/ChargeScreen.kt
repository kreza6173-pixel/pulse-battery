package io.github.kreza6173pixel.pulsebattery.ui.charge

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.kreza6173pixel.pulsebattery.R
import io.github.kreza6173pixel.pulsebattery.charge.ChargeActionResult
import io.github.kreza6173pixel.pulsebattery.charge.ChargeLimitPolicy
import io.github.kreza6173pixel.pulsebattery.charge.ChargeLimitService
import io.github.kreza6173pixel.pulsebattery.charge.ChargeLimitSettings
import io.github.kreza6173pixel.pulsebattery.charge.ChargeLimitStore
import io.github.kreza6173pixel.pulsebattery.charge.ChargeMode
import io.github.kreza6173pixel.pulsebattery.charge.ChargeRepository
import io.github.kreza6173pixel.pulsebattery.charge.ChargeState
import io.github.kreza6173pixel.pulsebattery.charge.ChargeSupport
import io.github.kreza6173pixel.pulsebattery.charge.GateActionResult
import io.github.kreza6173pixel.pulsebattery.charge.GateRepository
import io.github.kreza6173pixel.pulsebattery.charge.GateSnapshot
import io.github.kreza6173pixel.pulsebattery.charge.GateState
import io.github.kreza6173pixel.pulsebattery.charge.LIMIT_MAX
import io.github.kreza6173pixel.pulsebattery.charge.LIMIT_MIN
import io.github.kreza6173pixel.pulsebattery.charge.MIN_HYSTERESIS
import io.github.kreza6173pixel.pulsebattery.charge.NODE_INPUT_SUSPEND
import io.github.kreza6173pixel.pulsebattery.diag.DiagResult
import io.github.kreza6173pixel.pulsebattery.exec.ConnectionState
import io.github.kreza6173pixel.pulsebattery.exec.ExecBridge
import io.github.kreza6173pixel.pulsebattery.health.HealthParsers
import io.github.kreza6173pixel.pulsebattery.ui.common.CopyShareButtons
import io.github.kreza6173pixel.pulsebattery.ui.common.LtrMonoText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Step the thresholds move by, so the two buttons stay useful without a slider. */
private const val THRESHOLD_STEP = 5

/**
 * Charging controls, in order of immediacy: the automatic limit, then the manual gate, then
 * the ROM modes.
 *
 * The limit and the gate both write one kernel node and need root. The ROM modes need only
 * shell access, so they stay usable in both access modes.
 */
@Composable
fun ChargeScreen(
    bridge: ExecBridge,
    rootAvailable: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val modeRepo = remember(bridge) { ChargeRepository(bridge) }
    val gateRepo = remember(bridge, rootAvailable) { GateRepository(bridge, rootAvailable) }
    val limitStore = remember(context) { ChargeLimitStore(context) }
    val scope = rememberCoroutineScope()

    var limit by remember { mutableStateOf(limitStore.read()) }
    var modeProbe by remember { mutableStateOf<DiagResult<ChargeState>?>(null) }
    var gateProbe by remember { mutableStateOf<DiagResult<GateSnapshot>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var modeAction by remember { mutableStateOf<ChargeActionResult?>(null) }
    var gateAction by remember { mutableStateOf<GateActionResult?>(null) }
    var reloadTick by remember { mutableStateOf(0) }
    val connected = bridge.connectionState == ConnectionState.CONNECTED

    // The service runs either way, but a foreground service with no visible notification is
    // exactly the hidden background work this app promises not to do.
    val notificationRequest = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    LaunchedEffect(connected, reloadTick) {
        if (!connected) return@LaunchedEffect
        gateProbe = withContext(Dispatchers.IO) { gateRepo.read() }
        modeProbe = withContext(Dispatchers.IO) { modeRepo.read() }
    }

    fun saveLimit(next: ChargeLimitSettings) {
        val clean = limitStore.write(next)
        limit = clean
        if (clean.enabled) {
            val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            if (needsPermission) {
                notificationRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            ChargeLimitService.start(context)
        } else {
            ChargeLimitService.stop(context)
        }
    }

    fun applyMode(target: ChargeMode) {
        if (busy) return
        scope.launch {
            busy = true
            modeAction = withContext(Dispatchers.IO) { modeRepo.setMode(target) }
            gateAction = null
            busy = false
            reloadTick++
        }
    }

    fun applyGate(block: () -> GateActionResult) {
        if (busy) return
        scope.launch {
            busy = true
            gateAction = withContext(Dispatchers.IO) { block() }
            modeAction = null
            busy = false
            reloadTick++
        }
    }

    val modeResult = modeProbe
    val gateResult = gateProbe
    val state = (modeResult as? DiagResult.Ok)?.value
    val gate = (gateResult as? DiagResult.Ok)?.value
    // Taken once, outside the branches: inside them the compiler cannot narrow the nullable
    // result, and a non-null assertion would only hide that.
    val modeRaw = modeResult?.raw.orEmpty()
    val gateRaw = gateResult?.raw.orEmpty()
    val enabled = connected && !busy

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!connected) {
            item { Text(stringResource(R.string.diag_waiting)) }
        }
        if (busy) {
            item { Text(stringResource(R.string.gate_working)) }
        }

        item {
            LimitCard(
                settings = limit,
                rootAvailable = rootAvailable,
                romProtectionActive = state?.mode == ChargeMode.PROTECTED,
                onChange = { saveLimit(it) },
            )
        }

        if (gate != null) {
            item {
                GateCard(
                    gate = gate,
                    romProtectionActive = state?.mode == ChargeMode.PROTECTED,
                    enabled = enabled,
                    onPause = { applyGate { gateRepo.pause() } },
                    onResume = { applyGate { gateRepo.resume() } },
                )
            }
            gateAction?.let { action ->
                item { GateResultCard(action) }
            }
            item {
                ResetStatsCard(
                    enabled = enabled,
                    onReset = { applyGate { gateRepo.resetBatteryStats() } },
                )
            }
        }
        if (gateResult is DiagResult.Error) {
            item {
                Text(
                    text = stringResource(R.string.diag_error, gateResult.message),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        item { HorizontalDivider() }

        when (modeResult) {
            null -> item { Text(stringResource(R.string.diag_loading)) }
            is DiagResult.Error -> item {
                Text(
                    text = stringResource(R.string.diag_error, modeResult.message),
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
                modeAction?.let { action ->
                    item {
                        ModeResultCard(
                            action = action,
                            enabled = enabled,
                            onRevert = { previous -> applyMode(previous) },
                        )
                    }
                }
                for (mode in ChargeMode.entries) {
                    item(key = mode.name) {
                        ModeCard(
                            mode = mode,
                            isCurrent = state.mode == mode,
                            enabled = enabled,
                            onApply = { applyMode(mode) },
                        )
                    }
                }
                item { NotesCard(state) }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.charge_raw),
                    style = MaterialTheme.typography.titleSmall,
                )
                LtrMonoText((gateRaw + "\n" + modeRaw).trim())
                CopyShareButtons(gateRaw + "\n" + modeRaw)
            }
        }
    }
}

@Composable
private fun LimitCard(
    settings: ChargeLimitSettings,
    rootAvailable: Boolean,
    romProtectionActive: Boolean,
    onChange: (ChargeLimitSettings) -> Unit,
) {
    val resumeRange = ChargeLimitPolicy.resumeRange(settings.limitPercent)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.limit_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.limit_enable),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = settings.enabled,
                    onCheckedChange = { onChange(settings.copy(enabled = it)) },
                    enabled = rootAvailable,
                )
            }

            StepperRow(
                label = stringResource(R.string.limit_stop_at, settings.limitPercent),
                enabled = rootAvailable,
                canDecrease = settings.limitPercent - THRESHOLD_STEP >= LIMIT_MIN,
                canIncrease = settings.limitPercent + THRESHOLD_STEP <= LIMIT_MAX,
                onDecrease = {
                    onChange(settings.copy(limitPercent = settings.limitPercent - THRESHOLD_STEP))
                },
                onIncrease = {
                    onChange(settings.copy(limitPercent = settings.limitPercent + THRESHOLD_STEP))
                },
            )
            StepperRow(
                label = stringResource(R.string.limit_resume_at, settings.resumePercent),
                enabled = rootAvailable,
                canDecrease = settings.resumePercent - THRESHOLD_STEP >= resumeRange.first,
                canIncrease = settings.resumePercent + THRESHOLD_STEP <= resumeRange.last,
                onDecrease = {
                    onChange(settings.copy(resumePercent = settings.resumePercent - THRESHOLD_STEP))
                },
                onIncrease = {
                    onChange(settings.copy(resumePercent = settings.resumePercent + THRESHOLD_STEP))
                },
            )
            Text(
                text = stringResource(R.string.limit_range_note, MIN_HYSTERESIS),
                style = MaterialTheme.typography.bodySmall,
            )

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.limit_restore_on_boot),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = settings.restoreOnBoot,
                    onCheckedChange = { onChange(settings.copy(restoreOnBoot = it)) },
                    enabled = rootAvailable,
                )
            }

            if (!rootAvailable) {
                Text(
                    text = stringResource(R.string.gate_needs_root),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (romProtectionActive) {
                Text(
                    text = stringResource(R.string.limit_rom_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Text(
                text = stringResource(R.string.limit_service_note),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun StepperRow(
    label: String,
    enabled: Boolean,
    canDecrease: Boolean,
    canIncrease: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        OutlinedButton(onClick = onDecrease, enabled = enabled && canDecrease) {
            Text(stringResource(R.string.limit_decrease))
        }
        OutlinedButton(onClick = onIncrease, enabled = enabled && canIncrease) {
            Text(stringResource(R.string.limit_increase))
        }
    }
}

@Composable
private fun GateCard(
    gate: GateSnapshot,
    romProtectionActive: Boolean,
    enabled: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
) {
    val milliAmps = HealthParsers.toMilliAmps(gate.currentMicroAmps)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.gate_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(gate.state.labelRes()),
                style = MaterialTheme.typography.bodyMedium,
                color = if (gate.state == GateState.PAUSED) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            gate.capacityPercent?.let {
                Text(
                    text = stringResource(R.string.gate_level, it),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (milliAmps != null) {
                Text(
                    text = stringResource(R.string.gate_current, milliAmps),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (gate.rawSuspend.isNotEmpty()) {
                LtrMonoText(
                    stringResource(R.string.gate_node_value, NODE_INPUT_SUSPEND, gate.rawSuspend),
                )
            }
            if (gate.rawStatus.isNotEmpty()) {
                LtrMonoText(stringResource(R.string.gate_status_value, gate.rawStatus))
            }

            if (gate.state == GateState.PAUSED) {
                Text(
                    text = stringResource(R.string.gate_paused_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (romProtectionActive) {
                Text(
                    text = stringResource(R.string.gate_conflict),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onPause,
                    enabled = enabled && gate.writable && gate.state != GateState.PAUSED,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.gate_pause))
                }
                OutlinedButton(
                    onClick = onResume,
                    enabled = enabled && gate.writable && gate.state != GateState.OPEN,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.gate_resume))
                }
            }
            if (!gate.writable) {
                Text(
                    text = stringResource(R.string.gate_needs_root),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = stringResource(R.string.gate_manual_note),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun GateResultCard(action: GateActionResult) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)) {
            Text(
                text = stringResource(
                    if (action.ok) R.string.gate_applied else R.string.gate_not_applied,
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
        }
    }
}

@Composable
private fun ResetStatsCard(enabled: Boolean, onReset: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.gate_reset_hint),
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(
                onClick = onReset,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.gate_reset_stats))
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
private fun ModeResultCard(
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

private fun GateState.labelRes(): Int = when (this) {
    GateState.OPEN -> R.string.gate_state_open
    GateState.PAUSED -> R.string.gate_state_paused
    GateState.UNKNOWN -> R.string.gate_state_unknown
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

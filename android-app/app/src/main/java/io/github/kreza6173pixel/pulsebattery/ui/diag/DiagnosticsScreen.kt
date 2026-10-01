package io.github.kreza6173pixel.pulsebattery.ui.diag

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.kreza6173pixel.pulsebattery.R
import io.github.kreza6173pixel.pulsebattery.diag.BatteryHealth
import io.github.kreza6173pixel.pulsebattery.diag.BatterySnapshot
import io.github.kreza6173pixel.pulsebattery.diag.BatteryStatus
import io.github.kreza6173pixel.pulsebattery.diag.DiagResult
import io.github.kreza6173pixel.pulsebattery.diag.DiagnosticsRepository
import io.github.kreza6173pixel.pulsebattery.diag.WakeLockEntry
import io.github.kreza6173pixel.pulsebattery.exec.ConnectionState
import io.github.kreza6173pixel.pulsebattery.exec.ExecBridge
import io.github.kreza6173pixel.pulsebattery.ui.common.CopyShareButtons
import io.github.kreza6173pixel.pulsebattery.ui.common.LtrMonoText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val REFRESH_MS = 5_000L

/** M3a: live battery state and the wake locks held right now. Read-only. */
@Composable
fun DiagnosticsScreen(bridge: ExecBridge, modifier: Modifier = Modifier) {
    val repo = remember(bridge) { DiagnosticsRepository(bridge) }
    var battery by remember { mutableStateOf<DiagResult<BatterySnapshot>?>(null) }
    var locks by remember { mutableStateOf<DiagResult<List<WakeLockEntry>>?>(null) }
    var autoRefresh by remember { mutableStateOf(true) }
    var manualTick by remember { mutableStateOf(0) }
    val connected = bridge.connectionState == ConnectionState.CONNECTED

    LaunchedEffect(connected, autoRefresh, manualTick) {
        if (!connected) return@LaunchedEffect
        while (true) {
            battery = withContext(Dispatchers.IO) { repo.battery() }
            locks = withContext(Dispatchers.IO) { repo.wakeLocks() }
            if (!autoRefresh) break
            delay(REFRESH_MS)
        }
    }

    val lockResult = locks

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.diag_auto_refresh),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = autoRefresh, onCheckedChange = { autoRefresh = it })
            }
        }
        item {
            OutlinedButton(
                onClick = { manualTick++ },
                enabled = connected,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.diag_refresh))
            }
        }
        if (!connected) {
            item { Text(stringResource(R.string.diag_waiting)) }
        }

        item { BatteryCard(battery) }

        item { WakeLockHeader(lockResult) }
        if (lockResult is DiagResult.Ok) {
            if (lockResult.value.isEmpty()) {
                item { Text(stringResource(R.string.diag_wakelocks_none)) }
            } else {
                items(lockResult.value) { entry -> WakeLockRow(entry) }
            }
        }
    }
}

@Composable
private fun BatteryCard(result: DiagResult<BatterySnapshot>?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.diag_battery),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (result != null) CopyShareButtons(result.raw)
            }
            when (result) {
                null -> Text(stringResource(R.string.diag_loading))
                is DiagResult.Error -> ErrorText(result.message)
                is DiagResult.Ok -> BatteryBody(result.value)
            }
        }
    }
}

@Composable
private fun BatteryBody(b: BatterySnapshot) {
    val percent = b.percent
    Text(
        text = if (percent != null) "$percent%" else "?",
        style = MaterialTheme.typography.displayMedium,
    )
    Text(
        text = stringResource(statusRes(b.status)),
        style = MaterialTheme.typography.titleSmall,
    )
    Spacer(Modifier.height(8.dp))
    LinearProgressIndicator(
        progress = { (percent ?: 0) / 100f },
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))

    val temp = b.temperatureC
    InfoRow(
        stringResource(R.string.diag_row_temp),
        if (temp != null) stringResource(R.string.diag_temp_value, temp) else "?",
    )
    val mv = b.voltageMv
    InfoRow(
        stringResource(R.string.diag_row_voltage),
        if (mv != null) stringResource(R.string.diag_voltage_value, mv / 1000.0) else "?",
    )
    InfoRow(stringResource(R.string.diag_row_health), stringResource(healthRes(b.health)))
    InfoRow(stringResource(R.string.diag_row_source), stringResource(sourceRes(b)))
    InfoRow(stringResource(R.string.diag_row_tech), b.technology ?: "?")
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun WakeLockHeader(result: DiagResult<List<WakeLockEntry>>?) {
    val count = (result as? DiagResult.Ok)?.value?.size ?: 0
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.diag_wakelocks, count),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            if (result != null) CopyShareButtons(result.raw)
        }
        when (result) {
            null -> Text(stringResource(R.string.diag_loading))
            is DiagResult.Error -> ErrorText(result.message)
            is DiagResult.Ok -> Unit
        }
    }
}

@Composable
private fun WakeLockRow(w: WakeLockEntry) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 10.dp)) {
            LtrMonoText(
                text = w.attributedPackages.joinToString(", ").ifEmpty { "?" },
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.diag_wakelock_meta, w.shortType, w.heldFor),
                style = MaterialTheme.typography.labelMedium,
            )
            LtrMonoText(text = w.tag, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ErrorText(message: String) {
    Text(
        text = stringResource(R.string.diag_error, message),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
    )
}

private fun statusRes(s: BatteryStatus): Int = when (s) {
    BatteryStatus.CHARGING -> R.string.diag_status_charging
    BatteryStatus.DISCHARGING -> R.string.diag_status_discharging
    BatteryStatus.NOT_CHARGING -> R.string.diag_status_not_charging
    BatteryStatus.FULL -> R.string.diag_status_full
    BatteryStatus.UNKNOWN -> R.string.diag_unknown
}

private fun healthRes(h: BatteryHealth): Int = when (h) {
    BatteryHealth.GOOD -> R.string.diag_health_good
    BatteryHealth.OVERHEAT -> R.string.diag_health_overheat
    BatteryHealth.DEAD -> R.string.diag_health_dead
    BatteryHealth.OVER_VOLTAGE -> R.string.diag_health_over_voltage
    BatteryHealth.FAILURE -> R.string.diag_health_failure
    BatteryHealth.COLD -> R.string.diag_health_cold
    BatteryHealth.UNKNOWN -> R.string.diag_unknown
}

private fun sourceRes(b: BatterySnapshot): Int = when {
    b.acPowered -> R.string.diag_source_ac
    b.usbPowered -> R.string.diag_source_usb
    b.wirelessPowered -> R.string.diag_source_wireless
    b.dockPowered -> R.string.diag_source_dock
    else -> R.string.diag_source_battery
}

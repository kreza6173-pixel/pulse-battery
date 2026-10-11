package io.github.kreza6173pixel.pulsebattery.ui.health

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import io.github.kreza6173pixel.pulsebattery.diag.DiagResult
import io.github.kreza6173pixel.pulsebattery.exec.ConnectionState
import io.github.kreza6173pixel.pulsebattery.exec.ExecBridge
import io.github.kreza6173pixel.pulsebattery.health.BatteryHealth
import io.github.kreza6173pixel.pulsebattery.health.HealthNode
import io.github.kreza6173pixel.pulsebattery.health.HealthParsers
import io.github.kreza6173pixel.pulsebattery.health.HealthRepository
import io.github.kreza6173pixel.pulsebattery.ui.common.CopyShareButtons
import io.github.kreza6173pixel.pulsebattery.ui.common.LtrMonoText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Battery health from the kernel nodes. Read-only: there is no write path on this screen.
 *
 * [rootAvailable] only decides whether the hint about root is worth showing. It never hides
 * anything, so the shell and root modes can be compared on the same screen.
 */
@Composable
fun HealthScreen(
    bridge: ExecBridge,
    rootAvailable: Boolean,
    modifier: Modifier = Modifier,
) {
    val repo = remember(bridge) { HealthRepository(bridge) }
    var probe by remember { mutableStateOf<DiagResult<BatteryHealth>?>(null) }
    var loading by remember { mutableStateOf(false) }
    var reloadTick by remember { mutableStateOf(0) }
    val connected = bridge.connectionState == ConnectionState.CONNECTED

    LaunchedEffect(connected, reloadTick) {
        if (!connected) return@LaunchedEffect
        loading = true
        probe = withContext(Dispatchers.IO) { repo.read() }
        loading = false
    }

    val current = probe
    val health = (current as? DiagResult.Ok)?.value
    // Taken once, outside the branches, for the same reason as on the charge screen.
    val rawOutput = current?.raw.orEmpty()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!connected) {
            item { Text(stringResource(R.string.diag_waiting)) }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.health_read_only),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = { reloadTick++ }, enabled = connected && !loading) {
                    Text(stringResource(R.string.diag_refresh))
                }
            }
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
        if (health != null) {
            item { SummaryCard(health) }
            if (health.readableCount < health.nodes.size && !rootAvailable) {
                item {
                    Text(
                        text = stringResource(R.string.health_root_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            item {
                Text(
                    text = stringResource(
                        R.string.health_nodes,
                        health.readableCount,
                        health.nodes.size,
                    ),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (health.readableCount == 0) {
                item { Text(stringResource(R.string.health_nothing)) }
            }
            items(health.nodes, key = { it.name }) { node -> NodeRow(node) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.health_raw),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    CopyShareButtons(rawOutput)
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(health: BatteryHealth) {
    val percent = health.healthPercent
    val full = HealthParsers.toMilliAmpHours(health.fullMicroAmpHours)
    val design = HealthParsers.toMilliAmpHours(health.designMicroAmpHours)
    val celsius = HealthParsers.toCelsius(health.temperatureTenthsC)
    val milliAmps = HealthParsers.toMilliAmps(health.currentMicroAmps)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.health_summary),
                style = MaterialTheme.typography.titleMedium,
            )
            if (percent != null) {
                Text(
                    text = stringResource(R.string.health_percent, percent),
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
            if (full != null && design != null) {
                Text(
                    text = stringResource(R.string.health_capacity_pair, full, design),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            health.cycleCount?.let {
                Text(
                    text = stringResource(R.string.health_cycles, it),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            health.capacityPercent?.let {
                Text(
                    text = stringResource(R.string.health_level, it),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            celsius?.let {
                Text(
                    text = stringResource(R.string.health_temp, it),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (milliAmps != null) {
                Text(
                    text = stringResource(R.string.health_current, milliAmps),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.health_current_hint),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun NodeRow(node: HealthNode) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp)) {
            LtrMonoText(text = node.name, style = MaterialTheme.typography.bodyMedium)
            if (node.readable) {
                LtrMonoText(text = node.raw)
            } else {
                Text(
                    text = stringResource(R.string.health_node_unreadable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LtrMonoText(
                    text = node.raw,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

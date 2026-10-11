package io.github.kreza6173pixel.pulsebattery.ui.cpu

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
import io.github.kreza6173pixel.pulsebattery.cpu.UidCpuParsers
import io.github.kreza6173pixel.pulsebattery.cpu.UidCpuReport
import io.github.kreza6173pixel.pulsebattery.cpu.UidCpuRepository
import io.github.kreza6173pixel.pulsebattery.cpu.UidCpuRow
import io.github.kreza6173pixel.pulsebattery.diag.DiagResult
import io.github.kreza6173pixel.pulsebattery.exec.ConnectionState
import io.github.kreza6173pixel.pulsebattery.exec.ExecBridge
import io.github.kreza6173pixel.pulsebattery.ui.common.CopyShareButtons
import io.github.kreza6173pixel.pulsebattery.ui.common.LtrMonoText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * CPU time per uid, straight from the kernel. Read-only: there is no write path here.
 *
 * [rootAvailable] only decides whether the hint about root is worth showing when the node was
 * refused.
 */
@Composable
fun CpuScreen(
    bridge: ExecBridge,
    rootAvailable: Boolean,
    modifier: Modifier = Modifier,
) {
    val repo = remember(bridge) { UidCpuRepository(bridge) }
    var probe by remember { mutableStateOf<DiagResult<UidCpuReport>?>(null) }
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
    val report = (current as? DiagResult.Ok)?.value
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
                    text = stringResource(R.string.cpu_read_only),
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
            is DiagResult.Error -> {
                item {
                    Text(
                        text = stringResource(R.string.diag_error, current.message),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (!rootAvailable) {
                    item {
                        Text(
                            text = stringResource(R.string.cpu_root_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            is DiagResult.Ok -> Unit
        }
        if (report != null) {
            item {
                Text(
                    text = stringResource(
                        R.string.cpu_total,
                        UidCpuParsers.formatDuration(report.totalMicros),
                    ),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            item {
                Text(
                    text = stringResource(R.string.cpu_rows, report.rows.size),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            items(report.rows, key = { it.uid }) { row -> CpuRowCard(row) }
            if (report.hiddenRows > 0) {
                item {
                    Text(
                        text = stringResource(
                            R.string.cpu_hidden,
                            report.hiddenRows,
                            UidCpuParsers.formatDuration(report.hiddenMicros),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        if (rawOutput.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.cpu_raw),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    CopyShareButtons(rawOutput)
                }
            }
        }
    }
}

@Composable
private fun CpuRowCard(row: UidCpuRow) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (row.packages.isEmpty()) {
                Text(
                    text = stringResource(R.string.cpu_no_package),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                row.packages.forEach { name ->
                    LtrMonoText(text = name, style = MaterialTheme.typography.bodyMedium)
                }
                if (row.packages.size > 1) {
                    Text(
                        text = stringResource(R.string.cpu_shared_uid, row.packages.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = stringResource(R.string.cpu_uid, row.uid),
                style = MaterialTheme.typography.labelSmall,
            )
            Text(
                text = stringResource(
                    R.string.cpu_row_total,
                    UidCpuParsers.formatDuration(row.totalMicros),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(
                    R.string.cpu_row_split,
                    UidCpuParsers.formatDuration(row.userMicros),
                    UidCpuParsers.formatDuration(row.systemMicros),
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = stringResource(
                    R.string.cpu_row_raw,
                    row.userMicros,
                    row.systemMicros,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

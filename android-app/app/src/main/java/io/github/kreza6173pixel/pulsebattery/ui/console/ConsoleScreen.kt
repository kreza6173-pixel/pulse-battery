package io.github.kreza6173pixel.pulsebattery.ui.console

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.kreza6173pixel.pulsebattery.R
import io.github.kreza6173pixel.pulsebattery.exec.ConnectionState
import io.github.kreza6173pixel.pulsebattery.exec.ConsoleHistory
import io.github.kreza6173pixel.pulsebattery.exec.ExecBridge
import io.github.kreza6173pixel.pulsebattery.exec.ExecOutcome
import io.github.kreza6173pixel.pulsebattery.exec.HistoryEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The complete list of commands the built-in self-test may run. Both are read-only: `id`
 * prints the uid, `getprop` reads one system property. Nothing here may change system state.
 */
private val SELF_TEST_COMMANDS = listOf("id", "getprop ro.build.version.sdk")

private const val TIMEOUT_MS = 15_000

private const val MAX_BIND_LOG_LINES = 12

/**
 * Console screen. Only reachable when Shizuku is READY, because every command here needs the
 * UserService. All padding is start/end so the layout mirrors under the `fa` locale.
 */
@Composable
fun ConsoleScreen(bridge: ExecBridge, modifier: Modifier = Modifier) {
    val history = remember { ConsoleHistory() }
    var entries by remember { mutableStateOf(emptyList<HistoryEntry>()) }
    var command by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // The AIDL call blocks until the command finishes, so it must never run on the main thread.
    fun run(raw: String) {
        if (busy || raw.isBlank()) return
        scope.launch {
            busy = true
            val outcome = withContext(Dispatchers.IO) { bridge.execBlocking(raw, TIMEOUT_MS) }
            when (outcome) {
                is ExecOutcome.Failed -> history.recordFailure(raw, outcome.message)
                is ExecOutcome.Completed -> history.record(
                    rawCommand = raw,
                    exitCode = outcome.result.exitCode,
                    durationMs = outcome.durationMs,
                    stdout = outcome.result.stdout,
                    stderr = outcome.result.stderr,
                    truncated = outcome.result.truncated,
                )
            }
            entries = history.items
            busy = false
        }
    }

    val connected = bridge.connectionState == ConnectionState.CONNECTED

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ConnectionLabel(bridge.connectionState)

        BindLog(bridge.bindLog)

        OutlinedTextField(
            value = command,
            onValueChange = { command = it },
            enabled = !busy,
            singleLine = true,
            label = { Text(stringResource(R.string.console_command_label)) },
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = { run(command.trim()) },
                enabled = !busy && connected && command.isNotBlank(),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = stringResource(R.string.console_run),
                    modifier = Modifier.padding(start = 8.dp, end = 8.dp),
                )
            }
            OutlinedButton(
                onClick = { bridge.cancel() },
                enabled = busy,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = stringResource(R.string.console_cancel),
                    modifier = Modifier.padding(start = 8.dp, end = 8.dp),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SELF_TEST_COMMANDS.forEach { preset ->
                OutlinedButton(
                    onClick = { run(preset) },
                    enabled = !busy && connected,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = preset,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        // `getprop ro.build.version.sdk` needs three lines in a half-width
                        // button; at maxLines = 2 it was cut off with no ellipsis.
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 4.dp, end = 4.dp),
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(entries) { entry -> HistoryCard(entry) }
        }
    }
}

@Composable
private fun ConnectionLabel(state: ConnectionState) {
    val labelRes = when (state) {
        ConnectionState.DISCONNECTED -> R.string.console_disconnected
        ConnectionState.CONNECTING -> R.string.console_connecting
        ConnectionState.CONNECTED -> R.string.console_connected
    }
    Text(
        text = stringResource(R.string.console_state_format, stringResource(labelRes)),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * Last few bind attempts, so a stuck DISCONNECTED can be diagnosed without logcat.
 * Deliberately shows the newest lines last so the cause is the final thing read.
 */
@Composable
private fun BindLog(lines: List<String>) {
    val shown = lines.takeLast(MAX_BIND_LOG_LINES)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 12.dp)
        ) {
            Text(
                text = stringResource(R.string.console_bind_log),
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(Modifier.height(4.dp))
            if (shown.isEmpty()) {
                Text(
                    text = stringResource(R.string.console_bind_log_empty),
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                Text(
                    text = shown.joinToString("\n"),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun HistoryCard(entry: HistoryEntry) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 12.dp)) {
            Text(text = entry.displayCommand, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (entry.failed) {
                    stringResource(R.string.console_meta_no_exit)
                } else {
                    stringResource(R.string.console_meta_format, entry.exitCode, entry.durationMs)
                },
                style = MaterialTheme.typography.labelMedium,
            )
            if (entry.truncated) {
                Text(
                    text = stringResource(R.string.console_truncated),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (entry.displayStdout.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(text = entry.displayStdout, style = MaterialTheme.typography.bodySmall)
            }
            if (entry.displayStderr.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = entry.displayStderr,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
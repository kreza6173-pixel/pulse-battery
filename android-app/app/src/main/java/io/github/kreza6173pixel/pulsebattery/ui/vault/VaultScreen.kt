package io.github.kreza6173pixel.pulsebattery.ui.vault

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
import io.github.kreza6173pixel.pulsebattery.diag.DiagResult
import io.github.kreza6173pixel.pulsebattery.exec.ConnectionState
import io.github.kreza6173pixel.pulsebattery.exec.ExecBridge
import io.github.kreza6173pixel.pulsebattery.standby.ActionResult
import io.github.kreza6173pixel.pulsebattery.ui.common.CopyShareButtons
import io.github.kreza6173pixel.pulsebattery.ui.common.LtrMonoText
import io.github.kreza6173pixel.pulsebattery.vault.DataVaultParsers
import io.github.kreza6173pixel.pulsebattery.vault.VaultEntry
import io.github.kreza6173pixel.pulsebattery.vault.VaultRepository
import io.github.kreza6173pixel.pulsebattery.vault.VaultSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val CONFIRM_RESTORE = "restore"
private const val CONFIRM_DELETE = "delete"
private const val CONFIRM_DATA_RESTORE = "data_restore"

/**
 * M5: export APKs to shared storage, restore them, delete backups.
 *
 * With root the same vault also holds a full archive of the app's private data and of its
 * shared-storage data and obb directories.
 */
@Composable
fun VaultScreen(
    bridge: ExecBridge,
    rootAvailable: Boolean,
    modifier: Modifier = Modifier,
) {
    val repo = remember(bridge) { VaultRepository(bridge) }
    val scope = rememberCoroutineScope()
    var snapshot by remember { mutableStateOf<DiagResult<VaultSnapshot>?>(null) }
    var query by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var lastAction by remember { mutableStateOf<ActionResult?>(null) }
    var reloadTick by remember { mutableStateOf(0) }
    val connected = bridge.connectionState == ConnectionState.CONNECTED

    LaunchedEffect(connected, reloadTick) {
        if (!connected) return@LaunchedEffect
        snapshot = withContext(Dispatchers.IO) { repo.load() }
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
    val vault: List<VaultEntry> = if (snap is DiagResult.Ok) snap.value.vault else emptyList()
    val apps: List<String> = if (snap is DiagResult.Ok) {
        snap.value.apps.filter { needle.isEmpty() || it.contains(needle, ignoreCase = true) }
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
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = stringResource(R.string.vault_where),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    LtrMonoText(VaultRepository.VAULT_DIR)
                    if (rootAvailable) {
                        Text(
                            text = stringResource(R.string.vault_data_root),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = stringResource(R.string.vault_data_stops_app),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = stringResource(R.string.vault_data_caveat),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.vault_data_locked),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        if (busy) {
            item { Text(stringResource(R.string.standby_working)) }
        }
        if (action != null) {
            item { ResultCard(action) }
        }

        item {
            Text(
                text = stringResource(R.string.vault_saved, vault.size),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        when (snap) {
            null -> item { Text(stringResource(R.string.diag_loading)) }
            is DiagResult.Error -> item {
                Text(
                    text = stringResource(R.string.diag_error, snap.message),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            is DiagResult.Ok -> if (vault.isEmpty()) {
                item { Text(stringResource(R.string.vault_empty)) }
            }
        }
        items(vault, key = { "v:" + it.pkg }) { entry ->
            VaultRow(
                entry = entry,
                enabled = enabled,
                rootAvailable = rootAvailable,
                onRestore = { act { repo.restore(entry.pkg) } },
                onDelete = { act { repo.delete(entry.pkg) } },
                onRestoreData = { act { repo.restoreData(entry.pkg, rootAvailable) } },
                onDeleteData = { act { repo.deleteData(entry.pkg) } },
            )
        }

        item {
            Text(
                text = stringResource(R.string.vault_apps, apps.size),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
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
        items(apps, key = { "a:" + it }) { pkg ->
            AppExportRow(
                pkg = pkg,
                enabled = enabled,
                rootAvailable = rootAvailable,
                onExport = { act { repo.export(pkg) } },
                onExportData = { act { repo.exportData(pkg, rootAvailable) } },
            )
        }
    }
}

@Composable
private fun ResultCard(a: ActionResult) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)) {
            Text(
                text = stringResource(if (a.ok) R.string.standby_applied else R.string.standby_not_applied),
                style = MaterialTheme.typography.titleSmall,
                color = if (a.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
            LtrMonoText(a.message)
            CopyShareButtons(a.message)
        }
    }
}

@Composable
private fun VaultRow(
    entry: VaultEntry,
    enabled: Boolean,
    rootAvailable: Boolean,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    onRestoreData: () -> Unit,
    onDeleteData: () -> Unit,
) {
    var confirm by remember { mutableStateOf<String?>(null) }
    val megabytes = DataVaultParsers.megabytes(entry.totalBytes)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 10.dp)) {
            LtrMonoText(text = entry.pkg, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = stringResource(R.string.vault_entry_meta, entry.apkCount, megabytes),
                style = MaterialTheme.typography.labelMedium,
            )
            if (rootAvailable) {
                Text(
                    text = if (entry.hasDataArchive) {
                        stringResource(
                            R.string.vault_data_meta,
                            DataVaultParsers.megabytes(entry.dataBytes),
                            DataVaultParsers.megabytes(entry.externalBytes),
                        )
                    } else {
                        stringResource(R.string.vault_data_none)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = {
                        if (confirm == CONFIRM_RESTORE) {
                            confirm = null
                            onRestore()
                        } else {
                            confirm = CONFIRM_RESTORE
                        }
                    },
                    enabled = enabled && entry.apkCount > 0,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        stringResource(
                            if (confirm == CONFIRM_RESTORE) R.string.vault_confirm else R.string.vault_restore
                        )
                    )
                }
                OutlinedButton(
                    onClick = {
                        if (confirm == CONFIRM_DELETE) {
                            confirm = null
                            onDelete()
                        } else {
                            confirm = CONFIRM_DELETE
                        }
                    },
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = stringResource(
                            if (confirm == CONFIRM_DELETE) R.string.vault_confirm else R.string.vault_delete
                        ),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            if (rootAvailable && entry.hasDataArchive) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = {
                            if (confirm == CONFIRM_DATA_RESTORE) {
                                confirm = null
                                onRestoreData()
                            } else {
                                confirm = CONFIRM_DATA_RESTORE
                            }
                        },
                        enabled = enabled,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            stringResource(
                                if (confirm == CONFIRM_DATA_RESTORE) {
                                    R.string.vault_confirm
                                } else {
                                    R.string.vault_data_restore
                                }
                            )
                        )
                    }
                    TextButton(onClick = onDeleteData, enabled = enabled) {
                        Text(
                            text = stringResource(R.string.vault_data_delete),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            if (confirm != null) {
                TextButton(onClick = { confirm = null }) {
                    Text(stringResource(R.string.vault_cancel))
                }
            }
        }
    }
}

@Composable
private fun AppExportRow(
    pkg: String,
    enabled: Boolean,
    rootAvailable: Boolean,
    onExport: () -> Unit,
    onExportData: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    LtrMonoText(text = pkg, style = MaterialTheme.typography.bodyMedium)
                }
                TextButton(onClick = onExport, enabled = enabled) {
                    Text(stringResource(R.string.vault_export))
                }
            }
            if (rootAvailable) {
                TextButton(
                    onClick = onExportData,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.vault_data_backup))
                }
            }
        }
    }
}

package io.github.kreza6173pixel.pulsebattery.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.kreza6173pixel.pulsebattery.R
import io.github.kreza6173pixel.pulsebattery.access.ServiceProvider
import io.github.kreza6173pixel.pulsebattery.shizuku.ShizukuRuntime
import io.github.kreza6173pixel.pulsebattery.shizuku.ShizukuState
import io.github.kreza6173pixel.pulsebattery.shizuku.ShizukuUidKind

/**
 * Home screen: Shizuku client state, exactly one next action per state, and the feature
 * entry points once READY. The drain report is the headline feature, so it comes first.
 */
@Composable
fun HomeScreen(
    runtime: ShizukuRuntime,
    modifier: Modifier = Modifier,
    onOpenReport: () -> Unit = {},
    onOpenConsole: () -> Unit = {},
    onOpenDiagnostics: () -> Unit = {},
    onOpenStandby: () -> Unit = {},
    onOpenVault: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
) {
    val state = runtime.state

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StateCard(runtime)
        StateAction(runtime, state)
        UidRow(runtime)
        AccessRows(runtime)
        // Every feature needs the Shizuku UserService, so they are only offered once READY.
        if (state == ShizukuState.READY) {
            FeatureButton(R.string.home_open_report, onOpenReport)
            FeatureButton(R.string.home_open_diagnostics, onOpenDiagnostics)
            FeatureButton(R.string.home_open_standby, onOpenStandby)
            FeatureButton(R.string.home_open_vault, onOpenVault)
            OutlinedButton(
                onClick = onOpenConsole,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.home_open_console),
                    modifier = Modifier.padding(start = 8.dp, end = 8.dp),
                )
            }
        }
        RefreshButton(runtime)
        TextButton(onClick = onOpenAbout, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.home_open_about))
        }
    }
}

@Composable
private fun FeatureButton(labelRes: Int, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(labelRes),
            modifier = Modifier.padding(start = 8.dp, end = 8.dp),
        )
    }
}

@Composable
private fun StateCard(runtime: ShizukuRuntime) {
    val state = runtime.state
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp)) {
            Text(
                text = stringResource(state.titleRes()),
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(state.bodyRes()),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (state == ShizukuState.PERMISSION_NEEDED && runtime.shouldShowRationale) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.state_rationale_hint),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun StateAction(runtime: ShizukuRuntime, state: ShizukuState) {
    when (state) {
        ShizukuState.NOT_INSTALLED -> Button(
            onClick = { runtime.launchManager() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(R.string.action_install_shizuku),
                modifier = Modifier.padding(start = 8.dp, end = 8.dp),
            )
        }

        ShizukuState.NOT_RUNNING -> Button(
            onClick = { runtime.launchManager() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(R.string.action_open_manager),
                modifier = Modifier.padding(start = 8.dp, end = 8.dp),
            )
        }

        ShizukuState.PERMISSION_NEEDED -> PermissionActions(runtime)

        ShizukuState.READY -> Unit
    }
}

/**
 * Grant button, plus a restart fallback: on some Shizuku builds a new grant is only applied
 * to a freshly attached process. When Shizuku has already said GRANTED, the restart becomes
 * the primary action.
 */
@Composable
private fun PermissionActions(runtime: ShizukuRuntime) {
    val stale = runtime.grantedButNotApplied
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (stale) {
            Text(
                text = stringResource(R.string.state_granted_not_applied),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Button(
                onClick = { runtime.restartApp() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.action_restart_app))
            }
        } else {
            Button(
                onClick = { runtime.requestPermission() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.action_request_permission),
                    modifier = Modifier.padding(start = 8.dp, end = 8.dp),
                )
            }
            Text(
                text = stringResource(R.string.state_restart_hint),
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(
                onClick = { runtime.restartApp() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.action_restart_app))
            }
        }
    }
}

@Composable
private fun UidRow(runtime: ShizukuRuntime) {
    val uid = runtime.uid
    val kind = runtime.uidKind
    Text(
        text = stringResource(
            R.string.state_uid_value,
            uid,
            stringResource(kind.labelRes()),
        ),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Start,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * Which provider is answering, and whether root-only features may appear. Shown in every
 * state so a phone report can always say what the app was running on.
 */
@Composable
private fun AccessRows(runtime: ShizukuRuntime) {
    val mode = runtime.accessMode
    Text(
        text = stringResource(
            R.string.access_provider_label,
            stringResource(mode.provider.labelRes()),
        ),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Start,
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        text = stringResource(
            if (mode.rootAvailable) R.string.access_root_available else R.string.access_root_unavailable,
        ),
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Start,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun RefreshButton(runtime: ShizukuRuntime) {
    OutlinedButton(
        onClick = { runtime.refresh() },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(R.string.action_refresh),
            modifier = Modifier.padding(start = 8.dp, end = 8.dp),
        )
    }
}

private fun ShizukuState.titleRes(): Int = when (this) {
    ShizukuState.NOT_INSTALLED -> R.string.state_not_installed_title
    ShizukuState.NOT_RUNNING -> R.string.state_not_running_title
    ShizukuState.PERMISSION_NEEDED -> R.string.state_permission_needed_title
    ShizukuState.READY -> R.string.state_ready_title
}

private fun ShizukuState.bodyRes(): Int = when (this) {
    ShizukuState.NOT_INSTALLED -> R.string.state_not_installed_body
    ShizukuState.NOT_RUNNING -> R.string.state_not_running_body
    ShizukuState.PERMISSION_NEEDED -> R.string.state_permission_needed_body
    ShizukuState.READY -> R.string.state_ready_body
}

private fun ShizukuUidKind.labelRes(): Int = when (this) {
    ShizukuUidKind.ROOT -> R.string.uid_root
    ShizukuUidKind.SHELL -> R.string.uid_shell
    ShizukuUidKind.OTHER -> R.string.uid_other
    ShizukuUidKind.UNKNOWN -> R.string.uid_unknown
}

private fun ServiceProvider.labelRes(): Int = when (this) {
    ServiceProvider.SUI -> R.string.access_provider_sui
    ServiceProvider.SHIZUKU -> R.string.access_provider_shizuku
    ServiceProvider.NONE -> R.string.access_provider_none
}

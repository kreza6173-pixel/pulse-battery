package io.github.kreza6173pixel.pulsebattery

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.kreza6173pixel.pulsebattery.exec.ExecBridge
import io.github.kreza6173pixel.pulsebattery.shizuku.ShizukuRuntime
import io.github.kreza6173pixel.pulsebattery.shizuku.ShizukuState
import io.github.kreza6173pixel.pulsebattery.ui.about.AboutScreen
import io.github.kreza6173pixel.pulsebattery.ui.charge.ChargeScreen
import io.github.kreza6173pixel.pulsebattery.ui.console.ConsoleScreen
import io.github.kreza6173pixel.pulsebattery.ui.diag.DiagnosticsScreen
import io.github.kreza6173pixel.pulsebattery.ui.health.HealthScreen
import io.github.kreza6173pixel.pulsebattery.ui.home.HomeScreen
import io.github.kreza6173pixel.pulsebattery.ui.report.DrainReportScreen
import io.github.kreza6173pixel.pulsebattery.ui.standby.StandbyScreen
import io.github.kreza6173pixel.pulsebattery.ui.theme.PulseBatteryTheme
import io.github.kreza6173pixel.pulsebattery.ui.vault.VaultScreen
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {

    private val runtime by lazy { ShizukuRuntime(applicationContext) }
    private val bridge by lazy { ExecBridge(applicationContext) }

    /**
     * The app ships English only. Pin en-US so digits, layout direction and formatting stay
     * consistent on devices set to an RTL or non-Latin-digit locale.
     */
    override fun attachBaseContext(newBase: Context) {
        Locale.setDefault(Locale.US)
        val config = Configuration(newBase.resources.configuration)
        config.setLocale(Locale.US)
        config.setLayoutDirection(Locale.US)
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            PulseBatteryTheme {
                DisposableEffect(Unit) {
                    runtime.start()
                    bridge.start()
                    onDispose {
                        bridge.stop()
                        runtime.stop()
                    }
                }

                AppRoot(runtime = runtime, bridge = bridge)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // The manager may have been started or stopped while we were backgrounded.
        runtime.refresh()
    }
}

private enum class Screen {
    HOME, REPORT, CONSOLE, DIAGNOSTICS, STANDBY, CHARGE, HEALTH, VAULT, ABOUT
}

// TopAppBar is still @ExperimentalMaterial3Api in material3 1.4.0.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRoot(runtime: ShizukuRuntime, bridge: ExecBridge) {
    var screen by remember { mutableStateOf(Screen.HOME) }

    // Bind the user service only while Shizuku is usable; this also recovers after a restart.
    val ready = runtime.state == ShizukuState.READY
    DisposableEffect(ready) {
        if (ready) bridge.connect() else bridge.disconnect()
        onDispose { bridge.disconnect() }
    }

    // Feature screens need the user service and fall back to home when not READY.
    // About does not need Shizuku, so it stays reachable in every state.
    val shown = if (ready || screen == Screen.ABOUT) screen else Screen.HOME

    BackHandler(enabled = shown != Screen.HOME) { screen = Screen.HOME }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(
                            when (shown) {
                                Screen.HOME -> R.string.app_name
                                Screen.REPORT -> R.string.report_title
                                Screen.CONSOLE -> R.string.console_title
                                Screen.DIAGNOSTICS -> R.string.diag_title
                                Screen.STANDBY -> R.string.standby_title
                                Screen.CHARGE -> R.string.charge_title
                                Screen.HEALTH -> R.string.health_title
                                Screen.VAULT -> R.string.vault_title
                                Screen.ABOUT -> R.string.about_title
                            }
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp, end = 4.dp),
                    )
                },
            )
        },
    ) { innerPadding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
        when (shown) {
            Screen.HOME -> HomeScreen(
                runtime = runtime,
                modifier = contentModifier,
                onOpenReport = { screen = Screen.REPORT },
                onOpenConsole = { screen = Screen.CONSOLE },
                onOpenDiagnostics = { screen = Screen.DIAGNOSTICS },
                onOpenStandby = { screen = Screen.STANDBY },
                onOpenCharge = { screen = Screen.CHARGE },
                onOpenHealth = { screen = Screen.HEALTH },
                onOpenVault = { screen = Screen.VAULT },
                onOpenAbout = { screen = Screen.ABOUT },
            )
            Screen.REPORT -> DrainReportScreen(bridge = bridge, modifier = contentModifier)
            Screen.CONSOLE -> ConsoleScreen(bridge = bridge, modifier = contentModifier)
            Screen.DIAGNOSTICS -> DiagnosticsScreen(bridge = bridge, modifier = contentModifier)
            Screen.STANDBY -> StandbyScreen(bridge = bridge, modifier = contentModifier)
            Screen.CHARGE -> ChargeScreen(bridge = bridge, modifier = contentModifier)
            Screen.HEALTH -> HealthScreen(
                bridge = bridge,
                rootAvailable = runtime.accessMode.rootAvailable,
                modifier = contentModifier,
            )
            Screen.VAULT -> VaultScreen(bridge = bridge, modifier = contentModifier)
            Screen.ABOUT -> AboutScreen(modifier = contentModifier)
        }
    }
}

package io.github.kreza6173pixel.pulsebattery

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
import io.github.kreza6173pixel.pulsebattery.ui.console.ConsoleScreen
import io.github.kreza6173pixel.pulsebattery.ui.diag.DiagnosticsScreen
import io.github.kreza6173pixel.pulsebattery.ui.home.HomeScreen
import io.github.kreza6173pixel.pulsebattery.ui.standby.StandbyScreen
import io.github.kreza6173pixel.pulsebattery.ui.theme.PulseBatteryTheme
import io.github.kreza6173pixel.pulsebattery.ui.vault.VaultScreen

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {

    private val runtime by lazy { ShizukuRuntime(applicationContext) }
    private val bridge by lazy { ExecBridge(applicationContext) }

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

private enum class Screen { HOME, CONSOLE, DIAGNOSTICS, STANDBY, VAULT }

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

    // Every screen except home needs the user service, so they fall back to home when not READY.
    val shown = if (ready) screen else Screen.HOME

    BackHandler(enabled = shown != Screen.HOME) { screen = Screen.HOME }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(
                            when (shown) {
                                Screen.HOME -> R.string.app_name
                                Screen.CONSOLE -> R.string.console_title
                                Screen.DIAGNOSTICS -> R.string.diag_title
                                Screen.STANDBY -> R.string.standby_title
                                Screen.VAULT -> R.string.vault_title
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
                onOpenConsole = { screen = Screen.CONSOLE },
                onOpenDiagnostics = { screen = Screen.DIAGNOSTICS },
                onOpenStandby = { screen = Screen.STANDBY },
                onOpenVault = { screen = Screen.VAULT },
            )
            Screen.CONSOLE -> ConsoleScreen(bridge = bridge, modifier = contentModifier)
            Screen.DIAGNOSTICS -> DiagnosticsScreen(bridge = bridge, modifier = contentModifier)
            Screen.STANDBY -> StandbyScreen(bridge = bridge, modifier = contentModifier)
            Screen.VAULT -> VaultScreen(bridge = bridge, modifier = contentModifier)
        }
    }
}

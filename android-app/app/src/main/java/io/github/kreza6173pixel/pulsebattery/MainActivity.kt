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
import io.github.kreza6173pixel.pulsebattery.ui.home.HomeScreen
import io.github.kreza6173pixel.pulsebattery.ui.theme.PulseBatteryTheme

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {

    private val runtime by lazy { ShizukuRuntime(applicationContext) }
    private val bridge by lazy { ExecBridge(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            PulseBatteryTheme {
                // Shizuku binder/permission listeners are registered and torn down
                // together with the composition.
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

// TopAppBar is still @ExperimentalMaterial3Api in material3 1.4.0. This is a top-level
// function, so the class-level @OptIn above does not cover it.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRoot(runtime: ShizukuRuntime, bridge: ExecBridge) {
    var consoleOpen by remember { mutableStateOf(false) }

    // Bind the user service only while Shizuku is usable, and drop back to the home screen
    // the moment it is not. This is also what recovers the connection after a Shizuku restart:
    // the state leaves READY, the effect disconnects, and a later READY binds again.
    val ready = runtime.state == ShizukuState.READY
    DisposableEffect(ready) {
        if (ready) bridge.connect() else bridge.disconnect()
        onDispose { bridge.disconnect() }
    }

    val onConsole = consoleOpen && ready

    Scaffold(
        topBar = {
            // `fillMaxWidth` + `maxLines = 1` + ellipsis stops the title being clipped at the
            // trailing edge under an RTL (fa) locale. Padding is start/end, so it mirrors.
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(
                            if (onConsole) R.string.console_title else R.string.app_name
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
        if (onConsole) {
            BackHandler { consoleOpen = false }
            ConsoleScreen(bridge = bridge, modifier = contentModifier)
        } else {
            HomeScreen(
                runtime = runtime,
                modifier = contentModifier,
                onOpenConsole = { consoleOpen = true },
            )
        }
    }
}
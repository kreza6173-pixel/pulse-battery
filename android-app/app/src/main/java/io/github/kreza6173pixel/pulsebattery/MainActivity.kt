package io.github.kreza6173pixel.pulsebattery

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.kreza6173pixel.pulsebattery.shizuku.ShizukuRuntime
import io.github.kreza6173pixel.pulsebattery.ui.home.HomeScreen
import io.github.kreza6173pixel.pulsebattery.ui.theme.PulseBatteryTheme

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {

    private val runtime by lazy { ShizukuRuntime(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            PulseBatteryTheme {
                // Shizuku binder/permission listeners are registered and torn down
                // together with the composition.
                DisposableEffect(Unit) {
                    runtime.start()
                    onDispose { runtime.stop() }
                }

                Scaffold(
                    topBar = {
                        // `fillMaxWidth` + `maxLines = 1` + ellipsis stops the title being
                        // clipped at the trailing edge under an RTL (fa) locale. Padding is
                        // start/end, so it mirrors automatically.
                        TopAppBar(
                            title = {
                                Text(
                                    text = stringResource(R.string.app_name),
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
                    // Scaffold already paints the background colour.
                    HomeScreen(
                        runtime = runtime,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // The manager may have been started or stopped while we were backgrounded.
        runtime.refresh()
    }
}
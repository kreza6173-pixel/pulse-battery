package io.github.kreza6173pixel.pulsebattery.ui.about

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.kreza6173pixel.pulsebattery.R
import io.github.kreza6173pixel.pulsebattery.ui.common.LtrMonoText

private const val REPO_URL = "https://github.com/kreza6173-pixel/pulse-battery"

private data class Library(val name: String, val coordinates: String, val license: String)

/** Libraries packaged in the APK. JUnit is test-only and not shipped. */
private val LIBRARIES = listOf(
    Library("Shizuku API", "dev.rikka.shizuku:api, provider 13.1.5", "MIT"),
    Library("Jetpack Compose (UI, Material 3)", "androidx.compose:compose-bom 2025.12.00", "Apache-2.0"),
    Library("AndroidX Activity Compose", "androidx.activity:activity-compose 1.12.1", "Apache-2.0"),
    Library("AndroidX Lifecycle", "androidx.lifecycle:lifecycle-runtime-ktx 2.10.0", "Apache-2.0"),
    Library("Kotlin standard library", "org.jetbrains.kotlin:kotlin-stdlib 2.2.21", "Apache-2.0"),
    Library("Kotlin coroutines", "org.jetbrains.kotlinx:kotlinx-coroutines (via AndroidX)", "Apache-2.0"),
)

@Composable
fun AboutScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val version = remember {
        runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "?"
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = stringResource(R.string.about_version, version),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        text = stringResource(R.string.about_tagline),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stringResource(R.string.about_no_tracking),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stringResource(R.string.about_license_app),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(REPO_URL)))
                        }
                    }) {
                        Text(stringResource(R.string.about_open_repo))
                    }
                }
            }
        }
        item {
            Text(
                text = stringResource(R.string.about_third_party),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        items(LIBRARIES) { lib ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 10.dp)) {
                    LtrMonoText(text = lib.name, style = MaterialTheme.typography.bodyMedium)
                    LtrMonoText(text = lib.coordinates, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LtrMonoText(text = lib.license, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

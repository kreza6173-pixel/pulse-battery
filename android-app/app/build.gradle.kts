plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Release signing comes only from the environment (CI secrets). Without it the release
// build is simply unsigned, so forks and pull requests still build. See docs/RELEASE.md.
val releaseKeystorePath: String? = System.getenv("PULSE_KEYSTORE_PATH")
    ?.takeIf { it.isNotBlank() && file(it).exists() }

android {
    namespace = "io.github.kreza6173pixel.pulsebattery"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.kreza6173pixel.pulsebattery"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseKeystorePath != null) {
            create("release") {
                storeFile = file(releaseKeystorePath)
                storePassword = System.getenv("PULSE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("PULSE_KEY_ALIAS")
                keyPassword = System.getenv("PULSE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (releaseKeystorePath != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        // AGP 8.x disables AIDL generation by default. Required by the Shizuku
        // UserService interface in src/main/aidl. See docs/DECISIONS.md decision 20.
        aidl = true
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/{AL2.0,LGPL2.1,LGPL2.1_}"
            )
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
        ignoreWarnings = false
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.material3)
    implementation(libs.androidx.compose.ui)
    // Shizuku client library. 13.1.5 verified against the Maven Central metadata and
    // against the published AAR's API surface; see docs/DECISIONS.md.
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
}

plugins {
    alias(libs.plugins.android.application)
    // AGP 9 built-in Kotlin: do NOT apply org.jetbrains.kotlin.android (it double-registers the
    // `kotlin` extension -> "Cannot add extension with name 'kotlin'"). AGP compiles Kotlin with
    // its bundled Kotlin 2.2.10; Kotlin JVM target defaults to 17 (>= Kotlin 2.0).
}

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

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
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
    implementation(libs.androidx.compose.bom)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.compose.ui)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
}

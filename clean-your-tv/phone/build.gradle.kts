plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "app.cleanyourtv.phone"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.cleanyourtv.phone"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "1.3.0"
    }

    // Optional fixed signing key (see signing/README.md). Never committed; without it the build uses
    // the standard debug key, which CI keeps between runs.
    val sharedKey = rootProject.file("signing/clean-your-tv.keystore")
    signingConfigs {
        create("shared") {
            storeFile = sharedKey
            storePassword = providers.environmentVariable("CYT_KEYSTORE_PASSWORD").orNull ?: "cleanyourtv"
            keyAlias = "cleanyourtv"
            keyPassword = providers.environmentVariable("CYT_KEYSTORE_PASSWORD").orNull ?: "cleanyourtv"
        }
    }

    buildTypes {
        debug {
            if (sharedKey.exists()) signingConfig = signingConfigs.getByName("shared")
        }
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures { compose = true }

    packaging {
        resources.excludes += setOf("META-INF/*.kotlin_module", "META-INF/versions/**")
    }
}

dependencies {
    implementation(project(":core"))

    // Talks ADB directly to the TV over Wi-Fi; no computer or adb server needed.
    implementation("dev.mobile:dadb:1.2.10")

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}

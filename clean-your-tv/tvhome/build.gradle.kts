plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Clean Home: an ad-free Android TV home screen. Uses only the Android framework
// (no extra libraries) so it is small and starts fast on low-memory TVs.
android {
    namespace = "app.cleanyourtv.home"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.cleanyourtv.home"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.1.0"
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
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

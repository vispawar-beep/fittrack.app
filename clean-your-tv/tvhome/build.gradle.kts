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

    // One fixed key for every build, so new builds install as updates (see signing/README.md).
    signingConfigs {
        create("shared") {
            storeFile = rootProject.file("signing/clean-your-tv.keystore")
            storePassword = "cleanyourtv"
            keyAlias = "cleanyourtv"
            keyPassword = "cleanyourtv"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("shared")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

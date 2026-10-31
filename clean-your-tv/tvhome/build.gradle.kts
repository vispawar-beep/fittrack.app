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
        versionCode = 1
        versionName = "1.0.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

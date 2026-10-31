pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("com.android.application") version "8.9.1"
        id("org.jetbrains.kotlin.android") version "2.1.21"
        id("org.jetbrains.kotlin.jvm") version "2.1.21"
        id("org.jetbrains.kotlin.plugin.compose") version "2.1.21"
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "clean-your-tv"

// core:   plain Kotlin: the TV profiles, safety rules and ADB command builder (unit-tested).
// phone:  the Clean Your TV phone app.
// tvhome: Clean Home, the ad-free TV home screen.
include(":core", ":phone", ":tvhome")

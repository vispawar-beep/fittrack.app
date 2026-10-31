// Plugins are declared once here so every module shares one copy of them.
// Module build files live in core/, phone/ and tvhome/.
plugins {
    id("com.android.application") apply false
    id("org.jetbrains.kotlin.android") apply false
    id("org.jetbrains.kotlin.jvm") apply false
    id("org.jetbrains.kotlin.plugin.compose") apply false
}

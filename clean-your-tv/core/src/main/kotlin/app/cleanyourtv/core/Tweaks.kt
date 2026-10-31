package app.cleanyourtv.core

/** One setting a speed tweak changes. The old value is saved so it can be put back. */
data class SettingChange(val ns: SettingsNamespace, val key: String, val value: String)

/**
 * A speed tweak. [changes] are reversible settings; [oneShot] commands only
 * clear things (caches, background apps) and need no undo.
 */
data class Tweak(
    val id: String,
    val title: String,
    val description: String,
    val changes: List<SettingChange> = emptyList(),
    val oneShot: List<String> = emptyList(),
    val recommended: Boolean = true,
) {
    val reversible: Boolean get() = changes.isNotEmpty()
}

object Tweaks {
    private fun anim(scale: String) = listOf(
        SettingChange(SettingsNamespace.GLOBAL, "window_animation_scale", scale),
        SettingChange(SettingsNamespace.GLOBAL, "transition_animation_scale", scale),
        SettingChange(SettingsNamespace.GLOBAL, "animator_duration_scale", scale),
    )

    val all = listOf(
        Tweak(
            id = "fast-animations",
            title = "Faster menus",
            description = "Halves Android's animation time, so menus and apps open in half the time. Undo puts the old values back.",
            changes = anim("0.5"),
        ),
        Tweak(
            id = "no-wifi-scan",
            title = "Stop background Wi-Fi scanning",
            description = "The TV keeps scanning for networks even when connected. Turning it off saves CPU and avoids picture stutter.",
            changes = listOf(
                SettingChange(SettingsNamespace.GLOBAL, "wifi_scan_always_enabled", "0"),
                SettingChange(SettingsNamespace.GLOBAL, "ble_scan_always_enabled", "0"),
            ),
        ),
        Tweak(
            id = "trim-caches",
            title = "Clear app caches",
            description = "Frees storage by clearing every app's cache. Apps rebuild only what they need. Your logins are kept.",
            oneShot = listOf("pm trim-caches 999G"),
        ),
        Tweak(
            id = "stop-background",
            title = "Stop background apps",
            description = "Closes apps idling in memory so the one you open next starts faster.",
            oneShot = listOf("am kill-all"),
        ),
        Tweak(
            id = "compile-apps",
            title = "Optimise apps (takes a few minutes)",
            description = "Runs Android's app optimiser now instead of waiting for the TV to sleep. Apps start faster afterwards.",
            oneShot = listOf("cmd package bg-dexopt-job"),
            recommended = false,
        ),
    )

    fun byId(id: String) = all.firstOrNull { it.id == id }
}

package app.cleanyourtv.core

/**
 * Builds the ADB shell commands sent to the TV. Every package name is checked
 * before it goes into a command, so nothing from the TV or the user can inject
 * extra shell syntax.
 */
object Commands {
    private val PKG = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")
    private val COMPONENT = Regex("^[A-Za-z][A-Za-z0-9_.]*/[A-Za-z0-9_.$]+$")
    private val SETTING_KEY = Regex("^[a-z0-9_]+$")
    /** Values are single-quoted in the command, so `$` and `:` (accessibility service lists) are safe. */
    private val SETTING_VALUE = Regex("^[A-Za-z0-9_.\\-/:$]+$")

    fun isValidPackage(pkg: String) = PKG.matches(pkg)

    private fun checkPkg(pkg: String) = pkg.also { require(isValidPackage(it)) { "Invalid package name: $it" } }

    const val GET_PROPS =
        "echo brand=\$(getprop ro.product.brand); echo manufacturer=\$(getprop ro.product.manufacturer); " +
            "echo model=\$(getprop ro.product.model); echo device=\$(getprop ro.product.device); " +
            "echo sdk=\$(getprop ro.build.version.sdk); echo fingerprint=\$(getprop ro.build.fingerprint); " +
            "echo serial=\$(getprop ro.serialno)"

    const val LIST_SYSTEM = "pm list packages -s"
    const val LIST_DISABLED = "pm list packages -d"
    const val LIST_ALL = "pm list packages"
    const val RESOLVE_HOME =
        "cmd package resolve-activity --brief --user 0 -a android.intent.action.MAIN -c android.intent.category.HOME"

    /** Disables for user 0 only. The APK stays on the TV, and a factory reset restores everything. */
    fun disable(pkg: String) = "pm disable-user --user 0 ${checkPkg(pkg)}"
    fun enable(pkg: String) = "pm enable --user 0 ${checkPkg(pkg)}"
    fun forceStop(pkg: String) = "am force-stop ${checkPkg(pkg)}"

    /** Wi-Fi ADB streamed installs fail silently on SCBC R2 boards, so the APK is pushed first. */
    const val APK_TMP = "/data/local/tmp/clean-home.apk"
    const val INSTALL_PUSHED = "pm install -r $APK_TMP; rm -f $APK_TMP"
    fun compileSpeed(pkg: String) = "cmd package compile -m speed -f ${checkPkg(pkg)}"

    fun setHome(component: String): String {
        require(COMPONENT.matches(component)) { "Invalid component: $component" }
        return "cmd package set-home-activity --user 0 $component"
    }

    fun startHome() = "am start -a android.intent.action.MAIN -c android.intent.category.HOME"

    fun startActivity(component: String): String {
        require(COMPONENT.matches(component)) { "Invalid component: $component" }
        return "am start -n $component"
    }

    fun settingsGet(ns: SettingsNamespace, key: String): String {
        require(SETTING_KEY.matches(key))
        return "settings get ${ns.cli} $key"
    }

    fun settingsPut(ns: SettingsNamespace, key: String, value: String?): String {
        require(SETTING_KEY.matches(key))
        if (value == null) return "settings delete ${ns.cli} $key"
        require(SETTING_VALUE.matches(value)) { "Invalid setting value: $value" }
        return "settings put ${ns.cli} $key '$value'"
    }

    /** Output of `pm disable-user` on success, e.g. "Package x new state: disabled-user". */
    fun disableSucceeded(output: String) = output.contains("new state: disabled-user")
    fun enableSucceeded(output: String) = output.contains("new state: enabled") || output.contains("new state: default")
}

enum class SettingsNamespace(val cli: String) { GLOBAL("global"), SECURE("secure"), SYSTEM("system") }

/** Parses what the TV sends back. */
object Parse {
    /** Lines like "package:com.example.app". */
    fun packages(output: String): Set<String> =
        output.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("package:") }
            .map { it.removePrefix("package:").substringBefore('=').trim() }
            .filter { Commands.isValidPackage(it) }
            .toSet()

    fun props(output: String): DeviceProps {
        val map = output.lineSequence()
            .mapNotNull { line -> line.indexOf('=').takeIf { it > 0 }?.let { line.substring(0, it).trim() to line.substring(it + 1).trim() } }
            .toMap()
        return DeviceProps(
            brand = map["brand"].orEmpty(),
            manufacturer = map["manufacturer"].orEmpty(),
            model = map["model"].orEmpty(),
            device = map["device"].orEmpty(),
            sdk = map["sdk"]?.toIntOrNull() ?: 0,
            fingerprint = map["fingerprint"].orEmpty(),
            serial = map["serial"].orEmpty(),
        )
    }

    /** `settings get` prints "null" when the key is unset. */
    fun setting(output: String): String? = output.trim().takeUnless { it.isEmpty() || it == "null" }

    /** Last non-empty line of `resolve-activity --brief`, e.g. "com.google.android.tvlauncher/.MainActivity". */
    fun homeComponent(output: String): String? =
        output.lineSequence().map { it.trim() }.lastOrNull { it.contains('/') }

    /** Joins what is installed with the catalog, sorted for display. */
    fun inventory(all: Set<String>, system: Set<String>, disabled: Set<String>, profile: TvProfile?): List<PackageView> =
        all.map { pkg ->
            val installed = InstalledPackage(pkg, system = pkg in system, enabled = pkg !in disabled)
            PackageView(installed, Catalog.resolve(installed, profile))
        }.sortedWith(compareBy({ it.rule.category.ordinal }, { it.rule.risk.ordinal }, { it.rule.label.lowercase() }))
}

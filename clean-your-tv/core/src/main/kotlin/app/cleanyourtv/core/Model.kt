package app.cleanyourtv.core

/** What a package does for the viewer, used to group the list in the app. */
enum class Category(val title: String) {
    ADS("Ads & sponsored content"),
    BLOAT("Preinstalled bloatware"),
    STREAMING("Preinstalled streaming apps"),
    EXTRAS("Vendor extras"),
    USER("Apps you installed"),
    SYSTEM("System (protected)"),
}

/**
 * How safe it is to disable a package.
 *
 * - [SAFE]: no TV function depends on it. Selected by default when [PackageRule.recommended] is true.
 * - [OPTIONAL]: safe for the TV, but you lose a feature you might use (e.g. Chromecast, Netflix).
 * - [ADVANCED]: the TV keeps working, but a vendor feature may stop or a menu entry may disappear.
 * - [PROTECTED]: needed to boot, see, hear, use the remote, connect or update. The app never disables these.
 */
enum class Risk { SAFE, OPTIONAL, ADVANCED, PROTECTED }

data class PackageRule(
    val pkg: String,
    val label: String,
    val category: Category,
    val risk: Risk,
    val why: String,
    val recommended: Boolean = false,
)

/** The getprop values the app reads from the TV. */
data class DeviceProps(
    val brand: String,
    val manufacturer: String,
    val model: String,
    val device: String,
    val sdk: Int,
    val fingerprint: String,
    val serial: String = "",
) {
    val displayName: String get() = "$brand $model ($device), Android SDK $sdk"
}

/** How the app may change the home screen on a given TV. */
enum class HomeSwitchPolicy {
    /** set-home-activity works, so the ad-free home can replace the stock launcher. */
    ALLOWED,

    /**
     * The vendor holds the Home key for the stock launcher (Realtek key intercept on SCBC boards).
     * The stock launcher stays enabled as Android's Home, and Clean Home's accessibility service sends
     * the viewer on to Clean Home whenever the stock home comes up (the "stock_home_redirect" approach
     * from github.com/Gathin23/cleanyourtv). Replacing the stock launcher outright remains a trial.
     */
    VENDOR_HOME_REDIRECT,
}

/** A package as it is on the TV right now. */
data class InstalledPackage(
    val pkg: String,
    val system: Boolean,
    val enabled: Boolean,
)

/** A package joined with the rule that applies to it on this TV. */
data class PackageView(val installed: InstalledPackage, val rule: PackageRule) {
    val pkg: String get() = installed.pkg
    val canDisable: Boolean get() = rule.risk != Risk.PROTECTED
}

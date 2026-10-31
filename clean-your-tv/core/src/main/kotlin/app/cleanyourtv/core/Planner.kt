package app.cleanyourtv.core

/** One step of a clean-up, with the command to run and the command that undoes it. */
data class Step(val description: String, val command: String, val undo: String?, val pkg: String? = null)

sealed interface PlanResult {
    data class Ok(val steps: List<Step>) : PlanResult
    data class Refused(val reasons: List<String>) : PlanResult
}

object Planner {
    const val CLEAN_HOME_PKG = "app.cleanyourtv.home"
    const val CLEAN_HOME_COMPONENT = "app.cleanyourtv.home/.HomeActivity"
    const val STOCK_HOME_PKG = "com.google.android.tvlauncher"

    /** Packages that are selected when the app first opens on a TV. */
    fun recommended(inventory: List<PackageView>): Set<String> =
        inventory.filter { it.installed.enabled && it.rule.recommended && it.rule.risk == Risk.SAFE }
            .map { it.pkg }.toSet()

    /**
     * Turns a selection into steps. Refuses the whole plan if anything selected is
     * protected, unknown to the TV, or not a valid package name.
     */
    fun disablePlan(selection: Set<String>, inventory: List<PackageView>): PlanResult {
        val byPkg = inventory.associateBy { it.pkg }
        val reasons = mutableListOf<String>()
        val steps = mutableListOf<Step>()
        for (pkg in selection.sorted()) {
            val view = byPkg[pkg]
            when {
                !Commands.isValidPackage(pkg) -> reasons += "\"$pkg\" is not a valid package name."
                view == null -> reasons += "$pkg is not installed on this TV."
                !view.canDisable -> reasons += "${view.rule.label} is protected: ${view.rule.why}"
                !view.installed.enabled -> Unit // already disabled
                else -> steps += Step(
                    description = "Disable ${view.rule.label}",
                    command = Commands.disable(pkg),
                    undo = Commands.enable(pkg),
                    pkg = pkg,
                )
            }
        }
        return if (reasons.isEmpty()) PlanResult.Ok(steps) else PlanResult.Refused(reasons)
    }

    fun enablePlan(packages: Collection<String>): List<Step> =
        packages.filter(Commands::isValidPackage).sorted().map {
            Step("Enable $it", Commands.enable(it), Commands.disable(it), it)
        }

    /**
     * Steps to make Clean Home the home screen. The stock home is disabled only after
     * Clean Home is installed; the caller must confirm with the user that the Home key
     * opens Clean Home and otherwise run [restoreStockHome].
     */
    fun cleanHomePlan(installedPackages: Set<String>, profile: TvProfile?): PlanResult {
        if (CLEAN_HOME_PKG !in installedPackages) {
            return PlanResult.Refused(listOf("Install Clean Home on the TV first."))
        }
        val steps = mutableListOf(
            Step("Make Clean Home the home screen", Commands.setHome(CLEAN_HOME_COMPONENT), null),
        )
        if (STOCK_HOME_PKG in installedPackages) {
            steps += Step("Turn off the stock home and its ads", Commands.disable(STOCK_HOME_PKG), Commands.enable(STOCK_HOME_PKG), STOCK_HOME_PKG)
        }
        steps += Step("Open Clean Home", Commands.startHome(), null)
        return PlanResult.Ok(steps)
    }

    fun restoreStockHome(): List<Step> = listOf(
        Step("Turn the stock home back on", Commands.enable(STOCK_HOME_PKG), null, STOCK_HOME_PKG),
        Step("Make the stock home the home screen", Commands.setHome("$STOCK_HOME_PKG/.MainActivity"), null),
        Step("Open the stock home", Commands.startHome(), null),
    )
}

/**
 * Home redirect for TVs whose vendor holds the Home key: Clean Home's accessibility service
 * opens Clean Home whenever the stock home comes to the front. Enabling it only appends the
 * service to the TV's list, so other accessibility services (e.g. TalkBack) keep running.
 */
object HomeRedirect {
    const val SERVICE = "app.cleanyourtv.home/app.cleanyourtv.home.HomeRedirectService"

    fun withService(existing: String?): String =
        (servicesIn(existing).filter { it != SERVICE } + SERVICE).joinToString(":")

    fun withoutService(existing: String?): String? =
        servicesIn(existing).filter { it != SERVICE }.joinToString(":").ifEmpty { null }

    fun isOn(existing: String?) = SERVICE in servicesIn(existing)

    private fun servicesIn(value: String?): List<String> =
        value.orEmpty().split(':').map { it.trim() }.filter { it.isNotEmpty() && it != "null" }

    fun enableChanges(existing: String?): List<SettingChange> = listOf(
        SettingChange(SettingsNamespace.SECURE, "enabled_accessibility_services", withService(existing)),
        SettingChange(SettingsNamespace.SECURE, "accessibility_enabled", "1"),
    )
}

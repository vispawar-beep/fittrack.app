package app.cleanyourtv.phone

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.cleanyourtv.core.Commands
import app.cleanyourtv.core.DeviceProps
import app.cleanyourtv.core.HomeRedirect
import app.cleanyourtv.core.SettingChange
import app.cleanyourtv.core.Parse
import app.cleanyourtv.core.PackageView
import app.cleanyourtv.core.PlanResult
import app.cleanyourtv.core.Planner
import app.cleanyourtv.core.Profiles
import app.cleanyourtv.core.SettingsNamespace
import app.cleanyourtv.core.Step
import app.cleanyourtv.core.TvProfile
import app.cleanyourtv.core.Tweaks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class Phase { DISCONNECTED, CONNECTING, CONNECTED, WORKING }

data class UiState(
    val host: String = "",
    val port: String = "5555",
    val phase: Phase = Phase.DISCONNECTED,
    val props: DeviceProps? = null,
    val profile: TvProfile? = null,
    val inventory: List<PackageView> = emptyList(),
    val selection: Set<String> = emptySet(),
    val tweakSelection: Set<String> = Tweaks.all.filter { it.recommended }.map { it.id }.toSet(),
    val homeComponent: String? = null,
    val history: History = History(),
    val cleanHomeInstalled: Boolean = false,
    /** Clean Home's Home redirect (accessibility service) is on. */
    val redirectOn: Boolean = false,
    /** This phone app's version; it bundles the Clean Home of the same version. */
    val appVersion: String = "",
    /** Clean Home's version on the TV, or null if it isn't installed or can't be read. */
    val tvHomeVersion: String? = null,
    val cleanHomeApkBundled: Boolean = false,
    /** Seconds left before the Clean Home trial rolls back on its own; null when no trial is running. */
    val trialSecondsLeft: Int? = null,
    val log: List<String> = emptyList(),
    val message: String? = null,
)

class CleanViewModel(app: Application) : AndroidViewModel(app) {
    private val tv = TvConnection(app)
    private val store = HistoryStore(app)
    private val _state = MutableStateFlow(
        UiState(
            host = store.lastHost(), port = store.lastPort(), cleanHomeApkBundled = bundledApkExists(),
            appVersion = runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull().orEmpty(),
        )
    )
    val state: StateFlow<UiState> = _state
    private var trialJob: Job? = null

    private val tvId: String get() = state.value.props?.let { it.serial.ifBlank { it.fingerprint } } ?: "unknown"

    fun setHost(v: String) = _state.update { it.copy(host = v.trim()) }
    fun setPort(v: String) = _state.update { it.copy(port = v.filter(Char::isDigit).take(5)) }
    fun dismissMessage() = _state.update { it.copy(message = null) }

    private fun log(line: String) = _state.update { it.copy(log = (it.log + line).takeLast(200)) }

    fun connect() {
        val s = state.value
        val port = s.port.toIntOrNull() ?: 5555
        if (s.host.isBlank()) {
            _state.update { it.copy(message = "Enter the TV's IP address (TV: Settings > Network & Internet > your Wi-Fi).") }
            return
        }
        _state.update { it.copy(phase = Phase.CONNECTING, message = null) }
        viewModelScope.launch {
            try {
                val out = tv.connect(s.host, port)
                store.saveHost(s.host, s.port)
                val props = Parse.props(out)
                _state.update { it.copy(props = props, profile = Profiles.match(props)) }
                log("Connected to ${props.displayName}")
                refresh(selectRecommended = true)
            } catch (e: Exception) {
                tv.close()
                _state.update {
                    it.copy(
                        phase = Phase.DISCONNECTED,
                        message = "Could not connect: ${e.message ?: e.javaClass.simpleName}.\n" +
                            "Check that the phone and TV are on the same Wi-Fi, that USB debugging is on, " +
                            "and accept the \"Allow debugging?\" prompt on the TV.",
                    )
                }
            }
        }
    }

    fun disconnect() {
        trialJob?.cancel()
        tv.close()
        _state.update { UiState(host = it.host, port = it.port, cleanHomeApkBundled = it.cleanHomeApkBundled) }
    }

    private suspend fun refresh(selectRecommended: Boolean = false) {
        val all = Parse.packages(tv.shell(Commands.LIST_ALL).output)
        val system = Parse.packages(tv.shell(Commands.LIST_SYSTEM).output)
        val disabled = Parse.packages(tv.shell(Commands.LIST_DISABLED).output)
        val home = Parse.homeComponent(tv.shell(Commands.RESOLVE_HOME).output)
        val inventory = Parse.inventory(all, system, disabled, state.value.profile)
        val services = Parse.setting(tv.shell(Commands.settingsGet(SettingsNamespace.SECURE, A11Y_SERVICES)).output)
        val homeVersion = if (Planner.CLEAN_HOME_PKG in all) {
            Regex("versionName=([^\\s]+)").find(tv.shell("dumpsys package ${Planner.CLEAN_HOME_PKG}").output)?.groupValues?.get(1)
        } else null
        val history = store.load(tvId)
        _state.update {
            it.copy(
                phase = Phase.CONNECTED,
                inventory = inventory,
                homeComponent = home,
                history = history,
                cleanHomeInstalled = Planner.CLEAN_HOME_PKG in all,
                redirectOn = HomeRedirect.isOn(services),
                tvHomeVersion = homeVersion,
                selection = if (selectRecommended) Planner.recommended(inventory) else it.selection.filter { p -> p in all }.toSet(),
            )
        }
    }

    // ---- Selection ----

    fun toggle(pkg: String) = _state.update {
        it.copy(selection = if (pkg in it.selection) it.selection - pkg else it.selection + pkg)
    }

    fun selectRecommended() = _state.update { it.copy(selection = Planner.recommended(it.inventory)) }
    fun clearSelection() = _state.update { it.copy(selection = emptySet()) }
    fun toggleTweak(id: String) = _state.update {
        it.copy(tweakSelection = if (id in it.tweakSelection) it.tweakSelection - id else it.tweakSelection + id)
    }

    /** Runs [block] with the UI locked, logs errors, and reloads the package list afterwards. */
    private fun work(title: String, block: suspend () -> Unit) {
        if (state.value.phase != Phase.CONNECTED) return
        _state.update { it.copy(phase = Phase.WORKING) }
        log("— $title")
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                log("Error: ${e.message}")
                _state.update { it.copy(message = "Stopped: ${e.message}") }
            } finally {
                runCatching { refresh() }.onFailure { log("Could not reload the app list: ${it.message}") }
                _state.update { if (it.phase == Phase.WORKING) it.copy(phase = Phase.CONNECTED) else it }
            }
        }
    }

    private suspend fun exec(step: Step): Boolean {
        val r = tv.shell(step.command)
        val ok = r.exitCode == 0 && !r.allOutput.contains("Exception") && !r.allOutput.contains("Error:")
        log((if (ok) "✓ " else "✗ ") + step.description + if (ok) "" else " — ${r.allOutput.trim()}")
        return ok
    }

    private fun saveHistory(transform: (History) -> History) {
        val h = transform(store.load(tvId))
        store.save(tvId, h)
        _state.update { it.copy(history = h) }
    }

    // ---- Clean: disable bloatware and ad sources ----

    fun applyClean() = work("Cleaning") {
        when (val plan = Planner.disablePlan(state.value.selection, state.value.inventory)) {
            is PlanResult.Refused -> _state.update { it.copy(message = plan.reasons.joinToString("\n")) }
            is PlanResult.Ok -> {
                var done = 0
                for (step in plan.steps) {
                    if (exec(step)) {
                        done++
                        step.pkg?.let { p -> saveHistory { it.copy(disabled = it.disabled + p) } }
                    }
                }
                _state.update { it.copy(message = "Disabled $done of ${plan.steps.size} apps. Restart the TV to free all the memory they used.") }
            }
        }
    }

    fun enablePackage(pkg: String) = work("Turning $pkg back on") {
        Planner.enablePlan(listOf(pkg)).forEach { step ->
            if (exec(step)) saveHistory { it.copy(disabled = it.disabled - pkg) }
        }
    }

    // ---- Speed ----

    fun applyTweaks() = work("Speeding up") {
        for (tweak in Tweaks.all.filter { it.id in state.value.tweakSelection }) {
            for (change in tweak.changes) changeSetting(tweak.title, change)
            for (cmd in tweak.oneShot) exec(Step(tweak.title, cmd, null))
        }
        _state.update { it.copy(message = "Speed tweaks applied.") }
    }

    /** Saves the setting's value from before Clean Your TV first changed it, then changes it. */
    private suspend fun changeSetting(title: String, change: SettingChange): Boolean {
        val key = "${change.ns.cli}:${change.key}"
        if (key !in store.load(tvId).savedSettings) {
            val old = Parse.setting(tv.shell(Commands.settingsGet(change.ns, change.key)).output)
            saveHistory { it.copy(savedSettings = it.savedSettings + (key to old)) }
        }
        return exec(Step("$title: ${change.key}", Commands.settingsPut(change.ns, change.key, change.value), null))
    }

    private suspend fun restoreSettings() {
        for ((key, old) in store.load(tvId).savedSettings) {
            val ns = SettingsNamespace.entries.first { it.cli == key.substringBefore(':') }
            val name = key.substringAfter(':')
            if (exec(Step("Restore $name", Commands.settingsPut(ns, name, old), null))) {
                saveHistory { it.copy(savedSettings = it.savedSettings - key) }
            }
        }
    }

    fun undoTweaks() = work("Undoing speed tweaks") { restoreSettings() }

    // ---- Look: ad-free home screen ----

    private fun bundledApkExists() =
        runCatching { getApplication<Application>().assets.open(CLEAN_HOME_ASSET).close(); true }.getOrDefault(false)

    fun installCleanHome() = work("Installing the latest Clean Home on the TV") {
        val file = File(getApplication<Application>().cacheDir, CLEAN_HOME_ASSET)
        withContext(Dispatchers.IO) {
            getApplication<Application>().assets.open(CLEAN_HOME_ASSET).use { input ->
                file.outputStream().use { input.copyTo(it) }
            }
        }
        // Streamed installs over Wi-Fi ADB fail silently on SCBC R2 boards, so push the APK first
        // (finding from github.com/Gathin23/cleanyourtv, examples/sanyo-atv-r2).
        tv.push(file, Commands.APK_TMP)
        var out = tv.shell(Commands.INSTALL_PUSHED).allOutput
        if (!out.contains("Success") && (out.contains("UPDATE_INCOMPATIBLE") || out.contains("signatures do not match"))) {
            // Clean Home from an older build was signed with a different key, and Android won't update
            // across keys. Remove it (only its hidden-apps list and weather city are lost) and install fresh.
            log("Old Clean Home has a different signature; replacing it")
            exec(Step("Remove the old Clean Home", "pm uninstall ${Planner.CLEAN_HOME_PKG}", null))
            tv.push(file, Commands.APK_TMP)
            out = tv.shell(Commands.INSTALL_PUSHED).allOutput
        }
        if (!out.contains("Success")) error("the TV refused Clean Home: ${out.trim()}")
        log("✓ Clean Home installed")
        // A reinstall unbinds the Home redirect service; set it again so Android binds the new copy.
        val services = Parse.setting(tv.shell(Commands.settingsGet(SettingsNamespace.SECURE, A11Y_SERVICES)).output)
        if (HomeRedirect.isOn(services)) {
            exec(Step("Reconnect the Home redirect", Commands.settingsPut(SettingsNamespace.SECURE, A11Y_SERVICES, HomeRedirect.withoutService(services)), null))
            exec(Step("Reconnect the Home redirect", Commands.settingsPut(SettingsNamespace.SECURE, A11Y_SERVICES, HomeRedirect.withService(services)), null))
            exec(Step("Accessibility on", Commands.settingsPut(SettingsNamespace.SECURE, "accessibility_enabled", "1"), null))
        }
        exec(Step("Optimise Clean Home for speed", Commands.compileSpeed(Planner.CLEAN_HOME_PKG), null))
        exec(Step("Open Clean Home", Commands.startActivity(Planner.CLEAN_HOME_COMPONENT), null))
    }

    fun openCleanHome() = work("Opening Clean Home") {
        exec(Step("Open Clean Home", Commands.startActivity(Planner.CLEAN_HOME_COMPONENT), null))
    }

    /**
     * For TVs that lock Home to the stock launcher: keeps the stock launcher as Android's Home and turns
     * on Clean Home's redirect service, which opens Clean Home whenever the stock home comes up.
     * Other accessibility services stay on; Undo puts the previous values back.
     */
    fun enableHomeRedirect() = work("Sending the Home button to Clean Home") {
        if (!state.value.cleanHomeInstalled) error("install Clean Home first")
        val current = Parse.setting(tv.shell(Commands.settingsGet(SettingsNamespace.SECURE, A11Y_SERVICES)).output)
        HomeRedirect.enableChanges(current).forEach { changeSetting("Home redirect", it) }
        exec(Step("Open Clean Home", Commands.startActivity(Planner.CLEAN_HOME_COMPONENT), null))
        _state.update { it.copy(message = "Done. Press Home on the remote: the stock home flashes briefly, then Clean Home opens.") }
    }

    fun disableHomeRedirect() = work("Giving the Home button back to the stock home") {
        val current = Parse.setting(tv.shell(Commands.settingsGet(SettingsNamespace.SECURE, A11Y_SERVICES)).output)
        exec(Step("Turn off the Home redirect", Commands.settingsPut(SettingsNamespace.SECURE, A11Y_SERVICES, HomeRedirect.withoutService(current)), null))
        val saved = store.load(tvId).savedSettings
        val enabledKey = "secure:accessibility_enabled"
        if (enabledKey in saved) {
            exec(Step("Restore accessibility_enabled", Commands.settingsPut(SettingsNamespace.SECURE, "accessibility_enabled", saved[enabledKey]), null))
        }
        saveHistory { it.copy(savedSettings = it.savedSettings - "secure:$A11Y_SERVICES" - enabledKey) }
        exec(Step("Open the stock home", Commands.startHome(), null))
    }

    /**
     * Makes Clean Home the home screen, then waits for the user to press Home on the
     * remote and confirm. With no confirmation in [TRIAL_SECONDS], the stock home comes back.
     */
    fun startCleanHomeTrial() = work("Trying Clean Home as the home screen") {
        val installed = state.value.inventory.map { it.pkg }.toSet()
        when (val plan = Planner.cleanHomePlan(installed, state.value.profile)) {
            is PlanResult.Refused -> _state.update { it.copy(message = plan.reasons.joinToString("\n")) }
            is PlanResult.Ok -> {
                for (step in plan.steps) {
                    if (!exec(step) && step != plan.steps.last()) {
                        rollbackHome("A step failed, so the stock home was put back.")
                        return@work
                    }
                }
                saveHistory { it.copy(cleanHomeActive = true) }
                startTrialTimer()
            }
        }
    }

    private fun startTrialTimer() {
        trialJob?.cancel()
        trialJob = viewModelScope.launch {
            for (s in TRIAL_SECONDS downTo 1) {
                _state.update { it.copy(trialSecondsLeft = s) }
                delay(1000)
            }
            _state.update { it.copy(trialSecondsLeft = null) }
            rollbackHomeNow("No answer, so the stock home was put back. Nothing else changed.")
        }
    }

    fun confirmCleanHome() {
        trialJob?.cancel()
        _state.update { it.copy(trialSecondsLeft = null, message = "Clean Home is now your home screen. Undo puts the stock home back.") }
    }

    fun rejectCleanHome() {
        trialJob?.cancel()
        _state.update { it.copy(trialSecondsLeft = null) }
        rollbackHomeNow("The stock home is back. Your TV's Home key is locked to it, so open Clean Home from the apps row instead.")
    }

    private fun rollbackHomeNow(message: String) {
        // Waits for any running work so the rollback never overlaps another command.
        viewModelScope.launch {
            while (state.value.phase == Phase.WORKING) delay(200)
            if (state.value.phase != Phase.CONNECTED) return@launch
            work("Putting the stock home back") { rollbackHome(message) }
        }
    }

    private suspend fun rollbackHome(message: String) {
        Planner.restoreStockHome().forEach { exec(it) }
        saveHistory { it.copy(cleanHomeActive = false, disabled = it.disabled - Planner.STOCK_HOME_PKG) }
        _state.update { it.copy(message = message) }
    }

    // ---- Undo everything ----

    fun restoreEverything() = work("Undoing every change") {
        val h = store.load(tvId)
        if (h.cleanHomeActive) rollbackHome("The stock home is back.")
        for (step in Planner.enablePlan(h.disabled)) {
            if (exec(step)) step.pkg?.let { p -> saveHistory { it.copy(disabled = it.disabled - p) } }
        }
        restoreSettings()
        _state.update { it.copy(message = "Your TV is back to how it was before Clean Your TV.") }
    }

    /** A plain-text report like the one in the issue, to share or keep. */
    fun report(): String {
        val s = state.value
        val p = s.props ?: return "Not connected."
        val disabled = s.inventory.filter { !it.installed.enabled }.map { it.pkg }
        return buildString {
            appendLine("Clean Your TV: TV report")
            appendLine("Brand: ${p.brand}\nManufacturer: ${p.manufacturer}\nModel: ${p.model}\nDevice: ${p.device}")
            appendLine("Android SDK: ${p.sdk}\nBuild: ${p.fingerprint}")
            appendLine("Profile: ${s.profile?.name ?: "none"}")
            appendLine("Home screen: ${s.homeComponent ?: "unknown"}")
            appendLine("Disabled apps (${disabled.size}): ${disabled.joinToString()}")
            appendLine("Changed by Clean Your TV: ${s.history.disabled.size} apps, ${s.history.savedSettings.size} settings")
        }
    }

    override fun onCleared() {
        tv.close()
    }

    companion object {
        const val CLEAN_HOME_ASSET = "clean-home.apk"
        const val TRIAL_SECONDS = 60
        const val A11Y_SERVICES = "enabled_accessibility_services"
    }
}

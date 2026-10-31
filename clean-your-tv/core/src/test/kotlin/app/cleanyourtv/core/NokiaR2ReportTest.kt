package app.cleanyourtv.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Uses the package list read from the user's Nokia TV on 2026-10-30. */
class NokiaR2ReportTest {
    private val systemApps = """
        android, android.autoinstalls.config.scbc.device, com.amazon.amazonvideo.livingroom,
        com.android.backupconfirm, com.android.bluetooth, com.android.captiveportallogin,
        com.android.certinstaller, com.android.companiondevicemanager, com.android.cts.ctsshim,
        com.android.cts.priv.ctsshim, com.android.dreams.basic, com.android.dynsystem,
        com.android.externalstorage, com.android.gallery3d, com.android.hotspot2.osulogin,
        com.android.htmlviewer, com.android.inputdevices, com.android.keychain, com.android.localtransport,
        com.android.location.fused, com.android.networkstack.inprocess,
        com.android.networkstack.permissionconfig, com.android.networkstack.tethering.inprocess,
        com.android.pacprocessor, com.android.printspooler, com.android.providers.calendar,
        com.android.providers.contacts, com.android.providers.downloads, com.android.providers.media,
        com.android.providers.media.module, com.android.providers.settings, com.android.providers.tv,
        com.android.providers.userdictionary, com.android.proxyhandler, com.android.se,
        com.android.settings.intelligence, com.android.sharedstoragebackup, com.android.shell,
        com.android.soundpicker, com.android.statementservice, com.android.systemui, com.android.tv,
        com.android.tv.settings, com.android.vpndialogs, com.android.wallpaper.livepicker,
        com.android.wallpaperbackup, com.android.wifi.resources, com.apps.authota, com.apps.bluetoothremote,
        com.apps.channels, com.apps.clear, com.apps.eshow, com.apps.esticker, com.apps.factory.ui,
        com.apps.fivekey, com.apps.function.audiolink, com.apps.function.gdpr, com.apps.networkspeed,
        com.apps.ota, com.apps.puresound, com.apps.smartkey, com.apps.sys, com.apps.timeclock,
        com.apps.usbmediaplayer, com.apps.virtualkey, com.apps.wifihotspot, com.creative.fastscreen.tv,
        com.google.android.apps.initsetup, com.google.android.apps.mediashell,
        com.google.android.apps.nbu.smartconnect.tv, com.google.android.backdrop,
        com.google.android.ext.services, com.google.android.ext.shared, com.google.android.feedback,
        com.google.android.gms, com.google.android.gsf, com.google.android.inputmethod.latin,
        com.google.android.katniss, com.google.android.marvin.talkback, com.google.android.modulemetadata,
        com.google.android.onetimeinitializer, com.google.android.overlay.modules.ext.services,
        com.google.android.overlay.modules.modulemetadata.forframework,
        com.google.android.overlay.modules.permissioncontroller,
        com.google.android.overlay.modules.permissioncontroller.forframework,
        com.google.android.packageinstaller, com.google.android.partnersetup,
        com.google.android.permissioncontroller, com.google.android.play.games,
        com.google.android.presetup.bluetoothpair, com.google.android.sss.authbridge,
        com.google.android.syncadapters.calendar, com.google.android.tts,
        com.google.android.tungsten.setupwraith, com.google.android.tv.frameworkpackagestubs,
        com.google.android.tv.remote.service, com.google.android.tvlauncher,
        com.google.android.tvrecommendations, com.google.android.webview, com.google.android.youtube.tv,
        com.google.android.youtube.tvmusic, com.netflix.ninja, com.realtek.dtv, com.realtek.factorytools,
        com.realtek.keyinterceptserver, com.realtek.leanbacklauncher.partnercustomizer,
        com.realtek.media.notification, com.realtek.providers.tv, com.realtek.tv.actionmenu,
        com.realtek.tv.atsc, com.realtek.tv.atv, com.realtek.tv.isdb, com.realtek.tv.passthrough,
        com.realtek.tv.settings, com.rtk.engmenu, com.vewd.core.service,
        com.android.vending, com.google.android.videos
    """.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    private val props = Parse.props(
        """
        brand=NOKIA
        manufacturer=SCBC
        model=ATV R2
        device=R2
        sdk=30
        fingerprint=NOKIA/NOKIA/R2:11/RTT7.220308.001/1050:user/release-keys
        serial=ABC123
        """.trimIndent()
    )

    private val profile = Profiles.match(props)
    private val inventory = Parse.inventory(systemApps + "com.example.userapp", systemApps, emptySet(), profile)

    @Test fun reportHas118SystemApps() = assertEquals(118, systemApps.size)

    @Test fun matchesNokiaProfile() {
        assertEquals(Profiles.NOKIA_SCBC_R2, profile)
        assertEquals(HomeSwitchPolicy.VENDOR_HOME_TRIAL, profile!!.homeSwitch)
        assertEquals(30, props.sdk)
    }

    @Test fun recommendedSelectionRemovesAdSources() {
        val rec = Planner.recommended(inventory)
        for (pkg in listOf(
            "com.google.android.tvrecommendations", "com.google.android.videos",
            "com.realtek.leanbacklauncher.partnercustomizer", "com.vewd.core.service",
            "android.autoinstalls.config.scbc.device", "com.apps.eshow", "com.apps.esticker",
        )) assertTrue(pkg in rec, "$pkg should be recommended")
        // Streaming apps and features the user may want are never preselected.
        for (pkg in listOf("com.netflix.ninja", "com.google.android.youtube.tv", "com.amazon.amazonvideo.livingroom",
            "com.google.android.apps.mediashell", "com.google.android.katniss", "com.example.userapp"))
            assertFalse(pkg in rec, "$pkg should not be preselected")
    }

    @Test fun criticalPackagesAreProtected() {
        val byPkg = inventory.associateBy { it.pkg }
        for (pkg in listOf(
            "android", "com.android.systemui", "com.google.android.tvlauncher", "com.realtek.keyinterceptserver",
            "com.apps.bluetoothremote", "com.realtek.tv.passthrough", "com.android.providers.tv",
            "com.google.android.gms", "com.android.vending", "com.android.shell", "com.android.tv.settings",
            "com.google.android.inputmethod.latin", "com.realtek.tv.settings", "com.android.wifi.resources",
            // Not in the catalog: system apps default to protected.
            "com.google.android.sss.authbridge", "com.android.backupconfirm",
        )) {
            val v = assertNotNull(byPkg[pkg], pkg)
            assertEquals(Risk.PROTECTED, v.rule.risk, "$pkg must be protected")
        }
        assertEquals(Category.USER, byPkg.getValue("com.example.userapp").rule.category)
    }

    @Test fun plannerRefusesProtectedSelection() {
        val result = Planner.disablePlan(setOf("com.google.android.tvrecommendations", "com.realtek.keyinterceptserver"), inventory)
        assertIs<PlanResult.Refused>(result)
    }

    @Test fun plannerBuildsDisableAndUndo() {
        val result = Planner.disablePlan(Planner.recommended(inventory), inventory)
        assertIs<PlanResult.Ok>(result)
        val step = result.steps.first { it.pkg == "com.google.android.tvrecommendations" }
        assertEquals("pm disable-user --user 0 com.google.android.tvrecommendations", step.command)
        assertEquals("pm enable --user 0 com.google.android.tvrecommendations", step.undo)
        assertTrue(result.steps.none { it.pkg == "com.google.android.tvlauncher" })
    }

    @Test fun alreadyDisabledIsSkipped() {
        val inv = Parse.inventory(systemApps, systemApps, setOf("com.apps.eshow"), profile)
        val result = Planner.disablePlan(setOf("com.apps.eshow"), inv)
        assertIs<PlanResult.Ok>(result)
        assertTrue(result.steps.isEmpty())
    }

    @Test fun cleanHomeNeedsInstallFirst() {
        assertIs<PlanResult.Refused>(Planner.cleanHomePlan(systemApps, profile))
        val ok = Planner.cleanHomePlan(systemApps + Planner.CLEAN_HOME_PKG, profile)
        assertIs<PlanResult.Ok>(ok)
        assertEquals("cmd package set-home-activity --user 0 app.cleanyourtv.home/.HomeActivity", ok.steps.first().command)
    }

    @Test fun commandsRejectInjection() {
        assertFalse(Commands.isValidPackage("com.x; reboot"))
        assertFalse(Commands.isValidPackage("com.x\nreboot"))
        assertEquals(setOf("com.a.b"), Parse.packages("package:com.a.b\npackage:bad;rm -rf\njunk"))
    }

    @Test fun parsesHomeAndSettings() {
        assertEquals("com.google.android.tvlauncher/.MainActivity",
            Parse.homeComponent("priority=2 preferredOrder=0\ncom.google.android.tvlauncher/.MainActivity\n"))
        assertEquals(null, Parse.setting("null\n"))
        assertEquals("1.0", Parse.setting("1.0\n"))
        assertEquals("settings delete global window_animation_scale",
            Commands.settingsPut(SettingsNamespace.GLOBAL, "window_animation_scale", null))
    }
}

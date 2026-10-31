package app.cleanyourtv.core

/**
 * Rules for packages found on Android TV sets. Anything not listed here and
 * shipped as a system app is treated as PROTECTED: the app only disables
 * packages it knows are safe to disable.
 */
object Catalog {

    private fun safe(pkg: String, label: String, category: Category, why: String, recommended: Boolean = true) =
        PackageRule(pkg, label, category, Risk.SAFE, why, recommended)

    private fun optional(pkg: String, label: String, category: Category, why: String) =
        PackageRule(pkg, label, category, Risk.OPTIONAL, why, recommended = false)

    private fun advanced(pkg: String, label: String, category: Category, why: String) =
        PackageRule(pkg, label, category, Risk.ADVANCED, why, recommended = false)

    private fun protect(pkg: String, label: String, why: String) =
        PackageRule(pkg, label, Category.SYSTEM, Risk.PROTECTED, why)

    /** Generic Android TV / Google rules that apply to any brand. */
    val generic: List<PackageRule> = listOf(
        // Ads and sponsored rows on the stock Android TV home screen.
        safe("com.google.android.tvrecommendations", "TV recommendations service", Category.ADS,
            "Feeds the sponsored and \"recommended for you\" rows on the home screen."),
        safe("com.google.android.videos", "Google TV / Play Movies", Category.ADS,
            "Pushes movie and show promotions into a home-screen row. Purchased films still play on other devices."),
        safe("com.google.android.youtube.tvmusic", "YouTube Music", Category.BLOAT,
            "Preinstalled music app with its own promoted home-screen row."),
        safe("com.google.android.play.games", "Google Play Games", Category.BLOAT,
            "Games hub that is rarely used on a TV."),
        safe("com.google.android.feedback", "Google feedback", Category.BLOAT,
            "Sends crash and feedback reports to Google; not needed to use the TV."),
        safe("com.google.android.onetimeinitializer", "One-time initializer", Category.BLOAT,
            "Runs once after setup; nothing uses it afterwards."),
        safe("com.android.printspooler", "Print spooler", Category.BLOAT, "Printing from a TV is not used."),
        safe("com.android.wallpaper.livepicker", "Live wallpaper picker", Category.BLOAT,
            "TVs do not show a wallpaper picker."),
        safe("com.android.hotspot2.osulogin", "Passpoint sign-up", Category.BLOAT,
            "Hotspot 2.0 sign-up for carrier Wi-Fi; home Wi-Fi does not use it."),
        safe("com.android.dynsystem", "Dynamic system updates", Category.BLOAT,
            "Developer tool for test system images."),

        optional("com.google.android.youtube.tv", "YouTube", Category.STREAMING, "Disable only if you do not watch YouTube on the TV."),
        optional("com.netflix.ninja", "Netflix", Category.STREAMING, "Disable only if you do not use Netflix. The remote's Netflix key will stop working."),
        optional("com.amazon.amazonvideo.livingroom", "Prime Video", Category.STREAMING, "Disable only if you do not use Prime Video. The remote's Prime key will stop working."),
        optional("com.google.android.apps.mediashell", "Chromecast built-in", Category.EXTRAS,
            "Disable only if you never cast from your phone. Uses memory in the background."),
        optional("com.google.android.backdrop", "Ambient mode screensaver", Category.EXTRAS,
            "Downloads photos and tips for the screensaver. The basic screensaver remains."),
        optional("com.android.gallery3d", "Gallery", Category.EXTRAS, "Photo viewer; the USB media player can also show photos."),
        optional("com.google.android.marvin.talkback", "TalkBack", Category.EXTRAS, "Screen reader. Keep it if anyone in the home needs it."),
        optional("com.google.android.tts", "Google text-to-speech", Category.EXTRAS, "Needed by TalkBack and some accessibility features."),
        optional("com.google.android.apps.nbu.smartconnect.tv", "Smart Connect", Category.EXTRAS,
            "Google helper for connecting phones to the TV; casting still works through Chromecast built-in."),

        advanced("com.google.android.katniss", "Google Assistant / voice search", Category.EXTRAS,
            "Disabling stops the remote's microphone key and voice search."),
        advanced("com.google.android.partnersetup", "Google partner setup", Category.EXTRAS,
            "Brand partner configuration for Google apps. Rarely needed after setup."),

        // Protected: the TV, remote, network, store or updates depend on these.
        protect("com.google.android.tvlauncher", "Android TV home", "The stock home screen. Only replaced through the Clean Home flow, never disabled directly."),
        protect("com.android.vending", "Google Play Store", "Needed to install and update apps."),
        protect("com.google.android.gms", "Google Play services", "Needed by the Play Store, casting, sign-in and most apps."),
        protect("com.google.android.gsf", "Google services framework", "Needed by Google Play services."),
        protect("com.google.android.webview", "Android System WebView", "Many apps show pages through it."),
        protect("com.google.android.inputmethod.latin", "On-screen keyboard", "The only keyboard on the TV."),
        protect("com.google.android.tv.remote.service", "Android TV remote service", "Lets the Google TV phone app work as a remote."),
        protect("com.google.android.tungsten.setupwraith", "Setup wizard", "Disabling it can block the Home key on some TVs."),
        protect("com.google.android.apps.initsetup", "Initial setup", "Part of the setup flow."),
        protect("com.google.android.presetup.bluetoothpair", "Remote pairing at setup", "Pairs Bluetooth remotes."),
        protect("com.google.android.packageinstaller", "Package installer", "Needed to install apps, including Clean Home."),
        protect("com.google.android.permissioncontroller", "Permission controller", "Core Android component."),
        protect("com.android.tv.settings", "TV Settings", "The Settings app."),
        protect("com.android.tv", "Live TV", "Watches antenna and cable channels."),
        protect("com.android.systemui", "System UI", "Core Android component."),
        protect("com.android.shell", "Shell", "Clean Your TV talks to the TV through it."),
        protect("com.android.bluetooth", "Bluetooth", "Bluetooth remote and audio."),
        protect("android", "Android system", "Core Android component."),
    )

    /** SCBC / Realtek boards: Nokia, and other brands built by SCBC. */
    val scbcRealtek: List<PackageRule> = listOf(
        safe("com.vewd.core.service", "Vewd (Opera TV) core", Category.ADS,
            "Vendor web engine for the Vewd app store and HbbTV overlays; runs in the background."),
        safe("com.realtek.leanbacklauncher.partnercustomizer", "Partner home customizer", Category.ADS,
            "Injects the manufacturer's promoted apps and channels into the home screen."),
        safe("android.autoinstalls.config.scbc.device", "SCBC auto-installer config", Category.ADS,
            "Tells Android to silently install partner apps after updates."),
        safe("com.apps.eshow", "Store demo (eShow)", Category.BLOAT, "Retail shop demo mode."),
        safe("com.apps.esticker", "Store sticker overlay", Category.BLOAT, "Retail shop feature banner overlay."),
        safe("com.apps.networkspeed", "Network speed test", Category.BLOAT, "Vendor speed test app."),
        safe("com.creative.fastscreen.tv", "FastScreen mirroring", Category.BLOAT,
            "Vendor screen mirroring that runs in the background. Chromecast built-in does the same job."),

        optional("com.apps.usbmediaplayer", "USB media player", Category.EXTRAS, "Plays films and photos from a USB stick."),
        optional("com.apps.wifihotspot", "TV Wi-Fi hotspot", Category.EXTRAS, "Shares the TV's connection as a hotspot."),

        advanced("com.apps.timeclock", "Sleep timer and clock", Category.EXTRAS, "Vendor sleep timer and on/off timer."),
        advanced("com.apps.function.gdpr", "Vendor privacy consent", Category.EXTRAS, "Shows the manufacturer's privacy notice."),
        advanced("com.apps.clear", "Vendor memory cleaner", Category.EXTRAS, "Vendor cleaner. Clean Your TV's speed tools replace it."),
        advanced("com.apps.ota", "Vendor firmware updater", Category.EXTRAS,
            "Disabling stops firmware updates, including security fixes. Not advised."),

        protect("com.realtek.keyinterceptserver", "Remote key handler", "Routes remote keys, including Home. Disabling can leave the remote unusable."),
        protect("com.apps.bluetoothremote", "Bluetooth remote", "Pairs and runs the Bluetooth remote."),
        protect("com.apps.smartkey", "Remote smart keys", "Handles the remote's app keys."),
        protect("com.apps.fivekey", "Remote navigation keys", "Handles remote keys."),
        protect("com.apps.virtualkey", "Virtual keys", "Handles remote keys."),
        protect("com.apps.sys", "Vendor system service", "Core vendor service."),
        protect("com.apps.authota", "Update authorisation", "Part of firmware updates."),
        protect("com.apps.channels", "Channel list", "Antenna and cable channels."),
        protect("com.apps.puresound", "Sound processing", "Picture and sound settings use it."),
        protect("com.apps.function.audiolink", "Audio output link", "Speakers, soundbars and Bluetooth audio."),
        protect("com.apps.factory.ui", "Factory menu", "Service menu; changing it has no benefit."),
        protect("com.realtek.factorytools", "Factory tools", "Service tools; changing it has no benefit."),
        protect("com.rtk.engmenu", "Engineering menu", "Service menu; changing it has no benefit."),
        protect("com.realtek.dtv", "Digital TV tuner", "Antenna and cable channels."),
        protect("com.realtek.tv.atv", "Analogue tuner", "Antenna and cable channels."),
        protect("com.realtek.tv.atsc", "ATSC tuner", "Antenna and cable channels."),
        protect("com.realtek.tv.isdb", "ISDB tuner", "Antenna and cable channels."),
        protect("com.realtek.tv.passthrough", "HDMI and AV inputs", "Needed to watch HDMI sources."),
        protect("com.realtek.tv.actionmenu", "Quick settings menu", "The picture/sound menu on the remote."),
        protect("com.realtek.tv.settings", "Realtek TV settings", "Picture, sound and input settings; also the fallback home."),
        protect("com.realtek.providers.tv", "TV data provider", "Channel data."),
        protect("com.realtek.media.notification", "Media notifications", "Vendor media service."),
    )

    private val byPkg: Map<String, PackageRule> = (generic + scbcRealtek).associateBy { it.pkg }

    fun ruleFor(pkg: String, profile: TvProfile?): PackageRule? =
        profile?.overrides?.get(pkg) ?: byPkg[pkg]

    /**
     * The rule to use for a package on a TV. System packages without a rule are protected;
     * apps the user installed may be disabled.
     */
    fun resolve(pkg: InstalledPackage, profile: TvProfile?): PackageRule {
        ruleFor(pkg.pkg, profile)?.let { return it }
        if (isCoreAndroid(pkg.pkg) || pkg.system) {
            return PackageRule(pkg.pkg, pkg.pkg, Category.SYSTEM, Risk.PROTECTED,
                "System app not in the Clean Your TV catalog, so it is left alone.")
        }
        return PackageRule(pkg.pkg, pkg.pkg, Category.USER, Risk.OPTIONAL,
            "An app you installed. Disable it to free memory; enable it again any time.")
    }

    private val corePrefixes = listOf(
        "com.android.providers.", "com.android.networkstack", "com.android.wifi.",
        "com.google.android.ext.", "com.google.android.overlay.", "com.google.android.modulemetadata",
        "com.android.cts.", "com.android.se", "com.android.keychain", "com.android.inputdevices",
        "com.android.location.", "com.android.externalstorage", "com.android.certinstaller",
    )

    private fun isCoreAndroid(pkg: String) = corePrefixes.any { pkg.startsWith(it) }
}

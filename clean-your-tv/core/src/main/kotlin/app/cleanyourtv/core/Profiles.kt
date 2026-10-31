package app.cleanyourtv.core

/** A known TV model: how to recognise it and what differs from the catalog. */
data class TvProfile(
    val id: String,
    val name: String,
    val homeSwitch: HomeSwitchPolicy,
    /** Packages whose rule differs on this model. */
    val overrides: Map<String, PackageRule> = emptyMap(),
    val notes: List<String> = emptyList(),
    private val matcher: (DeviceProps) -> Boolean,
) {
    fun matches(props: DeviceProps) = matcher(props)
}

object Profiles {

    /**
     * Nokia Smart TV built by SCBC on the Realtek "R2" board, Android TV 11.
     * Read from the user's TV: NOKIA/NOKIA/R2:11/RTT7.220308.001/1050:user/release-keys.
     */
    val NOKIA_SCBC_R2 = TvProfile(
        id = "nokia-scbc-r2",
        name = "Nokia Smart TV (SCBC ATV R2, Android TV 11)",
        homeSwitch = HomeSwitchPolicy.VENDOR_HOME_TRIAL,
        notes = listOf(
            "The Home key is handled by Realtek's key intercept service. Clean Home can be tried as the " +
                "home screen; if the Home key still opens the stock home, Clean Your TV puts it back.",
            "Antenna/cable tuners, HDMI inputs, the Bluetooth remote and firmware updates are protected.",
        ),
        matcher = { p ->
            p.manufacturer.equals("SCBC", ignoreCase = true) &&
                (p.device.equals("R2", ignoreCase = true) || p.model.contains("R2", ignoreCase = true))
        },
    )

    /** Any other SCBC / Realtek TV (Nokia, and other brands SCBC builds). */
    val SCBC_GENERIC = TvProfile(
        id = "scbc-realtek",
        name = "SCBC / Realtek Android TV",
        homeSwitch = HomeSwitchPolicy.VENDOR_HOME_TRIAL,
        matcher = { p -> p.manufacturer.equals("SCBC", ignoreCase = true) },
    )

    val all = listOf(NOKIA_SCBC_R2, SCBC_GENERIC)

    fun match(props: DeviceProps): TvProfile? = all.firstOrNull { it.matches(props) }
}

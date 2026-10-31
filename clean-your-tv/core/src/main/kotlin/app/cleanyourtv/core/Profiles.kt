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
        homeSwitch = HomeSwitchPolicy.VENDOR_HOME_REDIRECT,
        notes = listOf(
            "The Home key is held for the stock home by Realtek's key service, so Clean Home takes over with the " +
                "Home redirect: the stock home stays installed and sends you straight on to Clean Home.",
            "Same SCBC R2 board as the SANYO and Compac ATV R2 lists in github.com/Gathin23/cleanyourtv.",
            "Tuners, HDMI inputs, the Bluetooth remote, remote keys and firmware updates are protected.",
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
        homeSwitch = HomeSwitchPolicy.VENDOR_HOME_REDIRECT,
        matcher = { p -> p.manufacturer.equals("SCBC", ignoreCase = true) },
    )

    val all = listOf(NOKIA_SCBC_R2, SCBC_GENERIC)

    fun match(props: DeviceProps): TvProfile? = all.firstOrNull { it.matches(props) }
}

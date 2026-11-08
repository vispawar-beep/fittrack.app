package app.cleanyourtv.home

import android.content.SharedPreferences
import android.graphics.Color

/**
 * A look for Clean Home. Everything visual reads from the current skin, so switching skins needs no
 * restart of the TV: Clean Home just redraws.
 */
data class Skin(
    val id: String,
    val name: String,
    val tagline: String,
    /** Wallpaper gradient, top-left to bottom-right. */
    val base: IntArray,
    /** Soft glowing orbs on the wallpaper: x, y (0..1 of the screen), radius (of the larger side), colour. */
    val orbs: List<Orb>,
    /** Glass panels (clock, weather, dock): fill at the top and at the bottom, and the rim. */
    val panelTop: Int,
    val panelBottom: Int,
    val rim: Int,
    /** Card behind apps that have no TV banner. */
    val cardTop: Int,
    val cardBottom: Int,
    /** Focus ring and highlights. */
    val accent: Int,
    val text: Int,
    val textSoft: Int,
    val textFaint: Int,
    /** Font family for the big clock. */
    val clockFont: String,
) {
    data class Orb(val x: Float, val y: Float, val r: Float, val color: Int)
}

object Skins {
    private fun a(alpha: Int, rgb: Int) = Color.argb(alpha, Color.red(rgb), Color.green(rgb), Color.blue(rgb))
    private val WHITE = Color.WHITE

    val CRYSTAL = Skin(
        id = "crystal",
        name = "Crystal",
        tagline = "Frosted glass over a deep night sky",
        base = intArrayOf(Color.rgb(8, 14, 40), Color.rgb(22, 16, 58), Color.rgb(6, 30, 44)),
        orbs = listOf(
            Skin.Orb(0.12f, 0.18f, 0.55f, Color.argb(150, 64, 120, 255)),
            Skin.Orb(0.88f, 0.12f, 0.45f, Color.argb(130, 170, 90, 255)),
            Skin.Orb(0.70f, 0.92f, 0.60f, Color.argb(120, 40, 210, 200)),
            Skin.Orb(0.25f, 0.95f, 0.40f, Color.argb(100, 255, 90, 160)),
        ),
        panelTop = a(74, WHITE), panelBottom = a(26, WHITE), rim = a(90, WHITE),
        cardTop = a(70, WHITE), cardBottom = a(28, WHITE),
        accent = Color.rgb(170, 225, 255),
        text = WHITE, textSoft = a(200, WHITE), textFaint = a(150, WHITE),
        clockFont = "sans-serif-thin",
    )

    val MIDNIGHT = Skin(
        id = "midnight",
        name = "Midnight",
        tagline = "Pure black, calm and cinematic",
        base = intArrayOf(Color.rgb(0, 0, 0), Color.rgb(6, 6, 10), Color.rgb(0, 0, 0)),
        orbs = listOf(
            Skin.Orb(0.50f, -0.10f, 0.60f, Color.argb(70, 120, 130, 160)),
        ),
        panelTop = a(40, WHITE), panelBottom = a(16, WHITE), rim = a(50, WHITE),
        cardTop = Color.rgb(36, 36, 42), cardBottom = Color.rgb(20, 20, 24),
        accent = Color.WHITE,
        text = WHITE, textSoft = a(180, WHITE), textFaint = a(110, WHITE),
        clockFont = "sans-serif-light",
    )

    val AURORA = Skin(
        id = "aurora",
        name = "Aurora",
        tagline = "Northern lights in green and violet",
        base = intArrayOf(Color.rgb(2, 18, 28), Color.rgb(4, 28, 38), Color.rgb(16, 8, 40)),
        orbs = listOf(
            Skin.Orb(0.20f, 0.10f, 0.60f, Color.argb(150, 40, 230, 150)),
            Skin.Orb(0.55f, 0.25f, 0.45f, Color.argb(110, 60, 200, 255)),
            Skin.Orb(0.90f, 0.40f, 0.50f, Color.argb(130, 160, 80, 255)),
            Skin.Orb(0.40f, 1.05f, 0.50f, Color.argb(90, 20, 120, 160)),
        ),
        panelTop = a(64, WHITE), panelBottom = a(20, WHITE), rim = a(80, Color.rgb(180, 255, 220)),
        cardTop = a(60, Color.rgb(200, 255, 235)), cardBottom = a(22, WHITE),
        accent = Color.rgb(110, 255, 190),
        text = WHITE, textSoft = a(205, Color.rgb(220, 255, 240)), textFaint = a(150, WHITE),
        clockFont = "sans-serif-thin",
    )

    val SUNSET = Skin(
        id = "sunset",
        name = "Sunset",
        tagline = "Warm saffron and rose, like an evening sky",
        base = intArrayOf(Color.rgb(40, 10, 30), Color.rgb(80, 22, 40), Color.rgb(30, 12, 50)),
        orbs = listOf(
            Skin.Orb(0.85f, 0.90f, 0.60f, Color.argb(170, 255, 140, 40)),
            Skin.Orb(0.15f, 0.20f, 0.50f, Color.argb(120, 255, 80, 120)),
            Skin.Orb(0.55f, 0.45f, 0.40f, Color.argb(80, 255, 200, 90)),
        ),
        panelTop = a(70, Color.rgb(255, 235, 220)), panelBottom = a(24, WHITE), rim = a(90, Color.rgb(255, 210, 170)),
        cardTop = a(70, Color.rgb(255, 225, 200)), cardBottom = a(26, WHITE),
        accent = Color.rgb(255, 196, 92),
        text = WHITE, textSoft = a(210, Color.rgb(255, 236, 220)), textFaint = a(160, WHITE),
        clockFont = "sans-serif-light",
    )

    val all = listOf(CRYSTAL, MIDNIGHT, AURORA, SUNSET)

    fun byId(id: String?) = all.firstOrNull { it.id == id }

    fun current(prefs: SharedPreferences): Skin = byId(prefs.getString("skin", null)) ?: CRYSTAL

    fun save(prefs: SharedPreferences, skin: Skin) = prefs.edit().putString("skin", skin.id).apply()
}

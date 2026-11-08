package app.cleanyourtv.home

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.text.format.DateFormat
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Date

/**
 * Clean Home: a home screen with no ads and no recommendation rows. Glass widgets for the time, date
 * and weather, apps as Android TV style cards, a dock, the TV's name at the bottom, and a choice of
 * skins (Crystal, Midnight, Aurora, Sunset). The only network use is the weather (Open-Meteo).
 */
class HomeActivity : Activity() {

    private data class AppEntry(val pkg: String, val label: String, val banner: Drawable?, val icon: Drawable?, val launch: Intent)

    private val prefs by lazy { getSharedPreferences("home", Context.MODE_PRIVATE) }
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var skin: Skin
    private lateinit var clock: TextView
    private lateinit var date: TextView
    private lateinit var weatherIcon: GlyphTile
    private lateinit var weatherTemp: TextView
    private lateinit var weatherText: TextView
    private lateinit var tvName: TextView
    private lateinit var grid: GridView
    private val adapter = AppAdapter()
    private var lastWeatherFetch = 0L

    private val tick = object : Runnable {
        override fun run() {
            val now = Date()
            clock.text = DateFormat.format(if (DateFormat.is24HourFormat(this@HomeActivity)) "HH:mm" else "h:mm", now)
            date.text = DateFormat.format("EEEE, d MMMM", now)
            if (System.currentTimeMillis() - lastWeatherFetch > WEATHER_EVERY_MS) refreshWeather()
            handler.postDelayed(this, 10_000)
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun text(size: Float, color: Int, face: String = "sans-serif", style: Int = Typeface.NORMAL) =
        TextView(this).apply {
            textSize = size
            setTextColor(color)
            typeface = Typeface.create(face, style)
            setShadowLayer(dp(6).toFloat(), 0f, dp(1).toFloat(), Color.argb(90, 0, 0, 0))
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyExtras(intent)
        skin = Skins.current(prefs)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(56), dp(28), dp(56), dp(14))
            background = SkinWallpaper(skin)
        }

        // ---- Top: time and date on the left, weather card on the right ----
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val timeBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        clock = text(72f, skin.text, skin.clockFont)
        date = text(20f, skin.textSoft, "sans-serif-light")
        timeBox.addView(clock)
        timeBox.addView(date)
        top.addView(timeBox, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(weatherCard())
        root.addView(top)

        // ---- Middle: apps as Android TV style landscape cards ----
        grid = GridView(this).apply {
            numColumns = 5
            horizontalSpacing = dp(14)
            verticalSpacing = dp(14)
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            selector = ColorDrawable(Color.TRANSPARENT)
            clipToPadding = false
            setPadding(dp(8), dp(18), dp(8), dp(8))
            adapter = this@HomeActivity.adapter
            setOnItemClickListener { _, _, pos, _ -> safeStart(this@HomeActivity.adapter.getItem(pos).launch) }
            setOnItemLongClickListener { _, _, pos, _ -> showAppMenu(this@HomeActivity.adapter.getItem(pos)); true }
        }
        root.addView(grid, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        // ---- Bottom: dock, then the TV's name ----
        root.addView(dock(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(6) })
        tvName = text(15f, skin.textFaint, "sans-serif-light").apply {
            letterSpacing = 0.18f
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, 0)
        }
        root.addView(tvName, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        setContentView(root)
        wireGridFocus()
        showCachedWeather()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val oldSkin = prefs.getString("skin", null)
        applyExtras(intent)
        if (prefs.getString("skin", null) != oldSkin) { recreate(); return }
        tvName.text = prefs.getString("tv_name", DEFAULT_NAME)
        if (intent.hasExtra(EXTRA_CITY)) { lastWeatherFetch = 0; refreshWeather() }
    }

    /**
     * Optional settings from the phone app or ADB:
     * `am start -n app.cleanyourtv.home/.HomeActivity --es skin aurora --es city Pune --es tv_name "Avnish's TV"`.
     */
    private fun applyExtras(intent: Intent?) {
        intent ?: return
        intent.getStringExtra(EXTRA_NAME)?.takeIf { it.isNotBlank() }?.let { prefs.edit().putString("tv_name", it.trim().take(40)).apply() }
        intent.getStringExtra(EXTRA_CITY)?.takeIf { it.isNotBlank() }?.let { prefs.edit().putString("w_pending", it.trim()).apply() }
        Skins.byId(intent.getStringExtra(EXTRA_SKIN))?.let { Skins.save(prefs, it) }
    }

    override fun onResume() {
        super.onResume()
        tvName.text = prefs.getString("tv_name", DEFAULT_NAME)
        adapter.set(loadApps())
        handler.post(tick)
        grid.requestFocus()
    }

    override fun onPause() {
        handler.removeCallbacks(tick)
        super.onPause()
    }

    /** Clean Home stands in for the home screen, so Back does nothing here, like the stock home. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && (isDefaultHome() || HomeRedirectService.isEnabled(this))) return true
        return super.onKeyDown(keyCode, event)
    }

    private fun isDefaultHome(): Boolean {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName == packageName
    }

    // ---------------------------------------------------------------- skins

    private fun chooseSkin() {
        val names = Skins.all.map { (if (it.id == skin.id) "✓  " else "     ") + it.name + "  —  " + it.tagline }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Choose a theme")
            .setItems(names) { _, which ->
                val chosen = Skins.all[which]
                if (chosen.id != skin.id) {
                    Skins.save(prefs, chosen)
                    recreate()
                }
            }
            .show()
    }

    // ---------------------------------------------------------------- weather

    private fun weatherCard(): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = Crystal.panel(this@HomeActivity, skin, 22f)
            setPadding(dp(14), dp(12), dp(24), dp(12))
            isFocusable = true
            isClickable = true
            setOnClickListener { askCity() }
            setOnFocusChangeListener { v, f -> v.background = Crystal.panel(this@HomeActivity, skin, 22f, f); scale(v, f) }
        }
        weatherIcon = GlyphTile(this, skin)
        card.addView(weatherIcon, LinearLayout.LayoutParams(dp(76), dp(76)))
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0) }
        weatherTemp = text(36f, skin.text, "sans-serif-light")
        weatherText = text(14f, skin.textSoft)
        col.addView(weatherTemp)
        col.addView(weatherText)
        card.addView(col)
        return card
    }

    private fun showWeather(w: WeatherNow) {
        weatherIcon.glyph = Glyph(w.glyph)
        weatherTemp.text = "${w.temp}°"
        weatherText.text = "${w.condition}\nH:${w.high}°  L:${w.low}°  ·  ${w.place}"
    }

    private fun showCachedWeather() {
        val place = Weather.loadPlace(prefs)
        val cached = place?.let { Weather.loadLast(prefs, it) }
        if (cached != null) {
            showWeather(cached)
        } else {
            weatherIcon.glyph = Glyph(GlyphKind.CLOUD_SUN)
            weatherTemp.text = "--°"
            weatherText.text = if (place == null) "Weather\nPress OK to set your city" else "Weather\n${place.name}"
        }
    }

    private fun refreshWeather() {
        lastWeatherFetch = System.currentTimeMillis()
        val pending = prefs.getString("w_pending", null)
        val known = Weather.loadPlace(prefs)
        if (pending == null && known == null) return
        Thread {
            val result = runCatching {
                val place = if (pending != null) {
                    (Weather.findPlace(pending) ?: error("City not found: $pending")).also {
                        Weather.savePlace(prefs, it)
                        prefs.edit().remove("w_pending").apply()
                    }
                } else known!!
                Weather.fetch(place)
            }
            handler.post {
                result.onSuccess { Weather.saveLast(prefs, it); showWeather(it) }
                    .onFailure { e -> weatherFailed(e) }
            }
        }.start()
    }

    private fun weatherFailed(e: Throwable) {
        if (e is IllegalStateException) {
            prefs.edit().remove("w_pending").apply()
            weatherText.text = "${e.message}\nPress OK to try another"
        } else {
            // Network errors keep the last weather on screen and try again in two minutes.
            lastWeatherFetch = System.currentTimeMillis() - WEATHER_EVERY_MS + RETRY_MS
        }
    }

    private fun askCity() {
        val input = EditText(this).apply {
            hint = "City, e.g. Pune"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            setText(Weather.loadPlace(prefs)?.name.orEmpty())
            setSelection(text.length)
        }
        val box = FrameLayout(this).apply { setPadding(dp(24), dp(8), dp(24), 0); addView(input) }
        AlertDialog.Builder(this)
            .setTitle("Weather city")
            .setView(box)
            .setPositiveButton("Save") { _, _ ->
                val city = input.text.toString().trim()
                if (city.isNotEmpty()) {
                    prefs.edit().putString("w_pending", city).apply()
                    weatherText.text = "Finding $city…"
                    refreshWeather()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ---------------------------------------------------------------- dock

    private data class DockItem(val label: String, val glyph: GlyphKind, val colors: IntArray, val action: () -> Unit)

    private fun dock(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = Crystal.panel(this@HomeActivity, skin, 26f)
            setPadding(dp(18), dp(10), dp(18), dp(10))
        }
        val items = mutableListOf<DockItem>()
        if (hasLaunch(LIVE_TV)) {
            items += DockItem("Live TV", GlyphKind.TV, intArrayOf(Color.rgb(58, 58, 64), Color.rgb(18, 18, 22))) { launchPackage(LIVE_TV) }
        }
        items += DockItem("Wi-Fi", GlyphKind.WIFI, intArrayOf(Color.rgb(64, 156, 255), Color.rgb(10, 96, 230))) {
            safeStart(Intent(Settings.ACTION_WIFI_SETTINGS))
        }
        items += DockItem("Settings", GlyphKind.GEAR, intArrayOf(Color.rgb(160, 164, 172), Color.rgb(98, 102, 110))) {
            safeStart(Intent(Settings.ACTION_SETTINGS))
        }
        items += DockItem("Themes", GlyphKind.PALETTE, intArrayOf(Color.rgb(191, 90, 242), Color.rgb(255, 55, 95))) { chooseSkin() }
        for (item in items) {
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                isFocusable = true
                isClickable = true
                setPadding(dp(14), 0, dp(14), 0)
                setOnClickListener { item.action() }
            }
            val tile = GlyphTile(this, skin).apply { colors = item.colors; glyph = Glyph(item.glyph) }
            cell.addView(tile, LinearLayout.LayoutParams(dp(58), dp(58)))
            cell.addView(text(11f, skin.textSoft).apply { text = item.label; gravity = Gravity.CENTER })
            cell.setOnFocusChangeListener { v, f -> tile.highlighted = f; scale(v, f) }
            bar.addView(cell)
        }
        return bar
    }

    // ---------------------------------------------------------------- apps

    private fun hidden(): Set<String> = prefs.getStringSet("hidden", emptySet()) ?: emptySet()

    private fun loadApps(): List<AppEntry> {
        val pm = packageManager
        val seen = LinkedHashMap<String, AppEntry>()
        val hidden = hidden()
        for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val query = Intent(Intent.ACTION_MAIN).addCategory(category)
            for (ri in pm.queryIntentActivities(query, 0)) {
                val ai = ri.activityInfo
                val pkg = ai.packageName
                if (pkg == packageName || pkg in seen || pkg in hidden) continue
                val launch = Intent(Intent.ACTION_MAIN).addCategory(category)
                    .setClassName(pkg, ai.name)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                val banner = runCatching { ai.loadBanner(pm) ?: ai.applicationInfo.loadBanner(pm) }.getOrNull()
                val icon = if (banner == null) runCatching { ai.loadIcon(pm) }.getOrNull() else null
                seen[pkg] = AppEntry(pkg, ri.loadLabel(pm).toString(), banner, icon, launch)
            }
        }
        val order = prefs.getString("order", "").orEmpty().split(',').filter { it.isNotEmpty() }
        return seen.values.sortedWith(compareBy({ order.indexOf(it.pkg).let { i -> if (i < 0) Int.MAX_VALUE else i } }, { it.label.lowercase() }))
    }

    private fun showAppMenu(app: AppEntry) {
        val options = arrayOf("Move to front", "Hide from Clean Home", "App info", "Show all hidden apps")
        AlertDialog.Builder(this)
            .setTitle(app.label)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        val order = listOf(app.pkg) + prefs.getString("order", "").orEmpty().split(',').filter { it.isNotEmpty() && it != app.pkg }
                        prefs.edit().putString("order", order.joinToString(",")).apply()
                    }
                    1 -> prefs.edit().putStringSet("hidden", hidden() + app.pkg).apply()
                    2 -> safeStart(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.pkg}")))
                    3 -> prefs.edit().remove("hidden").apply()
                }
                adapter.set(loadApps())
            }
            .show()
    }

    private fun hasLaunch(pkg: String) =
        packageManager.getLeanbackLaunchIntentForPackage(pkg) != null || packageManager.getLaunchIntentForPackage(pkg) != null

    private fun launchPackage(pkg: String) {
        val i = packageManager.getLeanbackLaunchIntentForPackage(pkg) ?: packageManager.getLaunchIntentForPackage(pkg)
        if (i != null) safeStart(i)
    }

    private fun safeStart(intent: Intent) {
        runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    // ---------------------------------------------------------------- focus

    private fun scale(v: View, focused: Boolean) {
        val s = if (focused) 1.1f else 1f
        v.animate().scaleX(s).scaleY(s).setDuration(140).start()
    }

    private fun styleCell(view: View?, focused: Boolean) {
        val card = view as? AppCard ?: return
        card.highlighted = focused
        scale(card, focused)
    }

    /** GridView moves its own selection with the D-pad; mirror it onto the cards. */
    private fun wireGridFocus() {
        grid.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            private var last: View? = null
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                styleCell(last, false)
                if (grid.hasFocus()) styleCell(view, true)
                last = view
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                styleCell(last, false)
                last = null
            }
        }
        grid.setOnFocusChangeListener { _, hasFocus -> styleCell(grid.selectedView, hasFocus) }
    }

    private inner class AppAdapter : BaseAdapter() {
        private var items: List<AppEntry> = emptyList()

        fun set(list: List<AppEntry>) { items = list; notifyDataSetChanged() }
        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = items[position].pkg.hashCode().toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val app = items[position]
            val card = (convertView as? AppCard) ?: AppCard(this@HomeActivity, skin).apply {
                layoutParams = AbsListLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(104))
            }
            card.scaleX = 1f
            card.scaleY = 1f
            card.highlighted = false
            card.banner = app.banner
            card.icon = app.icon
            card.label = app.label
            card.contentDescription = app.label
            return card
        }
    }

    companion object {
        const val DEFAULT_NAME = "Avnish's TV"
        const val EXTRA_NAME = "tv_name"
        const val EXTRA_CITY = "city"
        const val EXTRA_SKIN = "skin"
        private const val LIVE_TV = "com.android.tv"
        private const val WEATHER_EVERY_MS = 30 * 60 * 1000L
        private const val RETRY_MS = 2 * 60 * 1000L
    }
}

/** GridView children need AbsListView.LayoutParams. */
private typealias AbsListLayoutParams = android.widget.AbsListView.LayoutParams

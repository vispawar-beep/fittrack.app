package app.cleanyourtv.home

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.format.DateFormat
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Date

/**
 * Clean Home: a home screen with a clock, a few shortcuts and your apps.
 * No ads, no recommendation rows, no network calls, no background services.
 */
class HomeActivity : Activity() {

    private data class AppEntry(val pkg: String, val label: String, val banner: Drawable?, val icon: Drawable?, val launch: Intent)

    private val prefs by lazy { getSharedPreferences("home", Context.MODE_PRIVATE) }
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var clock: TextView
    private lateinit var date: TextView
    private lateinit var grid: GridView
    private val adapter = AppAdapter()

    private val tick = object : Runnable {
        override fun run() {
            val now = Date()
            clock.text = DateFormat.getTimeFormat(this@HomeActivity).format(now)
            date.text = DateFormat.format("EEEE, d MMMM", now)
            handler.postDelayed(this, 15_000)
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(48), dp(32), dp(48), dp(24))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor("#0B1F24"), Color.parseColor("#102A30"), Color.parseColor("#06131A")),
            )
        }

        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val clockBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        clock = TextView(this).apply { textSize = 44f; setTextColor(Color.WHITE); typeface = Typeface.create("sans-serif-light", Typeface.NORMAL) }
        date = TextView(this).apply { textSize = 16f; setTextColor(Color.parseColor("#9FB8BD")) }
        clockBox.addView(clock); clockBox.addView(date)
        header.addView(clockBox, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        shortcut("Live TV & inputs") { launchPackage("com.android.tv") }?.let { header.addView(it) }
        shortcut("Wi-Fi") { safeStart(Intent(Settings.ACTION_WIFI_SETTINGS)) }?.let { header.addView(it) }
        shortcut("Settings") { safeStart(Intent(Settings.ACTION_SETTINGS)) }?.let { header.addView(it) }
        root.addView(header)

        root.addView(TextView(this).apply {
            text = "Your apps"
            textSize = 18f
            setTextColor(Color.parseColor("#CFE3E6"))
            setPadding(0, dp(28), 0, dp(12))
        })

        grid = GridView(this).apply {
            numColumns = 5
            horizontalSpacing = dp(20)
            verticalSpacing = dp(20)
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            selector = GradientDrawable().apply { setColor(Color.TRANSPARENT) }
            clipToPadding = false
            setPadding(dp(8), dp(8), dp(8), dp(8))
            adapter = this@HomeActivity.adapter
            setOnItemClickListener { _, _, pos, _ -> safeStart(this@HomeActivity.adapter.getItem(pos).launch) }
            setOnItemLongClickListener { _, _, pos, _ -> showAppMenu(this@HomeActivity.adapter.getItem(pos)); true }
        }
        root.addView(grid, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        root.addView(TextView(this).apply {
            text = "Hold OK on an app to hide it or open its settings."
            textSize = 12f
            setTextColor(Color.parseColor("#6E8A90"))
            setPadding(0, dp(8), 0, 0)
        })
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        adapter.set(loadApps())
        handler.post(tick)
        grid.requestFocus()
    }

    override fun onPause() {
        handler.removeCallbacks(tick)
        super.onPause()
    }

    /**
     * When Clean Home is the home screen, or stands in for it through the Home redirect, Back does
     * nothing, like the stock home.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && (isDefaultHome() || HomeRedirectService.isEnabled(this))) return true
        return super.onKeyDown(keyCode, event)
    }

    private fun isDefaultHome(): Boolean {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName == packageName
    }

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

    private fun launchPackage(pkg: String) {
        val i = packageManager.getLeanbackLaunchIntentForPackage(pkg) ?: packageManager.getLaunchIntentForPackage(pkg)
        if (i != null) safeStart(i)
    }

    private fun safeStart(intent: Intent) {
        runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    private fun focusBackground(): GradientDrawable = GradientDrawable().apply {
        cornerRadius = dp(10).toFloat()
        setColor(Color.parseColor("#1A3A41"))
    }

    private fun applyFocusStyle(v: View, focused: Boolean) {
        (v.background as? GradientDrawable)?.setStroke(dp(3), if (focused) Color.parseColor("#5EEAD4") else Color.TRANSPARENT)
        v.animate().scaleX(if (focused) 1.08f else 1f).scaleY(if (focused) 1.08f else 1f).setDuration(120).start()
    }

    /** A header button. Returns null when [action] has nothing to open on this TV. */
    private fun shortcut(label: String, action: () -> Unit): View? {
        if (label.startsWith("Live TV") &&
            packageManager.getLeanbackLaunchIntentForPackage("com.android.tv") == null &&
            packageManager.getLaunchIntentForPackage("com.android.tv") == null
        ) return null
        return TextView(this).apply {
            text = label
            textSize = 16f
            setTextColor(Color.WHITE)
            setPadding(dp(20), dp(10), dp(20), dp(10))
            background = focusBackground()
            isFocusable = true
            isClickable = true
            setOnClickListener { action() }
            setOnFocusChangeListener { v, f -> applyFocusStyle(v, f) }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { marginStart = dp(12) }
        }
    }

    private inner class AppAdapter : BaseAdapter() {
        private var items: List<AppEntry> = emptyList()

        fun set(list: List<AppEntry>) { items = list; notifyDataSetChanged() }
        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = items[position].pkg.hashCode().toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val app = items[position]
            val tile = (convertView as? FrameLayout) ?: FrameLayout(this@HomeActivity).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(110))
                background = focusBackground()
                addView(ImageView(context).apply {
                    id = android.R.id.icon
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                addView(TextView(context).apply {
                    id = android.R.id.text1
                    setTextColor(Color.WHITE)
                    textSize = 14f
                    gravity = Gravity.CENTER
                    maxLines = 2
                }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
            }
            tile.scaleX = 1f
            tile.scaleY = 1f
            (tile.background as? GradientDrawable)?.setStroke(dp(3), Color.TRANSPARENT)
            val image = tile.findViewById<ImageView>(android.R.id.icon)
            val label = tile.findViewById<TextView>(android.R.id.text1)
            if (app.banner != null) {
                image.setImageDrawable(app.banner)
                image.setPadding(0, 0, 0, 0)
                label.visibility = View.GONE
            } else {
                image.setImageDrawable(app.icon)
                image.setPadding(dp(28), dp(10), dp(28), dp(34))
                label.text = app.label
                label.setPadding(dp(6), 0, dp(6), dp(8))
                label.visibility = View.VISIBLE
            }
            tile.contentDescription = app.label
            return tile
        }
    }

    /** GridView moves its own selection with the D-pad; mirror it onto the tiles' focus style. */
    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        grid.setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
            private var last: View? = null
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                last?.let { applyFocusStyle(it, false) }
                view?.let { applyFocusStyle(it, true) }
                last = view
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {
                last?.let { applyFocusStyle(it, false) }
                last = null
            }
        })
        grid.setOnFocusChangeListener { _, hasFocus ->
            grid.selectedView?.let { applyFocusStyle(it, hasFocus) }
        }
    }
}

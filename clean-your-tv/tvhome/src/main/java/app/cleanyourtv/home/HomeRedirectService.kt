package app.cleanyourtv.home

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

/**
 * Home guard. Clean Your TV makes Clean Home the TV's real home screen and turns the stock launcher
 * off, which survives restarts. This service is the safety net for TVs whose firmware still brings the
 * stock home back (Realtek's key service on SCBC boards such as the Nokia ATV R2, or a firmware that
 * re-enables the stock launcher): whenever a stock home screen comes to the front, it opens Clean Home.
 *
 * Same idea as `stock_home_redirect` in github.com/Gathin23/cleanyourtv (MIT). It watches only which
 * window is in front and filters no keys, so volume and app keys on the remote behave as before.
 * Android starts it again at every boot because it stays listed in enabled_accessibility_services.
 */
class HomeRedirectService : AccessibilityService() {

    private var stockHomeActivities: Set<String> = emptySet()
    private var lastOpen = 0L
    private val handler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(STOCK_HOME)
        stockHomeActivities = runCatching {
            packageManager.queryIntentActivities(home, PackageManager.MATCH_DISABLED_COMPONENTS)
                .map { it.activityInfo.name }.toSet()
        }.getOrDefault(emptySet())
        // After a restart the stock home is often on screen before Android connects this service, and no
        // window change follows. Look a few times while the TV settles.
        for (delay in BOOT_CHECKS_MS) handler.postDelayed(::checkFront, delay)
    }

    private fun checkFront() {
        val front = runCatching { rootInActiveWindow?.packageName?.toString() }.getOrNull()
        if (front == STOCK_HOME) openCleanHome()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (event.packageName?.toString() != STOCK_HOME) return
        val cls = event.className?.toString() ?: return
        // Only the stock home screen itself; its own settings pages stay reachable.
        if (stockHomeActivities.isEmpty() || cls in stockHomeActivities) openCleanHome()
    }

    private fun openCleanHome() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastOpen < 700) return
        lastOpen = now
        // Started as an app, not as a Home intent, so a stock launcher that is still Home stays the home
        // process and the remote's Netflix/YouTube keys keep working (Android 10+ background-start rules).
        runCatching {
            startActivity(
                Intent(this, HomeActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
            )
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    companion object {
        const val STOCK_HOME = "com.google.android.tvlauncher"
        private val BOOT_CHECKS_MS = longArrayOf(1500, 5000, 12000, 25000)

        fun isEnabled(context: Context): Boolean =
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                ?.contains("${context.packageName}/") == true
    }
}

package app.cleanyourtv.home

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent

/**
 * Home redirect for TVs whose firmware holds the Home key for the stock launcher (the Realtek key
 * service on SCBC boards such as the Nokia ATV R2). The stock launcher stays Android's Home; when one
 * of its home screens comes to the front, this sends the viewer straight on to Clean Home.
 *
 * Same idea as `stock_home_redirect` in github.com/Gathin23/cleanyourtv (MIT). It listens only to
 * window changes of the stock launcher (see res/xml/home_redirect.xml) and filters no keys, so volume
 * and app keys on the remote behave exactly as before.
 *
 * Clean Your TV turns it on with `settings put secure enabled_accessibility_services`, and Undo turns it off.
 */
class HomeRedirectService : AccessibilityService() {

    private var stockHomeActivities: Set<String> = emptySet()
    private var lastOpen = 0L

    override fun onServiceConnected() {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(STOCK_HOME)
        stockHomeActivities = packageManager
            .queryIntentActivities(home, PackageManager.MATCH_DISABLED_COMPONENTS)
            .map { it.activityInfo.name }
            .toSet()
        // At boot the stock home is often already on screen before Android binds this service.
        val front = runCatching { rootInActiveWindow?.packageName?.toString() }.getOrNull()
        if (front == STOCK_HOME) openCleanHome()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (event.packageName?.toString() != STOCK_HOME) return
        val cls = event.className?.toString() ?: return
        // Only the stock home screen itself; its own settings pages (e.g. Customise channels) stay reachable.
        if (stockHomeActivities.isEmpty() || cls in stockHomeActivities) openCleanHome()
    }

    private fun openCleanHome() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastOpen < 700) return
        lastOpen = now
        // Started as an app, not as a Home intent, so the stock launcher stays the home process and the
        // remote's Netflix/YouTube keys keep working (Android 10+ limits background starts otherwise).
        runCatching {
            startActivity(
                Intent(this, HomeActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
            )
        }
    }

    override fun onInterrupt() {}

    companion object {
        const val STOCK_HOME = "com.google.android.tvlauncher"

        fun isEnabled(context: android.content.Context): Boolean =
            android.provider.Settings.Secure.getString(
                context.contentResolver, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            )?.contains("${context.packageName}/") == true
    }
}

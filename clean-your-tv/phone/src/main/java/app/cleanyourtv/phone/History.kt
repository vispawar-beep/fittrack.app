package app.cleanyourtv.phone

import android.content.Context
import org.json.JSONObject

/**
 * Everything Clean Your TV changed on one TV, saved on the phone so it can be
 * undone later, even after the app is closed. Keyed by the TV's serial number.
 */
data class History(
    val disabled: Set<String> = emptySet(),
    /** "namespace:key" -> value before the first change (null = was unset). */
    val savedSettings: Map<String, String?> = emptyMap(),
    val cleanHomeActive: Boolean = false,
) {
    val isEmpty: Boolean get() = disabled.isEmpty() && savedSettings.isEmpty() && !cleanHomeActive
}

class HistoryStore(context: Context) {
    private val prefs = context.getSharedPreferences("history", Context.MODE_PRIVATE)

    fun load(tvId: String): History {
        val raw = prefs.getString(tvId, null) ?: return History()
        return runCatching {
            val o = JSONObject(raw)
            val disabled = o.optJSONArray("disabled")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }.orEmpty()
            val settings = o.optJSONObject("settings")?.let { s ->
                s.keys().asSequence().associateWith { k -> if (s.isNull(k)) null else s.getString(k) }
            }.orEmpty()
            History(disabled, settings, o.optBoolean("cleanHome"))
        }.getOrDefault(History())
    }

    fun save(tvId: String, h: History) {
        val o = JSONObject()
        o.put("disabled", org.json.JSONArray(h.disabled.sorted()))
        val s = JSONObject()
        h.savedSettings.forEach { (k, v) -> s.put(k, v ?: JSONObject.NULL) }
        o.put("settings", s)
        o.put("cleanHome", h.cleanHomeActive)
        prefs.edit().putString(tvId, o.toString()).apply()
    }

    fun lastHost(): String = prefs.getString("_lastHost", "") ?: ""
    fun lastPort(): String = prefs.getString("_lastPort", "5555") ?: "5555"
    fun saveHost(host: String, port: String) = prefs.edit().putString("_lastHost", host).putString("_lastPort", port).apply()
}

package app.cleanyourtv.home

import android.content.SharedPreferences
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.roundToInt

/**
 * Current weather from Open-Meteo (open-meteo.com): free, no account or API key, and no tracking.
 * The TV sends only the city's coordinates. Clean Home's only network use.
 */
data class WeatherNow(
    val place: String,
    val temp: Int,
    val high: Int,
    val low: Int,
    val code: Int,
    val isDay: Boolean,
) {
    val glyph: GlyphKind get() = when (code) {
        0 -> if (isDay) GlyphKind.SUN else GlyphKind.MOON
        1, 2 -> if (isDay) GlyphKind.CLOUD_SUN else GlyphKind.CLOUD_MOON
        3 -> GlyphKind.CLOUD
        45, 48 -> GlyphKind.FOG
        in 51..67, in 80..82 -> GlyphKind.RAIN
        in 71..77, 85, 86 -> GlyphKind.SNOW
        in 95..99 -> GlyphKind.THUNDER
        else -> GlyphKind.CLOUD
    }

    val condition: String get() = when (code) {
        0 -> if (isDay) "Sunny" else "Clear"
        1 -> "Mostly clear"
        2 -> "Partly cloudy"
        3 -> "Cloudy"
        45, 48 -> "Fog"
        in 51..57 -> "Drizzle"
        in 61..67 -> "Rain"
        in 71..77 -> "Snow"
        in 80..82 -> "Showers"
        85, 86 -> "Snow showers"
        in 95..99 -> "Thunderstorm"
        else -> "—"
    }
}

data class Place(val name: String, val lat: Double, val lon: Double)

object Weather {
    private fun get(url: String): JSONObject {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        try {
            return JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        } finally {
            conn.disconnect()
        }
    }

    /** Finds a city by name. Blocking: call off the main thread. */
    fun findPlace(city: String): Place? {
        val q = URLEncoder.encode(city.trim(), "UTF-8")
        val results = get("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=1&language=en&format=json")
            .optJSONArray("results") ?: return null
        if (results.length() == 0) return null
        val r = results.getJSONObject(0)
        return Place(r.getString("name"), r.getDouble("latitude"), r.getDouble("longitude"))
    }

    /** Current conditions and today's high/low. Blocking: call off the main thread. */
    fun fetch(place: Place): WeatherNow {
        val o = get(
            "https://api.open-meteo.com/v1/forecast?latitude=${place.lat}&longitude=${place.lon}" +
                "&current=temperature_2m,weather_code,is_day&daily=temperature_2m_max,temperature_2m_min" +
                "&timezone=auto&forecast_days=1",
        )
        val cur = o.getJSONObject("current")
        val daily = o.getJSONObject("daily")
        return WeatherNow(
            place = place.name,
            temp = cur.getDouble("temperature_2m").roundToInt(),
            high = daily.getJSONArray("temperature_2m_max").getDouble(0).roundToInt(),
            low = daily.getJSONArray("temperature_2m_min").getDouble(0).roundToInt(),
            code = cur.getInt("weather_code"),
            isDay = cur.optInt("is_day", 1) == 1,
        )
    }

    fun savePlace(prefs: SharedPreferences, p: Place) = prefs.edit()
        .putString("w_name", p.name).putString("w_lat", p.lat.toString()).putString("w_lon", p.lon.toString()).apply()

    fun loadPlace(prefs: SharedPreferences): Place? {
        val name = prefs.getString("w_name", null) ?: return null
        val lat = prefs.getString("w_lat", null)?.toDoubleOrNull() ?: return null
        val lon = prefs.getString("w_lon", null)?.toDoubleOrNull() ?: return null
        return Place(name, lat, lon)
    }

    fun saveLast(prefs: SharedPreferences, w: WeatherNow) = prefs.edit()
        .putString("w_last", "${w.temp},${w.high},${w.low},${w.code},${if (w.isDay) 1 else 0}").apply()

    fun loadLast(prefs: SharedPreferences, place: Place): WeatherNow? {
        val p = prefs.getString("w_last", null)?.split(',')?.mapNotNull { it.toIntOrNull() } ?: return null
        if (p.size != 5) return null
        return WeatherNow(place.name, p[0], p[1], p[2], p[3], p[4] == 1)
    }
}

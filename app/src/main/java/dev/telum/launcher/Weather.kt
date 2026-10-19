package dev.telum.launcher

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class WeatherNow(
    val tempC: Double,
    val highC: Double,
    val lowC: Double,
    val code: Int,
    val isDay: Boolean,
) {
    val glyph: String
        get() = when (code) {
            0, 1 -> if (isDay) "sun" else "night"
            2 -> if (isDay) "partly" else "partly-night"
            3 -> "cloud"
            45, 48 -> "fog"
            in 51..67, in 80..82 -> "rain"
            in 71..77, 85, 86 -> "snow"
            in 95..99 -> "storm"
            else -> "cloud"
        }

    val description: String
        get() = when (code) {
            0 -> if (isDay) "Sunny" else "Clear"
            1 -> "Mostly clear"
            2 -> "Partly cloudy"
            3 -> "Cloudy"
            45, 48 -> "Fog"
            51, 53, 55 -> "Drizzle"
            56, 57 -> "Freezing drizzle"
            61, 63 -> "Rain"
            65 -> "Heavy rain"
            66, 67 -> "Freezing rain"
            71, 73 -> "Snow"
            75 -> "Heavy snow"
            77 -> "Snow grains"
            80, 81 -> "Showers"
            82 -> "Heavy showers"
            85, 86 -> "Snow showers"
            95 -> "Thunderstorms"
            96, 99 -> "Thunderstorms and hail"
            else -> "—"
        }

    /** True when the day's high and low are both present (Open-Meteo can send nulls). */
    val hasRange: Boolean get() = highC.isFinite() && lowC.isFinite()

    fun temp(f: Boolean, c: Double = tempC): String {
        // roundToInt() throws on NaN; a missing value must never take the home screen down.
        if (!c.isFinite()) return "–"
        val v = if (f) c * 9.0 / 5.0 + 32.0 else c
        return "${v.roundToInt()}°"
    }
}

/**
 * Current conditions from Open-Meteo (free, no account or key). Only the coordinates of the
 * place the user picked are sent. Fetches at most every 30 minutes, and only while Home is open.
 */
class Weather(private val prefs: Prefs) {
    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "telum-weather").apply { priority = Thread.MIN_PRIORITY } }
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var inFlight = false

    var now: WeatherNow? = parse(prefs.weatherJson)
        private set

    fun refreshIfStale(force: Boolean = false, done: () -> Unit) {
        if (!prefs.weatherOn || inFlight || !prefs.lat.isFinite() || !prefs.lon.isFinite()) return
        val age = System.currentTimeMillis() - prefs.weatherAt
        if (!force && age < 30 * 60_000L && now != null) return
        inFlight = true
        val lat = prefs.lat
        val lon = prefs.lon
        io.execute {
            try {
                val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                    "&current=temperature_2m,weather_code,is_day&daily=temperature_2m_max,temperature_2m_min" +
                    "&forecast_days=1&timezone=auto"
                val body = get(url)
                val parsed = parse(body)
                if (parsed != null) {
                    main.post {
                        prefs.weatherJson = body
                        prefs.weatherAt = System.currentTimeMillis()
                        now = parsed
                        done()
                    }
                }
            } catch (_: Exception) {
                // Offline or blocked: keep the last good reading.
            } finally {
                inFlight = false
            }
        }
    }

    /** Looks up a place name. Calls back on the main thread with (name, lat, lon) or null. */
    fun geocode(query: String, done: (Triple<String, Double, Double>?) -> Unit) {
        io.execute {
            val result = try {
                val q = URLEncoder.encode(query.trim(), "UTF-8")
                val body = get("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=1&language=en&format=json")
                val r = JSONObject(body).optJSONArray("results")?.optJSONObject(0)
                r?.let {
                    val admin = it.optString("admin1", "")
                    val name = it.getString("name") + if (admin.isNotEmpty() && admin != it.getString("name")) ", $admin" else ""
                    Triple(name, it.getDouble("latitude"), it.getDouble("longitude"))
                }
            } catch (_: Exception) { null }
            main.post { done(result) }
        }
    }

    fun clear() { now = null }

    fun close() { io.shutdownNow() }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 6000
        c.readTimeout = 6000
        c.setRequestProperty("User-Agent", "Telum-TV-Launcher")
        try {
            if (c.responseCode != 200) throw IllegalStateException("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    private fun parse(body: String?): WeatherNow? {
        if (body == null) return null
        return try {
            val j = JSONObject(body)
            val cur = j.getJSONObject("current")
            val daily = j.optJSONObject("daily")
            val temp = cur.getDouble("temperature_2m")
            if (!temp.isFinite()) return null
            WeatherNow(
                tempC = temp,
                highC = daily?.optJSONArray("temperature_2m_max")?.optDouble(0) ?: Double.NaN,
                lowC = daily?.optJSONArray("temperature_2m_min")?.optDouble(0) ?: Double.NaN,
                code = cur.getInt("weather_code"),
                isDay = cur.optInt("is_day", 1) == 1,
            )
        } catch (_: Exception) { null }
    }
}

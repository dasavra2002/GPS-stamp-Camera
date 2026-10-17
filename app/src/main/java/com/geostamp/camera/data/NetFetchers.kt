package com.geostamp.camera.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

private const val USER_AGENT = "GeoStampCamera/1.0 (Android)"

private fun httpGet(url: String): ByteArray? {
    var conn: HttpURLConnection? = null
    return try {
        conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("User-Agent", USER_AGENT)
        }
        if (conn.responseCode in 200..299) conn.inputStream.use { it.readBytes() } else null
    } catch (e: Exception) {
        null
    } finally {
        conn?.disconnect()
    }
}

data class Weather(
    val temperatureC: Double?,
    val humidity: Int?,
    val windKmh: Double?,
    val pressureHpa: Double?,
)

/** Current weather from Open-Meteo (free, no API key). */
object WeatherFetcher {
    fun fetch(lat: Double, lon: Double): Weather? {
        val url = String.format(
            Locale.US,
            "https://api.open-meteo.com/v1/forecast?latitude=%.5f&longitude=%.5f" +
                "&current=temperature_2m,relative_humidity_2m,wind_speed_10m,surface_pressure" +
                "&wind_speed_unit=kmh",
            lat, lon,
        )
        val body = httpGet(url) ?: return null
        return try {
            val cur = JSONObject(String(body)).getJSONObject("current")
            Weather(
                temperatureC = cur.optDoubleOrNull("temperature_2m"),
                humidity = cur.optDoubleOrNull("relative_humidity_2m")?.toInt(),
                windKmh = cur.optDoubleOrNull("wind_speed_10m"),
                pressureHpa = cur.optDoubleOrNull("surface_pressure"),
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Weather at a past moment (for gallery photos). Uses the recent-forecast API for the
     * last ~3 months and the historical archive for anything older.
     */
    fun fetchAt(lat: Double, lon: Double, utc: java.time.ZonedDateTime): Weather? {
        val t = utc.withZoneSameInstant(java.time.ZoneOffset.UTC)
        val day = t.toLocalDate().toString()
        val daysAgo = java.time.temporal.ChronoUnit.DAYS.between(t.toLocalDate(), java.time.LocalDate.now(java.time.ZoneOffset.UTC))
        val hourly = "&hourly=temperature_2m,relative_humidity_2m,wind_speed_10m,surface_pressure" +
            "&wind_speed_unit=kmh&timezone=GMT&start_date=$day&end_date=$day"
        val coords = String.format(Locale.US, "latitude=%.5f&longitude=%.5f", lat, lon)
        val hosts = if (daysAgo <= 80) {
            listOf("https://api.open-meteo.com/v1/forecast?", "https://archive-api.open-meteo.com/v1/archive?")
        } else {
            listOf("https://archive-api.open-meteo.com/v1/archive?")
        }
        val wantedTime = String.format(Locale.US, "%sT%02d:00", day, t.hour)
        for (host in hosts) {
            val body = httpGet(host + coords + hourly) ?: continue
            try {
                val h = JSONObject(String(body)).getJSONObject("hourly")
                val times = h.getJSONArray("time")
                var idx = -1
                for (i in 0 until times.length()) if (times.getString(i) == wantedTime) { idx = i; break }
                if (idx < 0) continue
                fun value(key: String): Double? {
                    val arr = h.optJSONArray(key) ?: return null
                    return if (arr.isNull(idx)) null else arr.optDouble(idx).takeIf { !it.isNaN() }
                }
                val w = Weather(
                    temperatureC = value("temperature_2m"),
                    humidity = value("relative_humidity_2m")?.toInt(),
                    windKmh = value("wind_speed_10m"),
                    pressureHpa = value("surface_pressure"),
                )
                if (w.temperatureC != null) return w
            } catch (e: Exception) {
                // try next source
            }
        }
        return null
    }

    private fun JSONObject.optDoubleOrNull(key: String): Double? =
        if (has(key) && !isNull(key)) optDouble(key).takeIf { !it.isNaN() } else null
}

/**
 * Builds a square map image centred on a point by stitching free map tiles.
 * Satellite / Terrain: Esri. Street: OpenStreetMap.
 */
object MapTileFetcher {
    private const val TILE = 256

    fun fetch(lat: Double, lon: Double, type: MapType, zoom: Int = 17, sizePx: Int = 512): Bitmap? {
        val n = 1 shl zoom
        val latRad = lat * PI / 180.0
        val worldX = (lon + 180.0) / 360.0 * n * TILE
        val worldY = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n * TILE

        val left = worldX - sizePx / 2.0
        val top = worldY - sizePx / 2.0
        val out = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.rgb(60, 60, 60))

        var gotAny = false
        val tx0 = floor(left / TILE).toInt()
        val tx1 = floor((left + sizePx) / TILE).toInt()
        val ty0 = floor(top / TILE).toInt()
        val ty1 = floor((top + sizePx) / TILE).toInt()
        for (ty in ty0..ty1) {
            if (ty < 0 || ty >= n) continue
            for (tx in tx0..tx1) {
                val wrappedX = ((tx % n) + n) % n
                val url = when (type) {
                    MapType.SATELLITE ->
                        "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/$zoom/$ty/$wrappedX"
                    MapType.TERRAIN ->
                        "https://server.arcgisonline.com/ArcGIS/rest/services/World_Topo_Map/MapServer/tile/$zoom/$ty/$wrappedX"
                    MapType.STREET ->
                        "https://tile.openstreetmap.org/$zoom/$wrappedX/$ty.png"
                }
                val bytes = httpGet(url) ?: continue
                val tile = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: continue
                canvas.drawBitmap(
                    tile,
                    (tx * TILE - left).toFloat(),
                    (ty * TILE - top).toFloat(),
                    null,
                )
                tile.recycle()
                gotAny = true
            }
        }
        return if (gotAny) out else null
    }
}

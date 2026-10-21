package com.geostamp.camera.data

import android.content.Context
import android.content.SharedPreferences
import android.location.Location
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class GeofenceSite(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float = 250f,
    val contractor: String = "",
    val client: String = "",
    val note: String = "",
    val autoApply: Boolean = true,
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("name", name)
            put("latitude", latitude)
            put("longitude", longitude)
            put("radiusMeters", radiusMeters.toDouble())
            put("contractor", contractor)
            put("client", client)
            put("note", note)
            put("autoApply", autoApply)
        }
    }

    companion object {
        fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val r = 6371000.0 // Earth radius in meters
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                    Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                    Math.sin(dLon / 2) * Math.sin(dLon / 2)
            val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
            return r * c
        }

        fun fromJson(json: JSONObject): GeofenceSite {
            return GeofenceSite(
                id = json.optString("id", UUID.randomUUID().toString()),
                name = json.optString("name", "Unnamed Site"),
                latitude = json.optDouble("latitude", 0.0),
                longitude = json.optDouble("longitude", 0.0),
                radiusMeters = json.optDouble("radiusMeters", 250.0).toFloat(),
                contractor = json.optString("contractor", ""),
                client = json.optString("client", ""),
                note = json.optString("note", ""),
                autoApply = json.optBoolean("autoApply", true),
            )
        }
    }
}

class GeofenceManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("geostamp_geofences", Context.MODE_PRIVATE)

    val sites = mutableStateListOf<GeofenceSite>()
    val autoSwitchEnabled = mutableStateOf(prefs.getBoolean("auto_switch_enabled", true))

    init {
        loadSites()
    }

    private fun loadSites() {
        sites.clear()
        val raw = prefs.getString("sites_json", null)
        if (!raw.isNullOrBlank()) {
            try {
                val array = JSONArray(raw)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    sites.add(GeofenceSite.fromJson(obj))
                }
            } catch (_: Exception) {}
        }
    }

    fun saveSites() {
        val array = JSONArray()
        for (site in sites) {
            array.put(site.toJson())
        }
        prefs.edit().putString("sites_json", array.toString()).apply()
    }

    fun addSite(site: GeofenceSite) {
        sites.add(site)
        saveSites()
    }

    fun updateSite(site: GeofenceSite) {
        val idx = sites.indexOfFirst { it.id == site.id }
        if (idx != -1) {
            sites[idx] = site
            saveSites()
        }
    }

    fun removeSite(id: String) {
        sites.removeAll { it.id == id }
        saveSites()
    }

    fun setAutoSwitch(enabled: Boolean) {
        autoSwitchEnabled.value = enabled
        prefs.edit().putBoolean("auto_switch_enabled", enabled).apply()
    }

    /**
     * Checks all configured geofence sites against the current coordinates.
     * Returns the matching site and the distance in meters, or null if outside all geofences.
     */
    fun findActiveSite(currentLat: Double?, currentLon: Double?): Pair<GeofenceSite, Float>? {
        if (currentLat == null || currentLon == null || sites.isEmpty()) return null

        var closestMatch: Pair<GeofenceSite, Float>? = null
        val results = FloatArray(1)

        for (site in sites) {
            Location.distanceBetween(currentLat, currentLon, site.latitude, site.longitude, results)
            val dist = results[0]
            if (dist <= site.radiusMeters) {
                if (closestMatch == null || dist < closestMatch.second) {
                    closestMatch = Pair(site, dist)
                }
            }
        }
        return closestMatch
    }

    /**
     * Finds the nearest site regardless of whether the user is inside or outside.
     */
    fun findNearestSite(currentLat: Double?, currentLon: Double?): Pair<GeofenceSite, Float>? {
        if (currentLat == null || currentLon == null || sites.isEmpty()) return null

        var nearest: Pair<GeofenceSite, Float>? = null
        val results = FloatArray(1)

        for (site in sites) {
            Location.distanceBetween(currentLat, currentLon, site.latitude, site.longitude, results)
            val dist = results[0]
            if (nearest == null || dist < nearest.second) {
                nearest = Pair(site, dist)
            }
        }
        return nearest
    }
}

package com.geostamp.camera.data

import android.content.Context
import android.location.Geocoder
import java.util.Locale

data class GeoResult(
    val title: String?,
    val shortAddress: String?,
    val address: String?,
    val countryCode: String?,
)

object Geo {
    /** Reverse-geocodes a point. Blocking: call from a background thread. */
    @Suppress("DEPRECATION")
    fun reverse(context: Context, lat: Double, lon: Double): GeoResult? {
        if (!Geocoder.isPresent()) return null
        return try {
            val a = Geocoder(context, Locale.getDefault()).getFromLocation(lat, lon, 1)?.firstOrNull()
                ?: return null
            val title = listOfNotNull(a.locality ?: a.subAdminArea, a.adminArea, a.countryName)
                .distinct().joinToString(", ")
            val short = listOfNotNull(a.thoroughfare, a.subLocality, a.locality ?: a.subAdminArea)
                .distinct().joinToString(", ")
            val full = if (a.maxAddressLineIndex >= 0) {
                (0..a.maxAddressLineIndex).joinToString(", ") { a.getAddressLine(it) }
            } else {
                listOfNotNull(a.thoroughfare, a.subLocality, a.locality, a.adminArea, a.postalCode, a.countryName)
                    .joinToString(", ")
            }
            GeoResult(
                title = title.ifBlank { null },
                shortAddress = short.ifBlank { null },
                address = full.ifBlank { null },
                countryCode = a.countryCode,
            )
        } catch (e: Exception) {
            null
        }
    }
}

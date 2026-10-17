package com.geostamp.camera.stamp

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import com.geostamp.camera.data.Geo
import com.geostamp.camera.data.MapTileFetcher
import com.geostamp.camera.data.MapType
import com.geostamp.camera.data.StampData
import com.geostamp.camera.data.WeatherFetcher
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What could be read from a gallery photo's own metadata. */
data class PhotoMeta(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitude: Double? = null,
    val time: ZonedDateTime? = null,
    val timeFromExif: Boolean = false,
    val direction: Float? = null,
    val camera: String? = null,
) {
    val hasLocation get() = latitude != null && longitude != null
}

object GalleryStamper {

    /**
     * Android hides GPS data from apps unless they ask for the original file.
     * Tries to get that original; falls back to the uri as given.
     */
    fun originalUri(context: Context, uri: Uri): Uri {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return uri
        return try {
            val media = if (uri.authority == MediaStore.AUTHORITY) uri else MediaStore.getMediaUri(context, uri)
            if (media == null) return uri
            val original = MediaStore.setRequireOriginal(media)
            context.contentResolver.openInputStream(original)?.close()
            original
        } catch (e: Exception) {
            uri
        }
    }

    fun displayName(context: Context, uri: Uri): String = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    } catch (e: Exception) {
        null
    } ?: uri.lastPathSegment ?: "photo"

    fun readMeta(context: Context, uri: Uri): PhotoMeta {
        val exif = try {
            context.contentResolver.openInputStream(uri)?.use { ExifInterface(it) }
        } catch (e: Exception) {
            null
        }

        var lat: Double? = null
        var lon: Double? = null
        var alt: Double? = null
        var dir: Float? = null
        var time: ZonedDateTime? = null
        var camera: String? = null

        if (exif != null) {
            exif.latLong?.let { ll ->
                if (!(ll[0] == 0.0 && ll[1] == 0.0)) { lat = ll[0]; lon = ll[1] }
            }
            if (exif.getAttribute(ExifInterface.TAG_GPS_ALTITUDE) != null) alt = exif.getAltitude(0.0)
            val d = exif.getAttributeDouble(ExifInterface.TAG_GPS_IMG_DIRECTION, Double.NaN)
            if (!d.isNaN()) dir = d.toFloat()
            camera = listOfNotNull(exif.getAttribute(ExifInterface.TAG_MAKE), exif.getAttribute(ExifInterface.TAG_MODEL))
                .joinToString(" ").ifBlank { null }

            val raw = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
            val offset = exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL)
                ?: exif.getAttribute(ExifInterface.TAG_OFFSET_TIME)
            if (raw != null) {
                try {
                    val local = LocalDateTime.parse(raw.trim(), DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss", Locale.US))
                    val zone: ZoneId = offset?.let { runCatching { ZoneOffset.of(it.trim()) }.getOrNull() } ?: ZoneId.systemDefault()
                    time = local.atZone(zone)
                } catch (e: Exception) {
                    // unparseable date, fall through
                }
            }
        }
        val fromExif = time != null
        if (time == null) time = fileTime(context, uri)

        return PhotoMeta(lat, lon, alt, time, fromExif, dir, camera)
    }

    /** Date the photo was taken according to the gallery, or the file's modified date. */
    private fun fileTime(context: Context, uri: Uri): ZonedDateTime? {
        for (column in listOf(MediaStore.Images.Media.DATE_TAKEN, "last_modified")) {
            try {
                context.contentResolver.query(uri, arrayOf(column), null, null, null)?.use {
                    if (it.moveToFirst() && !it.isNull(0)) {
                        val ms = it.getLong(0)
                        if (ms > 0) return Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
                    }
                }
            } catch (e: Exception) {
                // column not supported by this provider
            }
        }
        return null
    }

    /**
     * Looks up address, Plus Code, historical weather and map for a location and time.
     * Keeps any value already in [base] that the lookup can't provide (e.g. compass).
     * Blocking network calls: run on a background thread.
     */
    fun lookup(
        context: Context,
        lat: Double,
        lon: Double,
        time: ZonedDateTime,
        mapType: MapType,
        base: StampData,
    ): StampData {
        val geo = Geo.reverse(context, lat, lon)
        val weather = WeatherFetcher.fetchAt(lat, lon, time)
        val map = MapTileFetcher.fetch(lat, lon, mapType)
        return base.copy(
            latitude = lat,
            longitude = lon,
            plusCode = PlusCode.encode(lat, lon),
            title = geo?.title ?: base.title,
            shortAddress = geo?.shortAddress ?: base.shortAddress,
            address = geo?.address ?: base.address,
            countryCode = geo?.countryCode ?: base.countryCode,
            temperatureC = weather?.temperatureC ?: base.temperatureC,
            humidity = weather?.humidity ?: base.humidity,
            windKmh = weather?.windKmh ?: base.windKmh,
            pressureHpa = weather?.pressureHpa ?: base.pressureHpa,
            mapBitmap = map ?: base.mapBitmap,
            mapType = if (map != null) mapType else base.mapType,
        )
    }

    /** Finds coordinates for a typed place name or address. */
    @Suppress("DEPRECATION")
    fun searchPlace(context: Context, query: String): Pair<Double, Double>? = try {
        android.location.Geocoder(context, Locale.getDefault()).getFromLocationName(query, 1)
            ?.firstOrNull()?.let { it.latitude to it.longitude }
    } catch (e: Exception) {
        null
    }
}

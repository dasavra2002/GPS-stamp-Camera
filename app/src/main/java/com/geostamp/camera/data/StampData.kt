package com.geostamp.camera.data

import android.graphics.Bitmap

/** Live values that go into the watermark. Anything unknown stays null and is shown as "-". */
data class StampData(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitude: Double? = null,
    val accuracyM: Float? = null,
    val title: String? = null,
    val countryCode: String? = null,
    val shortAddress: String? = null,
    val address: String? = null,
    val plusCode: String? = null,
    val temperatureC: Double? = null,
    val humidity: Int? = null,
    val windKmh: Double? = null,
    val pressureHpa: Double? = null,
    val azimuthDeg: Float? = null,
    val magneticUt: Float? = null,
    val soundDb: Float? = null,
    val mapBitmap: Bitmap? = null,
    val mapType: MapType = MapType.SATELLITE,
)

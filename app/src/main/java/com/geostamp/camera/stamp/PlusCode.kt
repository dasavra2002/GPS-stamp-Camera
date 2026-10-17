package com.geostamp.camera.stamp

import kotlin.math.floor

/** Open Location Code ("Plus Code") encoder, 10-digit precision (about 14 x 14 m). */
object PlusCode {
    private const val ALPHABET = "23456789CFGHJMPQRVWX"
    private const val FINAL_RES = 8000.0 // 1 / 0.000125 degrees

    fun encode(latitude: Double, longitude: Double): String {
        var lat = latitude.coerceIn(-90.0, 90.0)
        if (lat >= 90.0) lat = 90.0 - 1e-9
        var lng = longitude
        while (lng < -180.0) lng += 360.0
        while (lng >= 180.0) lng -= 360.0

        var la = floor((lat + 90.0) * FINAL_RES).toLong()
        var ln = floor((lng + 180.0) * FINAL_RES).toLong()
        val chars = CharArray(10)
        for (i in 4 downTo 0) {
            chars[i * 2] = ALPHABET[(la % 20).toInt()]
            chars[i * 2 + 1] = ALPHABET[(ln % 20).toInt()]
            la /= 20
            ln /= 20
        }
        val code = String(chars)
        return code.substring(0, 8) + "+" + code.substring(8)
    }
}

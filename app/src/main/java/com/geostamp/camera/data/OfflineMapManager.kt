package com.geostamp.camera.data

import android.content.Context
import kotlinx.coroutines.delay
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

object OfflineMapManager {
    private const val TILE = 256
    private const val MAX_LAT = 85.05112878

    data class DownloadResult(
        val totalTiles: Int,
        val downloadedTiles: Int,
        val alreadyCachedTiles: Int,
        val failedTiles: Int,
    )

    fun calculateTilesForArea(
        centerLat: Double,
        centerLon: Double,
        radiusKm: Double,
        zooms: List<Int> = listOf(14, 15, 16, 17),
    ): List<TileCoord> {
        val coords = mutableListOf<TileCoord>()
        // 1 deg latitude is approx 111 km
        val deltaLat = radiusKm / 111.0
        val clampedCenterLat = centerLat.coerceIn(-MAX_LAT, MAX_LAT)
        val latCos = cos(clampedCenterLat * PI / 180.0).coerceAtLeast(0.01)
        val deltaLon = radiusKm / (111.0 * latCos)

        val minLat = (centerLat - deltaLat).coerceIn(-MAX_LAT, MAX_LAT)
        val maxLat = (centerLat + deltaLat).coerceIn(-MAX_LAT, MAX_LAT)
        val minLon = centerLon - deltaLon
        val maxLon = centerLon + deltaLon

        for (z in zooms) {
            val n = 1 shl z
            val (minX, maxY) = latLonToTile(minLat, minLon, z)
            val (maxX, minY) = latLonToTile(maxLat, maxLon, z)

            val x0 = minOf(minX, maxX)
            val x1 = maxOf(minX, maxX)
            val y0 = minOf(minY, maxY).coerceIn(0, n - 1)
            val y1 = maxOf(minY, maxY).coerceIn(0, n - 1)

            for (ty in y0..y1) {
                for (tx in x0..x1) {
                    val wrappedX = ((tx % n) + n) % n
                    coords.add(TileCoord(z, wrappedX, ty))
                }
            }
        }
        return coords.distinct()
    }

    /** Largest pack allowed in one go, to stay polite even to servers that allow offline use. */
    const val MAX_PACK_TILES = 5_000

    /** Tiles live in cacheDir/map_tiles/<source id>/<zoom>/<y>_<x>.png */
    fun cacheDir(context: Context, source: TileSource): File = File(context.cacheDir, "map_tiles/${source.id}")

    /**
     * Downloads every tile of an area for offline use. Only runs against a tile source whose
     * terms allow bulk downloading (a custom server); the free public servers forbid it.
     */
    suspend fun downloadAreaPack(
        context: Context,
        centerLat: Double,
        centerLon: Double,
        radiusKm: Double,
        source: TileSource,
        zooms: List<Int> = listOf(14, 15, 16, 17),
        onProgress: ((current: Int, total: Int) -> Unit)? = null,
    ): DownloadResult {
        require(source.bulkDownloadAllowed) { "${source.id} does not allow bulk downloads" }
        val tiles = calculateTilesForArea(centerLat, centerLon, radiusKm, zooms)
        require(tiles.size <= MAX_PACK_TILES) { "Area too large: ${tiles.size} tiles" }
        var downloaded = 0
        var alreadyCached = 0
        var failed = 0

        tiles.forEachIndexed { index, t ->
            val cacheFile = File(cacheDir(context, source), "${t.zoom}/${t.y}_${t.x}.png")

            if (cacheFile.exists() && cacheFile.length() > 0) {
                alreadyCached++
            } else {
                val bytes = httpGet(source.url(t.zoom, t.x, t.y))
                if (bytes != null && bytes.isNotEmpty()) {
                    runCatching {
                        cacheFile.parentFile?.mkdirs()
                        cacheFile.writeBytes(bytes)
                        downloaded++
                    }.onFailure { failed++ }
                } else {
                    failed++
                }
                // One request at a time with a short pause, as tile providers ask.
                delay(50)
            }
            onProgress?.invoke(index + 1, tiles.size)
        }

        return DownloadResult(
            totalTiles = tiles.size,
            downloadedTiles = downloaded,
            alreadyCachedTiles = alreadyCached,
            failedTiles = failed,
        )
    }

    fun getCacheInfo(context: Context): CacheInfo {
        val root = File(context.cacheDir, "map_tiles")
        if (!root.exists()) return CacheInfo(0, 0L)
        var count = 0
        var sizeBytes = 0L
        root.walkTopDown().filter { it.isFile && it.name.endsWith(".png") }.forEach {
            count++
            sizeBytes += it.length()
        }
        return CacheInfo(count, sizeBytes)
    }

    fun clearCache(context: Context): Boolean {
        val root = File(context.cacheDir, "map_tiles")
        return if (root.exists()) {
            root.deleteRecursively()
        } else true
    }

    private fun latLonToTile(lat: Double, lon: Double, zoom: Int): Pair<Int, Int> {
        val n = 1 shl zoom
        val latRad = lat.coerceIn(-MAX_LAT, MAX_LAT) * PI / 180.0
        val x = floor((lon + 180.0) / 360.0 * n).toInt()
        val y = floor((1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n).toInt()
        return x to y
    }

    data class TileCoord(val zoom: Int, val x: Int, val y: Int)
    data class CacheInfo(val fileCount: Int, val totalBytes: Long) {
        val mb: Double get() = totalBytes / (1024.0 * 1024.0)
    }
}

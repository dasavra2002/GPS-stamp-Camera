package com.geostamp.camera

import com.geostamp.camera.data.OfflineMapManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineMapManagerTest {

    @Test
    fun testCalculateTilesForArea() {
        val lat = 28.6139 // New Delhi
        val lon = 77.2090
        val tiles = OfflineMapManager.calculateTilesForArea(lat, lon, radiusKm = 1.0, zooms = listOf(15))

        assertTrue(tiles.isNotEmpty())
        for (t in tiles) {
            assertEquals(15, t.zoom)
            assertTrue(t.x in 0 until (1 shl 15))
            assertTrue(t.y in 0 until (1 shl 15))
        }
    }

    @Test
    fun testCacheInfoMb() {
        val info = OfflineMapManager.CacheInfo(fileCount = 10, totalBytes = 10 * 1024 * 1024L)
        assertEquals(10.0, info.mb, 0.01)
        assertEquals(10, info.fileCount)
    }

    @Test
    fun testTileCoordinateWrapping() {
        val tiles = OfflineMapManager.calculateTilesForArea(0.0, 179.99, radiusKm = 20.0, zooms = listOf(10))
        for (t in tiles) {
            assertTrue(t.x >= 0)
            assertTrue(t.x < (1 shl 10))
        }
    }
}

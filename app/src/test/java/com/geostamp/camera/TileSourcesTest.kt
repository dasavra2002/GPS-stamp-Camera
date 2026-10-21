package com.geostamp.camera

import com.geostamp.camera.data.MapType
import com.geostamp.camera.data.TileSources
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TileSourcesTest {

    @After
    fun reset() = TileSources.configureCustom("", "")

    @Test
    fun publicServersDoNotAllowBulkDownload() {
        TileSources.configureCustom("", "")
        for (type in MapType.entries) {
            assertFalse(type.name, TileSources.forType(type).bulkDownloadAllowed)
        }
    }

    @Test
    fun builtInUrlsAndAttribution() {
        assertEquals("https://tile.openstreetmap.org/16/100/200.png", TileSources.OSM.url(16, 100, 200))
        assertEquals(
            "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/16/200/100",
            TileSources.ESRI_IMAGERY.url(16, 100, 200),
        )
        assertTrue(TileSources.OSM.attribution.contains("OpenStreetMap contributors"))
    }

    @Test
    fun customServerIsUsedForEveryTypeAndAllowsPacks() {
        TileSources.configureCustom("https://api.maptiler.com/maps/streets/{z}/{x}/{y}.png?key=abc", "© MapTiler © OpenStreetMap contributors")
        for (type in MapType.entries) {
            val s = TileSources.forType(type)
            assertTrue(s.bulkDownloadAllowed)
            assertEquals("https://api.maptiler.com/maps/streets/3/4/5.png?key=abc", s.url(3, 4, 5))
            assertEquals("© MapTiler © OpenStreetMap contributors", s.attribution)
        }
    }

    @Test
    fun invalidTemplatesAreIgnored() {
        TileSources.configureCustom("http://example.com/{z}/{x}/{y}.png", "x")
        assertNull(TileSources.custom)
        TileSources.configureCustom("https://example.com/{z}/{x}.png", "x")
        assertNull(TileSources.custom)
    }

    @Test
    fun customSourcesGetSeparateCaches() {
        TileSources.configureCustom("https://a.example/{z}/{x}/{y}.png", "")
        val a = TileSources.custom!!.id
        TileSources.configureCustom("https://b.example/{z}/{x}/{y}.png", "")
        assertFalse(a == TileSources.custom!!.id)
        assertTrue(TileSources.custom!!.attribution.isNotBlank())
    }
}

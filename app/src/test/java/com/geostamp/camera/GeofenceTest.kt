package com.geostamp.camera

import com.geostamp.camera.data.GeofenceSite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeofenceTest {

    @Test
    fun testGeofenceJsonSerialization() {
        val site = GeofenceSite(
            id = "site-101",
            name = "Connaught Place Metro Station",
            latitude = 28.6315,
            longitude = 77.2167,
            radiusMeters = 300f,
            contractor = "L&T Construction",
            client = "DMRC",
            note = "Shaft excavation stage 2",
            autoApply = true
        )

        val json = site.toJson()
        val restored = GeofenceSite.fromJson(json)

        assertEquals("site-101", restored.id)
        assertEquals("Connaught Place Metro Station", restored.name)
        assertEquals(28.6315, restored.latitude, 0.0001)
        assertEquals(77.2167, restored.longitude, 0.0001)
        assertEquals(300f, restored.radiusMeters, 0.1f)
        assertEquals("L&T Construction", restored.contractor)
        assertEquals("DMRC", restored.client)
        assertEquals("Shaft excavation stage 2", restored.note)
        assertTrue(restored.autoApply)
    }

    @Test
    fun testHaversineDistanceInsideGeofence() {
        val siteLat = 28.6139
        val siteLon = 77.2090
        val siteRadius = 250f

        // Point ~100m away
        val currentLat = 28.6145
        val currentLon = 77.2095
        val dist = GeofenceSite.distanceMeters(currentLat, currentLon, siteLat, siteLon)

        assertTrue("Distance should be around 80-120m, got: $dist", dist in 50.0..150.0)
        assertTrue("User should be inside 250m geofence", dist <= siteRadius)
    }

    @Test
    fun testHaversineDistanceOutsideGeofence() {
        val siteLat = 28.6139
        val siteLon = 77.2090
        val siteRadius = 250f

        // Point ~2.5km away
        val currentLat = 28.6350
        val currentLon = 77.2150
        val dist = GeofenceSite.distanceMeters(currentLat, currentLon, siteLat, siteLon)

        assertTrue("Distance should be > 2000m, got: $dist", dist > 2000.0)
        assertFalse("User should be outside 250m geofence", dist <= siteRadius)
    }
}

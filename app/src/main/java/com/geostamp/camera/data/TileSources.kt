package com.geostamp.camera.data

import java.util.Locale

/**
 * A map tile server and the terms that come with it.
 *
 * The free public servers (OpenStreetMap, Esri) allow fetching the tiles a user is
 * looking at, with attribution, but forbid bulk downloading for offline use
 * (https://operations.osmfoundation.org/policies/tiles/). Offline packs therefore
 * need a provider whose plan allows it, set up as a custom tile server.
 */
data class TileSource(
    val id: String,
    /** URL with {z}, {x} and {y} placeholders. */
    val urlTemplate: String,
    val attribution: String,
    val bulkDownloadAllowed: Boolean,
) {
    fun url(z: Int, x: Int, y: Int): String =
        urlTemplate.replace("{z}", z.toString()).replace("{x}", x.toString()).replace("{y}", y.toString())
}

object TileSources {
    val ESRI_IMAGERY = TileSource(
        "esri_imagery",
        "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}",
        "© Esri, Maxar, Earthstar Geographics",
        bulkDownloadAllowed = false,
    )
    val ESRI_TOPO = TileSource(
        "esri_topo",
        "https://server.arcgisonline.com/ArcGIS/rest/services/World_Topo_Map/MapServer/tile/{z}/{y}/{x}",
        "© Esri",
        bulkDownloadAllowed = false,
    )
    val OSM = TileSource(
        "osm",
        "https://tile.openstreetmap.org/{z}/{x}/{y}.png",
        "© OpenStreetMap contributors",
        bulkDownloadAllowed = false,
    )

    /** User's own tile server (MapTiler, Thunderforest, Stadia, self-hosted...), used for every map type. */
    @Volatile var custom: TileSource? = null
        private set

    fun configureCustom(urlTemplate: String, attribution: String) {
        custom = if (isValidTemplate(urlTemplate)) {
            val t = urlTemplate.trim()
            TileSource(
                id = "custom_" + Integer.toHexString(t.hashCode()),
                urlTemplate = t,
                attribution = attribution.trim().ifEmpty { "Map data © provider" },
                // The user vouches that their plan allows offline use when they set this up.
                bulkDownloadAllowed = true,
            )
        } else {
            null
        }
    }

    fun isValidTemplate(t: String): Boolean {
        val s = t.trim()
        return s.lowercase(Locale.US).startsWith("https://") && "{z}" in s && "{x}" in s && "{y}" in s
    }

    fun forType(type: MapType): TileSource = custom ?: when (type) {
        MapType.SATELLITE -> ESRI_IMAGERY
        MapType.TERRAIN -> ESRI_TOPO
        MapType.STREET -> OSM
    }
}

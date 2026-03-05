package com.aiinpm.geospatialcommandcenter.model

import kotlin.math.*

/**
 * WGS-84 coordinate with optional accuracy radius.
 * Resolution throttling: defaults to city-level (~0.01° ≈ 1 km).
 */
data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Double? = null,
    val resolutionLevel: ResolutionLevel = ResolutionLevel.CITY
) {
    enum class ResolutionLevel {
        COUNTRY,   // ±100 km
        PROVINCE,  // ±50 km
        CITY,      // ±1 km (default)
        PRECISE    // sub-km — only when justified by humanitarian need + multiple credible sources
    }

    /** Haversine distance in kilometers. */
    fun distanceTo(other: GeoPoint): Double {
        val r = 6371.0
        val dLat = Math.toRadians(other.latitude - latitude)
        val dLon = Math.toRadians(other.longitude - longitude)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(latitude)) *
                cos(Math.toRadians(other.latitude)) *
                sin(dLon / 2).pow(2)
        return 2 * r * asin(sqrt(a))
    }

    /** Throttle to requested resolution by rounding coordinates. */
    fun throttle(level: ResolutionLevel = ResolutionLevel.CITY): GeoPoint {
        val decimals = when (level) {
            ResolutionLevel.COUNTRY  -> 0
            ResolutionLevel.PROVINCE -> 1
            ResolutionLevel.CITY     -> 2
            ResolutionLevel.PRECISE  -> 4
        }
        val factor = 10.0.pow(decimals)
        return copy(
            latitude = (latitude * factor).roundToInt() / factor,
            longitude = (longitude * factor).roundToInt() / factor,
            resolutionLevel = level
        )
    }

    private fun Double.roundToInt(): Double = kotlin.math.round(this)

    /** Check if point falls within Iran's approximate bounding box. */
    fun isWithinIran(): Boolean =
        latitude in 25.0..40.0 && longitude in 44.0..64.0
}


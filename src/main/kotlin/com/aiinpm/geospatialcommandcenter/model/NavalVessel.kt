package com.aiinpm.geospatialcommandcenter.model

import java.time.Instant

/**
 * Live or OSINT-derived naval vessel track.
 *
 * Data sources:
 *   - AIS_LIVE         : live AIS broadcast received via aggregator API
 *   - OSINT_SYNTHETIC  : position estimated from open-source intelligence
 *                        (press releases, ship-tracker sites, NAVCENT statements)
 *
 * MMSI follows ITU-R M.585: USA vessels begin with 338.
 * Allied vessels carry their national prefix.
 */
data class NavalVessel(
    /** Maritime Mobile Service Identity (9-digit unique vessel identifier). */
    val mmsi: String,

    /** Official ship name (e.g. "USS Dwight D. Eisenhower"). */
    val name: String,

    /** Hull classification symbol (e.g. "CVN-69", "DDG-97"). */
    val hullNumber: String,

    /** Platform class / ship type. */
    val vesselClass: NavalVesselClass,

    /** Flag state (e.g. "United States", "United Kingdom"). */
    val flag: String,

    /** WGS-84 latitude (decimal degrees, city-level resolution). */
    val latitude: Double,

    /** WGS-84 longitude (decimal degrees, city-level resolution). */
    val longitude: Double,

    /** Speed over ground in knots (1 knot = 1.852 km/h). */
    val speedKnots: Double,

    /** Course over ground in true degrees (0–360). */
    val courseTrue: Double,

    /** True heading in degrees (0–360), null if not broadcast. */
    val heading: Double?,

    /** Navigational status: "Underway", "Anchored", "Moored". */
    val navStatus: String,

    /** Named operational area (e.g. "Persian Gulf", "Red Sea"). */
    val operationalArea: String,

    /** Operational alert status for this track. */
    val alertLevel: NavalAlertLevel,

    /** Human-readable reason for non-ROUTINE alert, if any. */
    val alertReason: String?,

    /** Data provenance: "AIS_LIVE" or "OSINT_SYNTHETIC". */
    val dataSource: String,

    /** Timestamp of the most recent position fix. */
    val updatedAt: Instant = Instant.now()
) {
    /** Speed in km/h derived from knots (display convenience). */
    val speedKmh: Double get() = speedKnots * 1.852

    /** Emoji icon for this vessel class (used by front-end rendering). */
    val classIcon: String get() = when (vesselClass) {
        NavalVesselClass.AIRCRAFT_CARRIER         -> "🛥"
        NavalVesselClass.GUIDED_MISSILE_DESTROYER -> "⚓"
        NavalVesselClass.GUIDED_MISSILE_CRUISER   -> "⚓"
        NavalVesselClass.AMPHIBIOUS_ASSAULT       -> "🚢"
        NavalVesselClass.AMPHIBIOUS_TRANSPORT     -> "🚢"
        NavalVesselClass.FAST_ATTACK_SUB          -> "🔱"
        NavalVesselClass.LOGISTICS_SUPPORT        -> "🛳"
        NavalVesselClass.MINE_COUNTERMEASURES     -> "⚓"
        NavalVesselClass.ALLIED_SURFACE           -> "⚓"
        NavalVesselClass.UNKNOWN                  -> "❓"
    }
}


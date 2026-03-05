package com.aiinpm.geospatialcommandcenter.model

import java.time.Instant
import java.util.UUID

/**
 * A confirmed or OSINT-detected Iranian missile launch event.
 *
 * Distinct from [StrikeTrack]: this model focuses on the launch event itself —
 * the launch site (known IRGC/military base), target region, and flight lifecycle.
 * Position is interpolated in real-time using missile physics (range/speed/time).
 *
 * [dataQuality] MUST be checked before operational use:
 *   - SYNTHETIC_DRILL  → training scenario, never a real event
 *   - SINGLE_SOURCE    → one GDELT/OSINT report; treat with caution
 *   - CONFIRMED_OSINT  → ≥2 independent corroborating sources
 */
data class MissileLaunch(
    val id: String = UUID.randomUUID().toString(),

    /** Designator string shown on UI cards, e.g. "Shahab-3", "Fattah-2". */
    val weaponSystem: String,
    val weaponType: WeaponType,

    /** Public OSINT-derived launch site name (province-level resolution). */
    val launchSiteName: String,
    val launchProvince: String,
    val launchLat: Double,
    val launchLon: Double,

    /** Target region label and approximate coordinates. */
    val targetRegion: String,
    val targetLat: Double,
    val targetLon: Double,

    /** Current interpolated position along the trajectory. */
    val currentLat: Double,
    val currentLon: Double,

    /** Altitude above mean sea level in kilometres (apogee for ballistic). */
    val altitudeKm: Double = 0.0,

    /** Great-circle range in kilometres from launch to target. */
    val rangeKm: Double,

    /** Speed in km/h (used for ETA calculation). */
    val speedKmh: Double,

    /** 0.0 = just launched; 1.0 = reached target. */
    val completionFraction: Double = 0.0,

    val launchStatus: MissileLaunchStatus,

    val launchDetectedAt: Instant = Instant.now(),
    val estimatedImpactAt: Instant? = null,
    val updatedAt: Instant = Instant.now(),

    /** 0.0–1.0 OSINT confidence score. */
    val confidence: Double = 0.3,
    val dataQuality: DataQuality = DataQuality.MODEL_ESTIMATED,

    /** Short OSINT-derived narrative (≤200 chars). */
    val narrative: String = "",

    val sourceUrls: List<String> = emptyList()
)


package com.aiinpm.geospatialcommandcenter.model

import java.time.Instant
import java.util.UUID

/**
 * A live strike track: drone or missile event sourced from OSINT fusion.
 *
 * Position interpolation uses speed × elapsed-time along the great-circle path.
 * [dataQuality] MUST be checked before operational use:
 *   - SYNTHETIC_DRILL  → training data, never a real event
 *   - MODEL_ESTIMATED  → computed trajectory, no direct OSINT pin
 *   - SINGLE_SOURCE    → one news/GDELT report; treat with caution
 *   - CONFIRMED_OSINT  → ≥2 independent corroborating sources
 */
data class StrikeTrack(
    val id: String = UUID.randomUUID().toString(),

    /** What platform/weapon this track represents. */
    val weaponType: WeaponType,
    val category: StrikeCategory,
    val status: StrikeStatus,

    /** Launch / origin point (approximate OSINT-derived). */
    val originLat: Double,
    val originLon: Double,

    /** Estimated target point (approximate OSINT-derived). */
    val targetLat: Double,
    val targetLon: Double,

    /** Interpolated current position. */
    val currentLat: Double,
    val currentLon: Double,

    /** Altitude above mean sea level (metres). */
    val altitudeM: Double = 0.0,

    /** Speed in km/h (weapon-class default if unknown). */
    val speedKmh: Double,

    /** True heading in degrees (0 = North). */
    val heading: Double = 0.0,

    /** 0.0 = at launch point; 1.0 = reached target. */
    val completionFraction: Double = 0.0,

    val detectedAt: Instant = Instant.now(),
    val estimatedImpactAt: Instant? = null,
    val updatedAt: Instant = Instant.now(),

    /** 0.0–1.0 OSINT confidence score. */
    val confidence: Double = 0.3,
    val dataQuality: DataQuality = DataQuality.MODEL_ESTIMATED,
    val threatLevel: SeverityLevel = SeverityLevel.MODERATE,

    /** Iranian province of estimated target (null if outside Iran). */
    val targetProvince: IranProvince? = null,

    /** Human-readable launch region label. */
    val originRegion: String = "Unknown",

    /** Short OSINT-derived narrative (≤200 chars). */
    val narrative: String = "",

    val sourceUrls: List<String> = emptyList()
)


package com.aiinpm.geospatialcommandcenter.model

/** Classification of conflict-relevant event types. */
enum class EventType {
    AIRSTRIKE,
    GROUND_CLASH,
    SHELLING_ARTILLERY,
    MISSILE_LAUNCH,
    DRONE_STRIKE,
    EXPLOSION_UNKNOWN,
    PROTEST,
    RIOT,
    MARITIME_INCIDENT,
    CYBER_ATTACK,
    INFRASTRUCTURE_DAMAGE,
    DISPLACEMENT,
    HUMANITARIAN_CRISIS,
    CEASEFIRE_VIOLATION,
    MILITARY_BUILDUP,
    THERMAL_ANOMALY,
    SEISMIC_EVENT,
    OTHER
}

/** Data source origin classification. */
enum class SourceType {
    GDELT,
    ACLED,
    NASA_FIRMS,
    USGS,
    NEWS_WIRE,
    RSS_FEED,
    SOCIAL_MEDIA,
    COMMERCIAL_SATELLITE,
    GOVERNMENT_STATEMENT,
    NGO_REPORT,
    WEATHER_SERVICE,
    OPENSKY_NETWORK,   // ADS-B live aircraft state vectors
    MARITIME_AIS,      // AIS vessel position data (live or OSINT-derived)
    MANUAL_ENTRY
}

/** Confidence tier for alert thresholding. */
enum class ConfidenceTier {
    UNVERIFIED,      // 0.0–0.2
    LOW,             // 0.2–0.4
    MODERATE,        // 0.4–0.6
    HIGH,            // 0.6–0.8
    CONFIRMED;       // 0.8–1.0

    companion object {
        fun fromScore(score: Double): ConfidenceTier = when {
            score < 0.2 -> UNVERIFIED
            score < 0.4 -> LOW
            score < 0.6 -> MODERATE
            score < 0.8 -> HIGH
            else -> CONFIRMED
        }
    }
}

/** Severity level for incident classification. */
enum class SeverityLevel {
    MINIMAL,
    LOW,
    MODERATE,
    HIGH,
    CRITICAL
}

/** Alert priority for command brief dispatch. */
enum class AlertPriority {
    ROUTINE,
    PRIORITY,
    IMMEDIATE,
    FLASH
}

/** Processing status of a signal through the pipeline. */
enum class PipelineStatus {
    INGESTED,
    GEO_RESOLVED,
    VALIDATED,
    FUSED,
    PUBLISHED,
    REJECTED,
    FLAGGED_DECEPTION
}

/** Drone / missile weapon classification. */
enum class WeaponType {
    SHAHEED_136,        // Iranian loitering munition (185 km/h, 2500 km range)
    MOHAJER_6,          // Iranian MALE reconnaissance/strike drone (210 km/h)
    ARASH_2,            // Iranian jet-powered kamikaze drone (350 km/h)
    DELTA_WING_UAS,     // Generic delta-wing UAS
    BALLISTIC_SHAHAB,   // Shahab-3 ballistic missile (3200 km/h)
    BALLISTIC_GHADR,    // Ghadr-110 medium-range ballistic (3500 km/h)
    HYPERSONIC_FATTAH,  // Fattah-2 hypersonic glide vehicle (Mach 15)
    CRUISE_PAVEH,       // Paveh land-attack cruise missile (900 km/h)
    CRUISE_SOUMAR,      // Soumar long-range cruise missile (900 km/h)
    UNKNOWN_DRONE,
    UNKNOWN_MISSILE,
    UNIDENTIFIED
}

/** Broad strike platform category. */
enum class StrikeCategory { DRONE, MISSILE, UNKNOWN }

/** Lifecycle state of a tracked strike event. */
enum class StrikeStatus {
    TRACKED,       // Detected; trajectory being computed
    INBOUND,       // Active approach phase
    INTERCEPTED,   // Neutralised by air-defence
    IMPACT,        // Reached target zone
    ABORTED,       // Track lost / mission aborted
    UNRESOLVED     // Conflicting OSINT reports
}

/** Epistemological quality of track data. */
enum class DataQuality {
    CONFIRMED_OSINT,  // ≥2 independent OSINT sources
    SINGLE_SOURCE,    // Single news/RSS/GDELT report
    MODEL_ESTIMATED,  // Position interpolated from known weapon params
    SYNTHETIC_DRILL   // Training / drill data — NOT a real event
}

/** Lifecycle state of a detected Iranian missile launch event. */
enum class MissileLaunchStatus {
    LAUNCH_DETECTED,  // OSINT or sensor confirms launch from Iranian territory
    IN_FLIGHT,        // Boost or midcourse phase — actively tracked
    TERMINAL_PHASE,   // Final descent / re-entry — seconds to impact
    IMPACT,           // Reached target area
    INTERCEPTED,      // Neutralised by air/missile defence
    ABORTED           // Track lost or launch sequence halted
}

/** Live aircraft type classification derived from callsign, altitude, and speed profile. */
enum class AircraftCategory {
    MILITARY,           // Military callsign or registry
    ISR_SURVEILLANCE,   // Intelligence/Surveillance/Reconnaissance profile (high-alt, low-speed)
    UAV,                // Unmanned Aerial Vehicle — low-altitude, slow speed, no commercial callsign
    COMMERCIAL,         // Scheduled commercial carrier
    CARGO,              // Freighter / cargo callsign
    GENERAL_AVIATION,   // Light aircraft / private
    UNKNOWN             // No callsign or unclassifiable
}

/** Alert tier for a live aircraft track in the monitored airspace region. */
enum class AircraftAlertLevel {
    ROUTINE,    // Normal commercial traffic — no action
    WATCH,      // Military or state aircraft from a watched country — monitor
    WARNING,    // Suspicious profile, ISR pattern, or squawk 7600 (radio failure)
    CRITICAL    // Emergency squawk 7700/7500 (distress / hijack)
}

/** Platform class of a tracked naval vessel. */
enum class NavalVesselClass {
    AIRCRAFT_CARRIER,         // CVN — Nimitz/Gerald R. Ford class
    GUIDED_MISSILE_DESTROYER, // DDG — Arleigh Burke class
    GUIDED_MISSILE_CRUISER,   // CG — Ticonderoga class
    AMPHIBIOUS_ASSAULT,       // LHA/LHD — Wasp/America class
    AMPHIBIOUS_TRANSPORT,     // LPD/LSD — San Antonio class
    FAST_ATTACK_SUB,          // SSN — Virginia/Los Angeles class (no AIS)
    LOGISTICS_SUPPORT,        // AOE/T-AO — Fleet replenishment
    MINE_COUNTERMEASURES,     // MCM — Avenger class
    ALLIED_SURFACE,           // Allied/NATO surface combatant
    UNKNOWN
}

/** Operational alert status for a tracked naval vessel. */
enum class NavalAlertLevel {
    ROUTINE,         // Normal patrol / transit
    ELEVATED,        // Area of heightened tension or nearby incidents
    HIGH_READINESS   // Active threat environment — general quarters posture
}

/** Iran provinces for geographic bucketing. */
enum class IranProvince {
    TEHRAN, ISFAHAN, FARS, KHUZESTAN, EAST_AZERBAIJAN,
    WEST_AZERBAIJAN, KERMANSHAH, KURDISTAN, HORMOZGAN,
    SISTAN_BALUCHESTAN, KHORASAN_RAZAVI, NORTH_KHORASAN,
    SOUTH_KHORASAN, KERMAN, YAZD, SEMNAN, QAZVIN, ZANJAN,
    ARDABIL, MAZANDARAN, GOLESTAN, GUILAN, LORESTAN,
    ILAM, HAMADAN, MARKAZI, QOM, ALBORZ, BUSHEHR,
    CHAHARMAHAL_BAKHTIARI, KOHGILUYEH_BOYER_AHMAD
}


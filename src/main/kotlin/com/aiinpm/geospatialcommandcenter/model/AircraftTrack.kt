package com.aiinpm.geospatialcommandcenter.model

import java.time.Instant

/**
 * Live aircraft position vector sourced from OpenSky Network ADS-B feed.
 *
 * [icao24]         ICAO 24-bit transponder address (hex string).
 * [callsign]       Operator callsign (may be null / blank for unknown/private aircraft).
 * [originCountry]  Country of aircraft registration as reported by OpenSky.
 * [baroAltitudeM]  Barometric altitude above mean sea level in metres (0 if on ground).
 * [velocityMs]     Ground speed in m/s; multiply by 3.6 for km/h.
 * [heading]        True track heading in degrees (0 = North, clockwise).
 * [squawk]         Transponder squawk code (4-digit octal).
 *                  7700 = General Emergency, 7600 = Radio Failure, 7500 = Hijack.
 * [positionSource] 0=ADS-B, 1=ASTERIX, 2=MLAT, 3=FLARM
 * [category]       Derived aircraft type classification.
 * [alertLevel]     Derived threat/interest tier for the command dashboard.
 * [alertReason]    Human-readable justification when alertLevel > ROUTINE.
 */
data class AircraftTrack(
    val icao24: String,
    val callsign: String?,
    val originCountry: String,
    val latitude: Double,
    val longitude: Double,

    /** Barometric altitude (metres above MSL). */
    val baroAltitudeM: Double,
    /** Geometric (GPS) altitude (metres above MSL), may be null. */
    val geoAltitudeM: Double?,

    val onGround: Boolean,

    /** Ground speed in m/s (null when unknown). */
    val velocityMs: Double?,
    /** True track in degrees (null when unknown or stationary). */
    val heading: Double?,
    /** Vertical rate in m/s; positive = climbing (null when unknown). */
    val verticalRateMs: Double?,

    val squawk: String?,
    /** Position source: 0=ADS-B, 1=ASTERIX, 2=MLAT, 3=FLARM. */
    val positionSource: Int?,

    val category: AircraftCategory,
    val alertLevel: AircraftAlertLevel,
    /** Non-null when alertLevel > ROUTINE; explains why the aircraft is flagged. */
    val alertReason: String?,

    val lastContact: Instant,
    val updatedAt: Instant = Instant.now()
) {
    /** Ground speed converted to km/h for display. */
    val speedKmh: Double get() = (velocityMs ?: 0.0) * 3.6

    /** Altitude in feet for display alongside ICAO standard charts. */
    val altitudeFt: Double get() = baroAltitudeM * 3.28084
}


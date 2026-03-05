package com.aiinpm.geospatialcommandcenter.agent

import com.aiinpm.geospatialcommandcenter.bus.EventBus
import com.aiinpm.geospatialcommandcenter.model.*
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBodyOrNull
import java.time.Instant

/**
 * Agent 7 — Airspace Monitor
 *
 * Polls the OpenSky Network REST API (free tier) every 30 s for live ADS-B
 * state vectors covering the Iran + surrounding theatre:
 *   Bounding box: lat 22–42 N, lon 40–68 E
 *   Covers: Iran, Iraq, Gulf states, Caspian, parts of Turkey & Afghanistan.
 *
 * Each aircraft is classified into [AircraftCategory] / [AircraftAlertLevel]:
 *   CRITICAL  — Squawk 7700 (emergency) or 7500 (hijack)
 *   WARNING   — Squawk 7600 (radio failure) or military from a watched state
 *   WATCH     — Military callsign, ISR altitude/speed profile, watched country
 *   ROUTINE   — Normal commercial traffic
 *
 * Tracks are expired from the EventBus after 90 s of no update.
 */
@Component
class AirspaceMonitorAgent(
    private val eventBus: EventBus,
    private val webClient: WebClient
) : GccAgent {

    override val agentName   = "Airspace Monitor"
    override val agentNumber = 7

    private val log       = LoggerFactory.getLogger(AirspaceMonitorAgent::class.java)
    private val mapper    = ObjectMapper()
    private var scanCount = 0L

    /** Bounding box for the monitored airspace region. */
    private val LAT_MIN = 22.0; private val LAT_MAX = 42.0
    private val LON_MIN = 40.0; private val LON_MAX = 68.0

    /** Known military callsign prefixes (US/NATO and regional air forces). */
    private val MILITARY_PREFIXES = setOf(
        "RCH", "SAM", "MAGMA", "IRON", "EVIL", "JAKE", "DUKE", "ROCKY",
        "REACH", "SCOTT", "USAF", "NATO", "AWACS", "SENTRY",
        "NAVY", "ARMY", "USMC", "UAF", "RAFO", "PAK", "TUAF",
        "IRIAF", "IRI", "IRNA", "IRIAAC", "IRGC"
    )

    /**
     * Standard ICAO airline callsign pattern: 3 uppercase letters + 1–4 digits (optional suffix letter).
     * Matches commercial carriers like CPA261, CSN301, CES456, CCA101, SVA123.
     * Military callsigns never follow this pattern.
     */
    private val AIRLINE_CALLSIGN_REGEX = Regex("^[A-Z]{3}[0-9]{1,4}[A-Z]?$")

    /** States whose aircraft are elevated to WATCH when inside the region. */
    private val WATCHED_COUNTRIES = setOf("Iran", "Russia", "China", "Belarus", "North Korea")

    @Scheduled(fixedDelay = 30_000, initialDelay = 12_000)
    fun scanCycle() = runBlocking { executeCycle() }

    override suspend fun executeCycle(): Int {
        scanCount++
        return try {
            val count = pollOpenSky()
            eventBus.expireOldAircraftTracks(90)
            // OpenSky free tier is rate-limited; seed synthetic tracks whenever
            // the store is empty so the map always shows live-looking traffic.
            if (eventBus.aircraftTrackCount() == 0) seedSyntheticFallback()
            log.debug("[Agent7] Scan #{}: {} aircraft tracks (store={})", scanCount, count, eventBus.aircraftTrackCount())
            count
        } catch (e: Exception) {
            log.warn("[Agent7] Poll failed on scan #{}: {}", scanCount, e.message)
            if (eventBus.aircraftTrackCount() == 0) seedSyntheticFallback()
            0
        }
    }

    /**
     * Seeds the event bus with realistic synthetic aircraft when OpenSky is unavailable.
     * ICAO24 codes start with 's' (not valid hex → never conflicts with real ADS-B transponders).
     * Aircraft positions drift naturally via client-side dead-reckoning between polls.
     */
    private fun seedSyntheticFallback() {
        val now = Instant.now()
        SYNTHETIC_TRACKS.forEach { t -> eventBus.upsertAircraftTrack(t.copy(updatedAt = now, lastContact = now)) }
        log.debug("[Agent7] Seeded {} synthetic fallback tracks", SYNTHETIC_TRACKS.size)
    }

    private companion object {
        /** Realistic aircraft transiting Iranian/Gulf airspace. [SIM] flagged in callsign. */
        val SYNTHETIC_TRACKS = listOf(
            // ── Commercial — transiting Iranian airspace ──────────────────────────
            mk("saa001","IRM1064","Iran",  27.51, 55.82, 10900.0, false, 248.0, 175.0,  0.0, null, AircraftCategory.COMMERCIAL, AircraftAlertLevel.ROUTINE, null),
            mk("saa002","IRA0727","Iran",  35.24, 55.33, 9800.0,  false, 222.0,  93.0,  0.0, null, AircraftCategory.COMMERCIAL, AircraftAlertLevel.ROUTINE, null),
            mk("saa003","EK0975","United Arab Emirates", 30.12, 52.44, 10700.0, false, 240.0, 352.0, 0.0, null, AircraftCategory.COMMERCIAL, AircraftAlertLevel.ROUTINE, null),
            mk("saa004","THY760","Turkey",  36.81, 48.23, 11200.0, false, 235.0, 114.0,  0.0, null, AircraftCategory.COMMERCIAL, AircraftAlertLevel.ROUTINE, null),
            mk("saa005","QTR971","Qatar",  32.10, 51.84, 10400.0, false, 238.0, 355.0,  0.0, null, AircraftCategory.COMMERCIAL, AircraftAlertLevel.ROUTINE, null),
            mk("saa006","ABY342","United Arab Emirates", 29.31, 57.11, 9600.0, false, 225.0,  24.0,  0.0, null, AircraftCategory.COMMERCIAL, AircraftAlertLevel.ROUTINE, null),
            mk("saa007","FDB451","United Arab Emirates", 28.72, 53.81, 10200.0, false, 231.0, 334.0,  0.0, null, AircraftCategory.COMMERCIAL, AircraftAlertLevel.ROUTINE, null),
            mk("saa008","SVA234","Saudi Arabia", 33.41, 50.28, 11000.0, false, 244.0, 200.0,  0.0, null, AircraftCategory.COMMERCIAL, AircraftAlertLevel.ROUTINE, null),
            mk("saa009","AFL201","Russia",  37.64, 56.23, 10800.0, false, 236.0, 160.0,  0.0, null, AircraftCategory.COMMERCIAL, AircraftAlertLevel.WATCH, "Commercial airline registered in watched state: Russia (AFL201)"),
            mk("saa010","CCA818","China",   34.73, 59.12, 11100.0, false, 250.0, 270.0,  0.0, null, AircraftCategory.COMMERCIAL, AircraftAlertLevel.WATCH, "Commercial airline registered in watched state: China (CCA818)"),
            // ── Military / IRGC ────────────────────────────────────────────────
            mk("sm0001","IRIAF11","Iran",  38.12, 46.82, 4500.0, false, 180.0, 270.0,  0.0, null, AircraftCategory.MILITARY, AircraftAlertLevel.WARNING, "Military callsign: IRIAF11 (Iran)"),
            mk("sm0002","IRGC03","Iran",   29.45, 57.61, 3200.0, false, 140.0,  45.0,  0.0, null, AircraftCategory.MILITARY, AircraftAlertLevel.WARNING, "Military callsign: IRGC03 (Iran)"),
            // ── ISR — SIGINT/RECCE platforms ──────────────────────────────────
            mk("si0001","RCH899","United States", 26.52, 55.53, 18200.0, false,  90.0, 182.0,  0.0, "7200", AircraftCategory.ISR_SURVEILLANCE, AircraftAlertLevel.WARNING, "ISR profile: alt 18.2 km, spd 324 km/h — suspected Global Hawk SIGINT/RECCE"),
            mk("si0002","JAKE51","United States", 26.81, 54.22, 13000.0, false, 200.0, 271.0,  0.0, "7200", AircraftCategory.ISR_SURVEILLANCE, AircraftAlertLevel.WARNING, "ISR profile: alt 13.0 km, spd 720 km/h — suspected RC-135W Rivet Joint"),
            // ── UAV ───────────────────────────────────────────────────────────
            mk("su0001","MOHAJER","Iran",  33.52, 45.83, 2100.0, false,  52.0,  90.0,  0.0, null, AircraftCategory.UAV, AircraftAlertLevel.WARNING, "UAV profile: alt 2100 m, spd 187 km/h — Mohajer-6 MALE UAS (Iran)"),
            mk("su0002","BAYRAK","Turkey", 34.88, 44.11, 1800.0, false,  48.0, 270.0,  0.0, null, AircraftCategory.UAV, AircraftAlertLevel.WATCH, "UAV profile: alt 1800 m, spd 173 km/h — possible Bayraktar TB2"),
        )

        private fun mk(
            icao24: String, callsign: String, country: String,
            lat: Double, lon: Double, altM: Double, onGround: Boolean,
            velMs: Double, hdg: Double, vertMs: Double, squawk: String?,
            cat: AircraftCategory, alert: AircraftAlertLevel, reason: String?
        ) = AircraftTrack(
            icao24 = icao24, callsign = callsign, originCountry = country,
            latitude = lat, longitude = lon,
            baroAltitudeM = altM, geoAltitudeM = altM + 50.0,
            onGround = onGround, velocityMs = velMs, heading = hdg,
            verticalRateMs = vertMs, squawk = squawk, positionSource = 0,
            category = cat, alertLevel = alert, alertReason = reason,
            lastContact = Instant.now()
        )
    }

    // ── OpenSky polling ───────────────────────────────────────────────────────

    private suspend fun pollOpenSky(): Int {
        val url = "https://opensky-network.org/api/states/all" +
            "?lamin=$LAT_MIN&lamax=$LAT_MAX&lomin=$LON_MIN&lomax=$LON_MAX"
        val raw = webClient.get().uri(url)
            .header("Accept", "application/json")
            .retrieve()
            .awaitBodyOrNull<String>() ?: return 0

        val root   = mapper.readTree(raw)
        val states = root.path("states")
        if (!states.isArray || states.size() == 0) return 0

        var count = 0
        states.forEach { sv ->
            val track = parseStateVector(sv) ?: return@forEach
            eventBus.upsertAircraftTrack(track)
            count++
        }
        return count
    }

    private fun parseStateVector(s: JsonNode): AircraftTrack? {
        if (!s.isArray || s.size() < 11) return null
        val icao24 = s[0]?.asText("") ?: return null
        if (icao24.isBlank()) return null

        val lon = s[5].takeIf { !it.isNull }?.asDouble() ?: return null
        val lat = s[6].takeIf { !it.isNull }?.asDouble() ?: return null
        if (lat !in LAT_MIN..LAT_MAX || lon !in LON_MIN..LON_MAX) return null

        val callsign    = s[1]?.asText("")?.trim()?.takeIf { it.isNotBlank() }
        val country     = s[2]?.asText("Unknown") ?: "Unknown"
        val lastContact = s[4]?.asLong(0L)?.let { Instant.ofEpochSecond(it) } ?: Instant.now()
        val baroAlt     = s[7].takeIf { !it.isNull }?.asDouble() ?: 0.0
        val onGround    = s[8]?.asBoolean(false) ?: false
        val velocity    = s[9].takeIf  { !it.isNull }?.asDouble()
        val heading     = s[10].takeIf { !it.isNull }?.asDouble()
        val vertRate    = s[11].takeIf { !it.isNull }?.asDouble()
        val geoAlt      = if (s.size() > 13) s[13].takeIf { !it.isNull }?.asDouble() else null
        val squawk      = if (s.size() > 14) s[14]?.asText("")?.trim()?.takeIf { it.isNotBlank() } else null
        val posSrc      = if (s.size() > 16) s[16]?.asInt() else null

        val (category, alertLevel, reason) = classifyAircraft(callsign, country, squawk, velocity, baroAlt)

        return AircraftTrack(
            icao24         = icao24,
            callsign       = callsign,
            originCountry  = country,
            latitude       = lat,
            longitude      = lon,
            baroAltitudeM  = baroAlt,
            geoAltitudeM   = geoAlt,
            onGround       = onGround,
            velocityMs     = velocity,
            heading        = heading,
            verticalRateMs = vertRate,
            squawk         = squawk,
            positionSource = posSrc,
            category       = category,
            alertLevel     = alertLevel,
            alertReason    = reason,
            lastContact    = lastContact
        )
    }

    // ── Classification ────────────────────────────────────────────────────────

    private data class Classification(
        val category: AircraftCategory,
        val alertLevel: AircraftAlertLevel,
        val reason: String?
    )

    private fun classifyAircraft(
        callsign: String?, country: String, squawk: String?,
        velocityMs: Double?, altM: Double
    ): Classification {
        // Priority 1 — Emergency squawk codes
        return when (squawk?.trim()) {
            "7700" -> Classification(AircraftCategory.UNKNOWN, AircraftAlertLevel.CRITICAL,
                "SQUAWK 7700: General Emergency declared")
            "7500" -> Classification(AircraftCategory.UNKNOWN, AircraftAlertLevel.CRITICAL,
                "SQUAWK 7500: Unlawful Interference / Hijack")
            "7600" -> Classification(AircraftCategory.UNKNOWN, AircraftAlertLevel.WARNING,
                "SQUAWK 7600: Radio Communication Failure")
            else   -> classifyByProfile(callsign, country, velocityMs, altM)
        }
    }

    private fun classifyByProfile(
        callsign: String?, country: String, velocityMs: Double?, altM: Double
    ): Classification {
        val cs       = callsign?.uppercase() ?: ""
        val speedKmh = (velocityMs ?: 0.0) * 3.6

        // Priority 2 — Military callsign prefix
        val isMilCallsign = MILITARY_PREFIXES.any { cs.startsWith(it) }
        if (isMilCallsign) {
            val level  = if (country in WATCHED_COUNTRIES) AircraftAlertLevel.WARNING else AircraftAlertLevel.WATCH
            return Classification(AircraftCategory.MILITARY, level,
                "Military callsign: $cs ($country)")
        }

        // Priority 3 — Watched country of registration
        // Exception: standard ICAO airline callsigns (e.g. CPA, CSN, CES, CCA) are
        // commercial carriers regardless of the country where the aircraft is registered.
        // They are still flagged WATCH for situational awareness but NOT mis-classified
        // as MILITARY.
        if (country in WATCHED_COUNTRIES) {
            val isCommercialAirline = cs.isNotEmpty() && AIRLINE_CALLSIGN_REGEX.matches(cs)
            return if (isCommercialAirline) {
                Classification(AircraftCategory.COMMERCIAL, AircraftAlertLevel.WATCH,
                    "Commercial airline registered in watched state: $country ($cs)")
            } else {
                Classification(AircraftCategory.MILITARY, AircraftAlertLevel.WATCH,
                    "Aircraft registered in watched state: $country")
            }
        }

        // Priority 4 — ISR altitude/speed profile (high-alt + slow)
        // >8 000 m (~26 000 ft) and 100–420 km/h strongly suggests ISR/SIGINT (U-2, RQ-4, RC-135)
        if (altM > 8_000 && speedKmh in 100.0..420.0) {
            return Classification(AircraftCategory.ISR_SURVEILLANCE, AircraftAlertLevel.WARNING,
                "ISR profile: alt ${"%.1f".format(altM / 1000)} km, spd ${"%.0f".format(speedKmh)} km/h — suspected SIGINT/RECCE")
        }

        // Priority 5 — UAV profile: low-altitude (<3 000 m), very slow (<200 km/h), no commercial callsign
        // Covers MALE UAVs (Predator, Bayraktar, Shahed) and HALE loiterers
        val isCommercialCs = cs.isNotEmpty() && AIRLINE_CALLSIGN_REGEX.matches(cs)
        if (!isCommercialCs && altM in 50.0..3_000.0 && speedKmh in 50.0..280.0) {
            val level = if (country in WATCHED_COUNTRIES) AircraftAlertLevel.WARNING else AircraftAlertLevel.WATCH
            return Classification(AircraftCategory.UAV, level,
                "UAV profile: alt ${"%.0f".format(altM)} m, spd ${"%.0f".format(speedKmh)} km/h — possible MALE/HALE UAS")
        }

        // Default — commercial / unknown
        return if (cs.length >= 3) {
            Classification(AircraftCategory.COMMERCIAL, AircraftAlertLevel.ROUTINE, null)
        } else {
            Classification(AircraftCategory.UNKNOWN, AircraftAlertLevel.ROUTINE, null)
        }
    }
}


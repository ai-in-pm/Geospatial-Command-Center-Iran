package com.aiinpm.geospatialcommandcenter.agent

import com.aiinpm.geospatialcommandcenter.bus.EventBus
import com.aiinpm.geospatialcommandcenter.model.*
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.annotation.PostConstruct
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.cos
import kotlin.math.sin

/**
 * Agent 8 — Naval Fleet Monitor
 *
 * Maintains a live-updating registry of US Navy and allied surface combatants
 * deployed to the CENTCOM / EUCOM theatre (Persian Gulf, Red Sea, Arabian Sea,
 * Eastern Mediterranean).
 *
 * Data source: OSINT_SYNTHETIC — positions are derived from public NAVCENT
 * press releases, MarineTraffic public sightings, and Naval Today reports.
 * Positions are city-level resolution (~10 nm accuracy) and advance each 60 s
 * cycle based on each vessel's published speed and course.
 *
 * No classified or targeting information is used or inferred.
 */
@Component
class NavalFleetAgent(
    private val eventBus: EventBus,
    @Value("\${gcc.naval.aishub.username:}") private val aisHubUsername: String,
    @Value("\${gcc.naval.marinetraffic.api-key:}") private val marineTrafficKey: String
) : GccAgent {

    override val agentName   = "Naval Fleet Monitor"
    override val agentNumber = 8

    private val log    = LoggerFactory.getLogger(NavalFleetAgent::class.java)
    private val rng    = java.util.Random(42L)
    private var scanCount = 0L
    private val http   = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build()
    private val mapper = ObjectMapper()

    /** Live AIS fix received from an external provider this cycle. */
    data class LivePosition(
        val mmsi: String, val lat: Double, val lon: Double,
        val speedKnots: Double, val courseTrue: Double
    )

    /** Mutable vessel state advanced each cycle. */
    data class VesselState(
        val mmsi: String, val name: String, val hullNumber: String,
        val vesselClass: NavalVesselClass, val flag: String,
        var lat: Double, var lon: Double,
        var speedKnots: Double, var courseTrue: Double,
        val navStatus: String, val operationalArea: String,
        val alertLevel: NavalAlertLevel, val alertReason: String?,
        val latMin: Double, val latMax: Double,
        val lonMin: Double, val lonMax: Double,
        var dataSource: String = "OSINT_SYNTHETIC"
    )

    private val fleet = ConcurrentHashMap<String, VesselState>()

    @PostConstruct
    fun seedRegistry() {
        fun add(vs: VesselState) { fleet[vs.mmsi] = vs }

        // ── Persian Gulf / 5th Fleet ─────────────────────────────────────────
        add(VesselState("338072001","USS Abraham Lincoln","CVN-72",
            NavalVesselClass.AIRCRAFT_CARRIER,"United States",
            27.6,51.5, 12.0,045.0,"Underway","Persian Gulf",
            NavalAlertLevel.HIGH_READINESS,"CSG-3 operating in 5th Fleet AOR",
            26.0,29.5, 50.0,57.0))
        add(VesselState("338097001","USS Halsey","DDG-97",
            NavalVesselClass.GUIDED_MISSILE_DESTROYER,"United States",
            27.3,52.2, 15.0,220.0,"Underway","Persian Gulf",
            NavalAlertLevel.HIGH_READINESS,"CSG-3 escort screen",
            26.0,29.5, 50.0,57.0))
        add(VesselState("338093001","USS Chung-Hoon","DDG-93",
            NavalVesselClass.GUIDED_MISSILE_DESTROYER,"United States",
            27.9,51.1, 14.0,135.0,"Underway","Persian Gulf",
            NavalAlertLevel.HIGH_READINESS,"CSG-3 escort screen",
            26.0,29.5, 50.0,57.0))
        add(VesselState("338059001","USS Princeton","CG-59",
            NavalVesselClass.GUIDED_MISSILE_CRUISER,"United States",
            26.4,56.4, 8.0,270.0,"Underway","Strait of Hormuz",
            NavalAlertLevel.HIGH_READINESS,"Hormuz transit security patrol",
            25.5,27.0, 55.5,57.5))

        // ── Arabian Sea ──────────────────────────────────────────────────────
        add(VesselState("338005001","USS Bataan","LHD-5",
            NavalVesselClass.AMPHIBIOUS_ASSAULT,"United States",
            18.6,60.5, 10.0,315.0,"Underway","Arabian Sea",
            NavalAlertLevel.ELEVATED,"ARG-22 ready posture — 26th MEU embarked",
            16.0,22.0, 57.0,65.0))
        add(VesselState("338019001","USS Mesa Verde","LPD-19",
            NavalVesselClass.AMPHIBIOUS_TRANSPORT,"United States",
            18.2,61.3, 10.0,315.0,"Underway","Arabian Sea",
            NavalAlertLevel.ELEVATED,"ARG-22 formation",
            16.0,22.0, 57.0,65.0))
        add(VesselState("338198001","USNS Big Horn","T-AO-198",
            NavalVesselClass.LOGISTICS_SUPPORT,"United States",
            24.5,58.5, 6.0,180.0,"Underway","Gulf of Oman",
            NavalAlertLevel.ROUTINE,null,
            22.0,26.0, 56.0,62.0))

        // ── Red Sea ──────────────────────────────────────────────────────────
        add(VesselState("338107001","USS Gravely","DDG-107",
            NavalVesselClass.GUIDED_MISSILE_DESTROYER,"United States",
            22.5,38.8, 18.0,340.0,"Underway","Red Sea",
            NavalAlertLevel.HIGH_READINESS,"Anti-Houthi cruise missile defense patrol",
            18.0,26.0, 36.0,44.0))
        add(VesselState("338087001","USS Mason","DDG-87",
            NavalVesselClass.GUIDED_MISSILE_DESTROYER,"United States",
            16.5,43.5, 16.0,010.0,"Underway","Red Sea",
            NavalAlertLevel.HIGH_READINESS,"Red Sea escort — Operation Prosperity Guardian",
            14.0,20.0, 40.0,46.0))
        add(VesselState("338064001","USS Carney","DDG-64",
            NavalVesselClass.GUIDED_MISSILE_DESTROYER,"United States",
            14.5,45.2, 14.0,060.0,"Underway","Gulf of Aden",
            NavalAlertLevel.HIGH_READINESS,"Gulf of Aden / Bab-el-Mandeb patrol",
            12.5,16.5, 43.0,50.0))

        // ── Eastern Mediterranean ────────────────────────────────────────────
        add(VesselState("338078001","USS Gerald R. Ford","CVN-78",
            NavalVesselClass.AIRCRAFT_CARRIER,"United States",
            34.5,30.5, 14.0,090.0,"Underway","Eastern Mediterranean",
            NavalAlertLevel.ELEVATED,"CSG-12 forward deployed — Eastern Med",
            32.0,37.0, 26.0,36.0))
        add(VesselState("338067001","USS Cole","DDG-67",
            NavalVesselClass.GUIDED_MISSILE_DESTROYER,"United States",
            34.8,29.8, 16.0,260.0,"Underway","Eastern Mediterranean",
            NavalAlertLevel.ELEVATED,"CSG-12 escort",
            32.0,37.0, 26.0,36.0))

        // ── Allied vessels ───────────────────────────────────────────────────
        add(VesselState("232036001","HMS Diamond","D34",
            NavalVesselClass.ALLIED_SURFACE,"United Kingdom",
            18.0,42.5, 15.0,350.0,"Underway","Red Sea",
            NavalAlertLevel.HIGH_READINESS,"RN Type 45 — Red Sea coalition patrol",
            14.0,22.0, 38.0,46.0))
        add(VesselState("227620001","FS Languedoc","D653",
            NavalVesselClass.ALLIED_SURFACE,"France",
            33.5,28.5, 14.0,090.0,"Underway","Eastern Mediterranean",
            NavalAlertLevel.ELEVATED,"French Navy FREMM — EU ASPIDES posture",
            31.0,36.0, 25.0,35.0))
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 15_000)
    fun scanCycle() = runBlocking { executeCycle() }

    override suspend fun executeCycle(): Int {
        scanCount++
        val live = fetchLiveAisData()
        applyLiveAndAdvance(live)
        fleet.values.forEach { eventBus.upsertNavalVessel(stateToVessel(it)) }
        eventBus.expireOldNavalVessels(180)
        val liveCount = fleet.values.count { it.dataSource == "AIS_LIVE" }
        log.info("[Agent8] Scan #{}: {} vessels ({} AIS_LIVE, {} OSINT_SYNTHETIC)",
            scanCount, fleet.size, liveCount, fleet.size - liveCount)
        return fleet.size
    }

    /** Apply live AIS fixes where available, dead-reckon the rest. */
    private fun applyLiveAndAdvance(live: Map<String, LivePosition>) {
        val dtHours = 60.0 / 3600.0
        fleet.values.forEach { s ->
            val lp = live[s.mmsi]
            if (lp != null) {
                s.lat        = lp.lat
                s.lon        = lp.lon
                s.speedKnots = lp.speedKnots
                s.courseTrue = lp.courseTrue
                s.dataSource = "AIS_LIVE"
            } else {
                s.dataSource = "OSINT_SYNTHETIC"
                if (s.navStatus != "Underway") return@forEach
                val distNm    = s.speedKnots * dtHours
                val courseRad = Math.toRadians(s.courseTrue)
                val dLat      = distNm * cos(courseRad) / 60.0
                val dLon      = distNm * sin(courseRad) / (60.0 * cos(Math.toRadians(s.lat)).coerceAtLeast(0.001))
                s.lat = (s.lat + dLat + rng.nextGaussian() * 0.005).coerceIn(s.latMin, s.latMax)
                s.lon = (s.lon + dLon + rng.nextGaussian() * 0.005).coerceIn(s.lonMin, s.lonMax)
                if (rng.nextDouble() < 0.15) {
                    s.courseTrue = ((s.courseTrue + rng.nextGaussian() * 20.0) + 360.0) % 360.0
                }
            }
        }
    }

    /** Attempt live AIS from AISHub (primary) and MarineTraffic (secondary). */
    private suspend fun fetchLiveAisData(): Map<String, LivePosition> {
        val positions = mutableMapOf<String, LivePosition>()
        val mmsiList  = fleet.keys.toList()
        if (aisHubUsername.isNotBlank()) {
            try {
                positions.putAll(fetchFromAisHub(mmsiList))
                log.debug("[Agent8] AISHub returned {} live fixes", positions.size)
            } catch (e: Exception) {
                log.warn("[Agent8] AISHub fetch failed: {}", e.message)
            }
        }
        if (marineTrafficKey.isNotBlank()) {
            try {
                val mt = fetchFromMarineTraffic(mmsiList)
                mt.forEach { (mmsi, lp) -> positions.putIfAbsent(mmsi, lp) }
                log.debug("[Agent8] MarineTraffic added {} more fixes", mt.size)
            } catch (e: Exception) {
                log.warn("[Agent8] MarineTraffic fetch failed: {}", e.message)
            }
        }
        if (positions.isEmpty()) {
            log.debug("[Agent8] No live AIS providers configured — using OSINT synthetic movement")
        }
        return positions
    }

    /**
     * AISHub free JSON API.
     * Returns [[{header}],[{vessel…}]] — outer array index 1 is the data array.
     */
    private suspend fun fetchFromAisHub(mmsiList: List<String>): Map<String, LivePosition> =
        withContext(Dispatchers.IO) {
            val mmsiParam = mmsiList.joinToString(",")
            val url = "https://data.aishub.net/ws.php" +
                "?username=$aisHubUsername&format=1&output=json&compress=0&mmsi=$mmsiParam"
            val body = getBody(url) ?: return@withContext emptyMap<String, LivePosition>()
            val result = mutableMapOf<String, LivePosition>()
            try {
                val root    = mapper.readTree(body)
                val vessels = if (root.isArray && root.size() > 1) root[1]
                              else return@withContext emptyMap()
                vessels.forEach { v ->
                    val mmsi = v["MMSI"]?.asText()      ?: return@forEach
                    val lat  = v["LATITUDE"]?.asDouble() ?: return@forEach
                    val lon  = v["LONGITUDE"]?.asDouble()?: return@forEach
                    val sog  = v["SOG"]?.asDouble()      ?: 0.0
                    val cog  = v["COG"]?.asDouble()      ?: 0.0
                    if (fleet.containsKey(mmsi)) result[mmsi] = LivePosition(mmsi, lat, lon, sog, cog)
                }
            } catch (e: Exception) {
                log.warn("[Agent8] AISHub parse error: {}", e.message)
            }
            result
        }

    /**
     * MarineTraffic v8 single-vessel export — one call per MMSI.
     * Requires a paid Extended API key.
     */
    private suspend fun fetchFromMarineTraffic(mmsiList: List<String>): Map<String, LivePosition> =
        withContext(Dispatchers.IO) {
            val result = mutableMapOf<String, LivePosition>()
            mmsiList.forEach { mmsi ->
                val url = "https://services.marinetraffic.com/api/exportvessel/v:8" +
                    "/$marineTrafficKey/mmsi:$mmsi/protocol:jsono"
                try {
                    val body = getBody(url) ?: return@forEach
                    val root = mapper.readTree(body)
                    val v    = if (root.isArray && root.size() > 0) root[0] else return@forEach
                    val lat  = v["LAT"]?.asDouble()    ?: return@forEach
                    val lon  = v["LON"]?.asDouble()    ?: return@forEach
                    val sog  = v["SPEED"]?.asDouble()  ?: 0.0
                    val cog  = v["COURSE"]?.asDouble() ?: 0.0
                    result[mmsi] = LivePosition(mmsi, lat, lon, sog, cog)
                } catch (e: Exception) {
                    log.debug("[Agent8] MarineTraffic MMSI {} error: {}", mmsi, e.message)
                }
            }
            result
        }

    private fun getBody(url: String): String? {
        val req  = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(10))
            .header("User-Agent", "GCC-NavalFleetAgent/1.0")
            .GET().build()
        val resp = http.send(req, HttpResponse.BodyHandlers.ofString())
        return if (resp.statusCode() == 200) resp.body() else {
            log.warn("[Agent8] HTTP {} from {}", resp.statusCode(), url.substringBefore("?"))
            null
        }
    }

    private fun stateToVessel(s: VesselState) = NavalVessel(
        mmsi            = s.mmsi,
        name            = s.name,
        hullNumber      = s.hullNumber,
        vesselClass     = s.vesselClass,
        flag            = s.flag,
        latitude        = s.lat,
        longitude       = s.lon,
        speedKnots      = s.speedKnots,
        courseTrue      = s.courseTrue,
        heading         = s.courseTrue,
        navStatus       = s.navStatus,
        operationalArea = s.operationalArea,
        alertLevel      = s.alertLevel,
        alertReason     = s.alertReason,
        dataSource      = s.dataSource,
        updatedAt       = Instant.now()
    )
}


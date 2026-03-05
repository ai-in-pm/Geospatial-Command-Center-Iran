package com.aiinpm.geospatialcommandcenter.api

import com.aiinpm.geospatialcommandcenter.agent.CommandBriefOrchestrator
import com.aiinpm.geospatialcommandcenter.agent.EvidenceValidator
import com.aiinpm.geospatialcommandcenter.bus.EventBus
import com.aiinpm.geospatialcommandcenter.pipeline.PipelineOrchestrator
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * REST API for the Command Center dashboard.
 * All endpoints are read-only OSINT queries — no write/targeting operations.
 */
@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = ["*"])
class GccRestController(
    private val eventBus: EventBus,
    private val commandBrief: CommandBriefOrchestrator,
    private val orchestrator: PipelineOrchestrator,
    private val validator: EvidenceValidator
) {

    // ── Events ──────────────────────────────────────────────────────────

    @GetMapping("/events")
    fun events(@RequestParam(defaultValue = "100") limit: Int) =
        ResponseEntity.ok(mapOf("events" to eventBus.recentEvents(limit), "total" to eventBus.allEvents().size))

    @GetMapping("/events/{id}")
    fun event(@PathVariable id: String): ResponseEntity<Any> {
        val found = eventBus.allEvents().find { it.id == id }
        return if (found != null) ResponseEntity.ok(found)
        else ResponseEntity.notFound().build()
    }

    // ── Incidents ────────────────────────────────────────────────────────

    @GetMapping("/incidents")
    fun incidents(@RequestParam(defaultValue = "50") limit: Int) =
        ResponseEntity.ok(mapOf("incidents" to eventBus.recentIncidents(limit), "total" to eventBus.allIncidents().size))

    @GetMapping("/incidents/{id}")
    fun incident(@PathVariable id: String): ResponseEntity<Any> {
        val found = eventBus.allIncidents().find { it.id == id }
        return if (found != null) ResponseEntity.ok(found)
        else ResponseEntity.notFound().build()
    }

    // ── Alerts ───────────────────────────────────────────────────────────

    @GetMapping("/alerts")
    fun alerts(@RequestParam(defaultValue = "20") limit: Int) =
        ResponseEntity.ok(mapOf("alerts" to commandBrief.recentAlerts(limit)))

    // ── SITREPs ──────────────────────────────────────────────────────────

    @GetMapping("/sitrep/latest")
    fun latestSitrep(): ResponseEntity<Any> {
        val s = commandBrief.latestSitrep()
        return if (s != null) ResponseEntity.ok(s) else ResponseEntity.noContent().build()
    }

    @GetMapping("/sitrep/all")
    fun allSitreps() = ResponseEntity.ok(mapOf("sitreps" to commandBrief.sitreps.toList()))

    // ── Map Layers ───────────────────────────────────────────────────────

    @GetMapping("/map/snapshot")
    fun mapSnapshot(): ResponseEntity<Any> {
        val snap = commandBrief.getMapSnapshot()
        return ResponseEntity.ok(mapOf(
            "strikes"           to snap.strikes.map { mapOf("lat" to it.latitude, "lon" to it.longitude) },
            "droneStrikes"      to snap.droneStrikes.map { mapOf("lat" to it.latitude, "lon" to it.longitude) },
            "missileStrikes"    to snap.missileStrikes.map { mapOf("lat" to it.latitude, "lon" to it.longitude) },
            "fireHotspots"      to snap.fireHotspots.map { mapOf("lat" to it.latitude, "lon" to it.longitude) },
            "seismicEvents"     to snap.seismicEvents.map { mapOf("lat" to it.latitude, "lon" to it.longitude) },
            "protests"          to snap.protests.map { mapOf("lat" to it.latitude, "lon" to it.longitude) },
            "maritimeIncidents" to snap.maritimeIncidents.map { mapOf("lat" to it.latitude, "lon" to it.longitude) },
            "totalIncidents"    to snap.allIncidents.size
        ))
    }

    // ── Strike Tracks ────────────────────────────────────────────────────

    @GetMapping("/strikes/active")
    fun activeStrikes() = ResponseEntity.ok(mapOf(
        "tracks" to eventBus.activeStrikeTracks().map { t ->
            mapOf(
                "id" to t.id, "weaponType" to t.weaponType.name,
                "category" to t.category.name, "status" to t.status.name,
                "originLat" to t.originLat, "originLon" to t.originLon,
                "targetLat" to t.targetLat, "targetLon" to t.targetLon,
                "currentLat" to t.currentLat, "currentLon" to t.currentLon,
                "altitudeM" to t.altitudeM, "speedKmh" to t.speedKmh,
                "heading" to t.heading, "completionFraction" to t.completionFraction,
                "detectedAt" to t.detectedAt.toString(),
                "estimatedImpactAt" to t.estimatedImpactAt?.toString(),
                "confidence" to t.confidence, "dataQuality" to t.dataQuality.name,
                "threatLevel" to t.threatLevel.name,
                "targetProvince" to t.targetProvince?.name,
                "originRegion" to t.originRegion, "narrative" to t.narrative
            )
        },
        "total" to eventBus.activeStrikeTracks().size
    ))

    @GetMapping("/strikes/timeline")
    fun strikeTimeline(@RequestParam(defaultValue = "50") limit: Int) =
        ResponseEntity.ok(mapOf("tracks" to eventBus.allStrikeTracks(limit).map { t ->
            mapOf("id" to t.id, "category" to t.category.name, "status" to t.status.name,
                  "weaponType" to t.weaponType.name, "detectedAt" to t.detectedAt.toString(),
                  "narrative" to t.narrative, "confidence" to t.confidence,
                  "dataQuality" to t.dataQuality.name, "threatLevel" to t.threatLevel.name)
        }))

    // ── Pipeline Health ──────────────────────────────────────────────────

    @GetMapping("/health")
    fun health() = ResponseEntity.ok(mapOf(
        "status"      to "UP",
        "cycleCount"  to orchestrator.cycleCount(),
        "agentHealth" to orchestrator.agentHealth(),
        "dedupSize"   to eventBus.dedupSize(),
        "totalEvents" to eventBus.allEvents().size,
        "totalIncidents" to eventBus.allIncidents().size
    ))

    // ── Live Airspace (Agent 7 — OpenSky Network ADS-B) ─────────────────

    /**
     * Returns live aircraft state vectors for the Iran + surrounding region.
     * Sourced from OpenSky Network ADS-B feed, refreshed every 30 s by Agent 7.
     * Each aircraft object includes: icao24, callsign, country, lat/lon, altitude,
     * speed, heading, squawk, category, alertLevel, alertReason, positionSource.
     */
    @GetMapping("/airspace/live")
    fun airspaceLive(): ResponseEntity<Any> {
        val tracks = eventBus.liveAircraftTracks()
        return ResponseEntity.ok(mapOf(
            "aircraft" to tracks.map { a ->
                mapOf(
                    "icao24"         to a.icao24,
                    "callsign"       to (a.callsign ?: ""),
                    "country"        to a.originCountry,
                    "lat"            to a.latitude,
                    "lon"            to a.longitude,
                    "altM"           to a.baroAltitudeM,
                    "altFt"          to a.altitudeFt.toLong(),
                    "speedKmh"       to a.speedKmh,
                    "velocityMs"     to (a.velocityMs ?: 0.0),
                    "vertRateMs"     to (a.verticalRateMs ?: 0.0),
                    "heading"        to (a.heading ?: 0.0),
                    "onGround"       to a.onGround,
                    "squawk"         to a.squawk,
                    "category"       to a.category.name,
                    "alertLevel"     to a.alertLevel.name,
                    "alertReason"    to a.alertReason,
                    "positionSource" to a.positionSource,
                    "updatedAt"      to a.updatedAt.toEpochMilli()
                )
            },
            "total"  to eventBus.aircraftTrackCount(),
            "alerts" to eventBus.alertAircraftTracks().size
        ))
    }

    // ── Naval Fleet (Agent 8 — Naval Fleet Monitor) ──────────────────────

    /**
     * Returns OSINT-derived naval vessel positions for US Navy and allied ships
     * operating in the CENTCOM / EUCOM theatre.
     * Data source: OSINT_SYNTHETIC — city-level resolution, not classified.
     */
    @GetMapping("/naval/fleet")
    fun navalFleet(): ResponseEntity<Any> {
        val vessels = eventBus.liveNavalVessels()
        return ResponseEntity.ok(mapOf(
            "vessels" to vessels.map { v ->
                mapOf(
                    "mmsi"            to v.mmsi,
                    "name"            to v.name,
                    "hullNumber"      to v.hullNumber,
                    "vesselClass"     to v.vesselClass.name,
                    "classIcon"       to v.classIcon,
                    "flag"            to v.flag,
                    "lat"             to v.latitude,
                    "lon"             to v.longitude,
                    "speedKnots"      to v.speedKnots,
                    "speedKmh"        to v.speedKmh,
                    "courseTrue"      to v.courseTrue,
                    "heading"         to v.heading,
                    "navStatus"       to v.navStatus,
                    "operationalArea" to v.operationalArea,
                    "alertLevel"      to v.alertLevel.name,
                    "alertReason"     to v.alertReason,
                    "dataSource"      to v.dataSource,
                    "updatedAt"       to v.updatedAt.toString()
                )
            },
            "total"         to eventBus.navalVesselCount(),
            "highReadiness" to vessels.count { it.alertLevel == com.aiinpm.geospatialcommandcenter.model.NavalAlertLevel.HIGH_READINESS }
        ))
    }

    // ── Missile Launches (Agent 9 — Missile Launch Monitor) ─────────────────

    /**
     * Returns active Iranian missile launch events.
     * Each entry includes launch site (province-level), weapon system, target region,
     * current interpolated position, flight phase, and data quality.
     * SYNTHETIC_DRILL entries are clearly labelled — treat SINGLE_SOURCE with caution.
     */
    @GetMapping("/missiles/live")
    fun missilesLive(): ResponseEntity<Any> {
        val launches = eventBus.activeMissileLaunches()
        return ResponseEntity.ok(mapOf(
            "launches" to launches.map { m ->
                mapOf(
                    "id"                to m.id,
                    "weaponSystem"      to m.weaponSystem,
                    "weaponType"        to m.weaponType.name,
                    "launchSiteName"    to m.launchSiteName,
                    "launchProvince"    to m.launchProvince,
                    "launchLat"         to m.launchLat,
                    "launchLon"         to m.launchLon,
                    "targetRegion"      to m.targetRegion,
                    "targetLat"         to m.targetLat,
                    "targetLon"         to m.targetLon,
                    "currentLat"        to m.currentLat,
                    "currentLon"        to m.currentLon,
                    "altitudeKm"        to m.altitudeKm,
                    "rangeKm"           to m.rangeKm,
                    "speedKmh"          to m.speedKmh,
                    "completionFraction" to m.completionFraction,
                    "launchStatus"      to m.launchStatus.name,
                    "launchDetectedAt"  to m.launchDetectedAt.toString(),
                    "estimatedImpactAt" to m.estimatedImpactAt?.toString(),
                    "confidence"        to m.confidence,
                    "dataQuality"       to m.dataQuality.name,
                    "narrative"         to m.narrative,
                    "sourceUrls"        to m.sourceUrls
                )
            },
            "total"  to launches.size,
            "active" to launches.count { it.launchStatus.name != "IMPACT" && it.launchStatus.name != "INTERCEPTED" }
        ))
    }

    // ── Safeguard: Red-Team Drill (register recycled hash) ───────────────

    @PostMapping("/redteam/recycled-hash")
    fun registerRecycledHash(@RequestBody body: Map<String, String>): ResponseEntity<Any> {
        val hash = body["hash"] ?: return ResponseEntity.badRequest().body(mapOf("error" to "missing hash"))
        validator.registerRecycledHash(hash)
        return ResponseEntity.ok(mapOf("registered" to hash))
    }
}


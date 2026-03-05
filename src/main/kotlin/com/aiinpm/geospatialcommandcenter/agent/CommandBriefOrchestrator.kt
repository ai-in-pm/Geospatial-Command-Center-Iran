package com.aiinpm.geospatialcommandcenter.agent

import com.aiinpm.geospatialcommandcenter.bus.EventBus
import com.aiinpm.geospatialcommandcenter.config.GccProperties
import com.aiinpm.geospatialcommandcenter.model.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Agent 5 — Command Brief & Map Orchestrator
 *
 * Dual-lane reasoning model:
 *   • Reactive lane  — low-latency alerts when high-confidence signals arrive
 *   • Planning lane  — deeper narrative synthesis for periodic SITREPs
 *
 * Delivers:
 *   - Live map layer snapshots (incidents by type / severity / province)
 *   - Threshold + anomaly alerts (FLASH / IMMEDIATE / PRIORITY / ROUTINE)
 *   - Auto-briefs every 30 / 60 / 180 minutes
 */
@Component
class CommandBriefOrchestrator(
    private val eventBus: EventBus,
    private val props: GccProperties
) : GccAgent {

    override val agentName   = "Command Brief & Map Orchestrator"
    override val agentNumber = 5
    private val log = LoggerFactory.getLogger(CommandBriefOrchestrator::class.java)

    val alerts   = CopyOnWriteArrayList<CopAlert>()
    val sitreps  = CopyOnWriteArrayList<Sitrep>()
    val mapLayers = MapLayerSnapshot()

    private var lastSitrepAt  = Instant.EPOCH
    private var cycleCount    = 0

    override suspend fun executeCycle(): Int {
        val incidents = eventBus.drainIncidents()
        if (incidents.isEmpty()) { cycleCount++; return 0 }

        // ── Reactive Lane: immediate alert assessment ──
        val newAlerts = mutableListOf<CopAlert>()
        for (incident in incidents) {
            updateMapLayers(incident)
            val alert = assessAlert(incident)
            if (alert != null) { alerts.add(alert); newAlerts.add(alert) }
        }

        // ── Strike Watch: FLASH alerts for INBOUND drone/missile tracks ──
        val inboundStrikes = eventBus.activeStrikeTracks()
            .filter { it.status == com.aiinpm.geospatialcommandcenter.model.StrikeStatus.INBOUND && it.confidence >= 0.4 }
        for (track in inboundStrikes) {
            val strikeAlert = CopAlert(
                priority   = AlertPriority.FLASH,
                incidentId = track.id,
                province   = track.targetProvince,
                message    = "⚠ INBOUND ${track.category.name}: ${track.weaponType.name.replace('_',' ')} — " +
                    "${track.originRegion} → ${track.targetProvince?.name?.replace('_',' ') ?: "unknown target"} " +
                    "ETA: ${track.estimatedImpactAt?.let { java.time.Duration.between(java.time.Instant.now(), it).toMinutes() } ?: "?"}min " +
                    "[conf=${"%.0f".format(track.confidence * 100)}%]",
                confidence = track.confidence,
                severity   = track.threatLevel,
                issuedAt   = java.time.Instant.now()
            )
            // De-duplicate: only add if no same-track alert in last 2 min
            val recentDup = alerts.any { it.incidentId == track.id &&
                java.time.Duration.between(it.issuedAt, java.time.Instant.now()).toMinutes() < 2 }
            if (!recentDup) { alerts.add(strikeAlert); newAlerts.add(strikeAlert) }
        }

        // ── Anomaly Detection: spike by province ──
        val provinceCounts = incidents.groupBy { it.province }.mapValues { it.value.size }
        for ((province, count) in provinceCounts) {
            if (count >= 3 && province != null) {
                val spikeAlert = CopAlert(
                    priority  = AlertPriority.PRIORITY,
                    incidentId = null,
                    province  = province,
                    message   = "Activity spike: $count incidents in ${province.name.replace("_"," ")} this cycle",
                    confidence = incidents.filter { it.province == province }.map { it.mergedConfidence }.average(),
                    severity  = SeverityLevel.MODERATE,
                    issuedAt  = Instant.now()
                )
                alerts.add(spikeAlert); newAlerts.add(spikeAlert)
            }
        }

        // ── Planning Lane: SITREP generation ──
        cycleCount++
        val minutesSinceLastSitrep = (Instant.now().epochSecond - lastSitrepAt.epochSecond) / 60
        val sitrepIntervalMinutes  = props.agent5.sitrep.intervalMinutes
        if (minutesSinceLastSitrep >= sitrepIntervalMinutes || lastSitrepAt == Instant.EPOCH) {
            val sitrep = generateSitrep(incidents)
            sitreps.add(sitrep)
            lastSitrepAt = Instant.now()
            log.info("[Agent5] SITREP #{} issued — {} incidents, {} alerts", sitreps.size, incidents.size, newAlerts.size)
        }

        if (newAlerts.isNotEmpty()) {
            newAlerts.forEach { log.warn("[Agent5] {} ALERT: {}", it.priority, it.message) }
        }
        return incidents.size
    }

    private fun assessAlert(incident: Incident): CopAlert? {
        val cfg = props.agent5.alert
        val threshConf = props.pipeline.alertConfidenceThreshold
        val threshSev  = SeverityLevel.valueOf(props.pipeline.alertSeverityThreshold)

        if (incident.mergedConfidence < threshConf) return null
        if (incident.severity.ordinal < threshSev.ordinal) return null

        val priority = when {
            incident.mergedConfidence >= cfg.flashConfidence && incident.severity == SeverityLevel.valueOf(cfg.flashSeverity) -> AlertPriority.FLASH
            incident.severity == SeverityLevel.CRITICAL                                                                       -> AlertPriority.IMMEDIATE
            incident.severity == SeverityLevel.HIGH                                                                           -> AlertPriority.PRIORITY
            else                                                                                                               -> AlertPriority.ROUTINE
        }

        return CopAlert(
            priority   = priority,
            incidentId = incident.id,
            province   = incident.province,
            message    = "[${priority}] ${incident.narrative} Confidence: ${"%.0f".format(incident.mergedConfidence * 100)}%",
            confidence = incident.mergedConfidence,
            severity   = incident.severity,
            issuedAt   = Instant.now()
        )
    }

    private fun updateMapLayers(incident: Incident) {
        mapLayers.apply {
            when (incident.primaryType) {
                EventType.THERMAL_ANOMALY  -> fireHotspots.add(incident.centroid)
                EventType.SEISMIC_EVENT    -> seismicEvents.add(incident.centroid)
                EventType.PROTEST, EventType.RIOT -> protests.add(incident.centroid)
                EventType.MARITIME_INCIDENT -> maritimeIncidents.add(incident.centroid)
                EventType.DRONE_STRIKE     -> droneStrikes.add(incident.centroid)
                EventType.MISSILE_LAUNCH   -> missileStrikes.add(incident.centroid)
                else                       -> strikes.add(incident.centroid)
            }
            allIncidents.add(incident)
        }
    }

    private fun generateSitrep(newIncidents: List<Incident>): Sitrep {
        val all = eventBus.allIncidents()
        val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.of("UTC"))
        val high = all.filter { it.severity.ordinal >= SeverityLevel.HIGH.ordinal }
        val byProvince = all.groupBy { it.province }.entries
            .sortedByDescending { it.value.size }
            .take(5)
            .joinToString("\n  ") { (p, evts) -> "${p?.name?.replace("_"," ") ?: "Unknown"}: ${evts.size} incidents" }

        val changed = newIncidents.sortedByDescending { it.mergedConfidence }.take(3)
            .joinToString("\n  ") { "• ${it.narrative} [conf=${"%.0f".format(it.mergedConfidence*100)}%, ${it.severity}]" }

        val openUncertainties = buildList {
            if (all.count { it.mergedDeceptionRisk > 0.4 } > 0) add("${all.count { it.mergedDeceptionRisk > 0.4 }} events with elevated deception risk")
            if (all.count { it.mergedConfidence < 0.5 } > 3) add("Multiple low-confidence events unresolved")
        }.joinToString("; ").ifBlank { "None identified" }

        val body = """
═══════════════════════════════════════════════════════
  GEOSPATIAL COMMAND CENTER — SITUATION REPORT
  Generated: ${fmt.format(Instant.now())} UTC  |  Cycle: $cycleCount
═══════════════════════════════════════════════════════
WHAT CHANGED (last interval):
  $changed

HIGH-PRIORITY INCIDENTS TOTAL: ${high.size}

TOP ACTIVE PROVINCES:
  $byProvince

OPEN UNCERTAINTIES:
  $openUncertainties

ALERTS ISSUED THIS SESSION: ${alerts.size}
═══════════════════════════════════════════════════════
        """.trimIndent()

        log.info("\n$body")
        return Sitrep(body = body, issuedAt = Instant.now(), incidentCount = all.size, highSeverityCount = high.size)
    }

    fun recentAlerts(limit: Int = 20): List<CopAlert> = alerts.sortedByDescending { it.issuedAt }.take(limit)
    fun latestSitrep(): Sitrep? = sitreps.lastOrNull()
    fun getMapSnapshot(): MapLayerSnapshot = mapLayers
}

data class CopAlert(
    val priority: AlertPriority, val incidentId: String?, val province: IranProvince?,
    val message: String, val confidence: Double, val severity: SeverityLevel, val issuedAt: Instant
)

data class Sitrep(val body: String, val issuedAt: Instant, val incidentCount: Int, val highSeverityCount: Int)

class MapLayerSnapshot {
    val allIncidents      = CopyOnWriteArrayList<Incident>()
    val strikes           = CopyOnWriteArrayList<GeoPoint>()
    val fireHotspots      = CopyOnWriteArrayList<GeoPoint>()
    val seismicEvents     = CopyOnWriteArrayList<GeoPoint>()
    val protests          = CopyOnWriteArrayList<GeoPoint>()
    val maritimeIncidents = CopyOnWriteArrayList<GeoPoint>()
    /** Drone-strike confirmed impact/event points from the OSINT incident pipeline. */
    val droneStrikes      = CopyOnWriteArrayList<GeoPoint>()
    /** Missile-strike confirmed impact/event points from the OSINT incident pipeline. */
    val missileStrikes    = CopyOnWriteArrayList<GeoPoint>()
}


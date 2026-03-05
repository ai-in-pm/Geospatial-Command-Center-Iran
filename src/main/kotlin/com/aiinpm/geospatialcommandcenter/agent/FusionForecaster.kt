package com.aiinpm.geospatialcommandcenter.agent

import com.aiinpm.geospatialcommandcenter.bus.EventBus
import com.aiinpm.geospatialcommandcenter.config.GccProperties
import com.aiinpm.geospatialcommandcenter.model.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.max

/**
 * Agent 4 — Fusion & Forecast
 *
 * Merges validated events into a coherent operational picture (COP):
 *   - Spatiotemporal DBSCAN-style clustering (rolling window)
 *   - Actor–Action–Location–Time event graph linkage
 *   - Severity scoring (casualties + infrastructure + spread + escalation)
 *   - Incident creation and publication to Agent 5
 */
@Component
class FusionForecaster(
    private val eventBus: EventBus,
    private val props: GccProperties
) : GccAgent {

    override val agentName   = "Fusion & Forecast"
    override val agentNumber = 4
    private val log = LoggerFactory.getLogger(FusionForecaster::class.java)

    /** Rolling event buffer for clustering — keyed by event id. */
    private val rollingBuffer = java.util.concurrent.ConcurrentHashMap<String, Event>()

    override suspend fun executeCycle(): Int {
        val events = eventBus.drainValidatedEvents()
        if (events.isEmpty()) return 0

        // Add to rolling buffer; expire old events
        val cutoff = Instant.now().minus(props.agent4.cluster.timeWindowMinutes, ChronoUnit.MINUTES)
        rollingBuffer.entries.removeIf { it.value.timeStart.isBefore(cutoff) }
        events.forEach { rollingBuffer[it.id] = it }

        // Cluster and fuse
        val incidents = cluster(rollingBuffer.values.toList())
        incidents.forEach { eventBus.publishIncident(it) }

        log.info("[Agent4] Produced {} incidents from {} events in rolling buffer", incidents.size, rollingBuffer.size)
        return incidents.size
    }

    /**
     * Greedy spatiotemporal DBSCAN approximation.
     * Groups events within [epsKm] distance AND [timeWindowMinutes] time window.
     */
    private fun cluster(events: List<Event>): List<Incident> {
        val cfg     = props.agent4.cluster
        val epsKm   = cfg.epsKm
        val visited = mutableSetOf<String>()
        val clusters = mutableListOf<List<Event>>()

        for (seed in events) {
            if (seed.id in visited) continue
            val cluster = mutableListOf(seed)
            visited.add(seed.id)
            for (candidate in events) {
                if (candidate.id in visited) continue
                val distOk = seed.geometry.distanceTo(candidate.geometry) <= epsKm
                val timeOk = abs(ChronoUnit.MINUTES.between(seed.timeStart, candidate.timeStart)) <= cfg.timeWindowMinutes
                if (distOk && timeOk) { cluster.add(candidate); visited.add(candidate.id) }
            }
            if (cluster.size >= cfg.minPoints) clusters.add(cluster)
        }

        return clusters.map { fuseCluster(it) }
    }

    private fun fuseCluster(events: List<Event>): Incident {
        val cfg = props.agent4.severity

        // Centroid = weighted mean by confidence
        val totalConf = events.sumOf { it.confidence }.coerceAtLeast(1e-9)
        val centLat   = events.sumOf { it.geometry.latitude  * it.confidence } / totalConf
        val centLon   = events.sumOf { it.geometry.longitude * it.confidence } / totalConf
        val centroid  = GeoPoint(centLat, centLon).throttle(GeoPoint.ResolutionLevel.CITY)

        val timeStart = events.minOf { it.timeStart }
        val timeEnd   = events.maxOf { it.timeStart }

        // Merged confidence = weighted harmonic-ish mean discounted by deception
        val mergedConf = events.map { it.confidence * (1 - it.deceptionRisk * 0.5) }.average()
        val mergedDeception = events.map { it.deceptionRisk }.average()

        // Primary type = most frequent
        val primaryType = events.groupingBy { it.type }.eachCount().maxByOrNull { it.value }!!.key
        val secondaryTypes = events.map { it.type }.toSet() - primaryType

        // Actors = union, deduplicated
        val actors = events.flatMap { it.actors }.distinct()

        // Province = most common
        val province = events.mapNotNull { it.province }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key

        // Geographic spread
        val maxDist = if (events.size > 1)
            events.flatMap { a -> events.map { b -> a.geometry.distanceTo(b.geometry) } }.maxOrNull() ?: 0.0
        else 0.0

        // Escalation indicators
        val escalation = buildEscalationIndicators(events)

        // Severity score
        val casualtyScore = events.mapNotNull { it.severityScore }.average().let { if (it.isNaN()) 0.0 else it }
        val spreadScore   = (maxDist / 500.0).coerceIn(0.0, 1.0)  // normalise to 500 km
        val escalationScore = (escalation.size / 5.0).coerceIn(0.0, 1.0)
        val infraScore    = if (events.any { infraKeywords.any { kw -> it.narrative.contains(kw, ignoreCase = true) } }) 0.8 else 0.2

        val severityScore = (
            casualtyScore  * cfg.casualtyWeight +
            infraScore     * cfg.infraWeight +
            spreadScore    * cfg.spreadWeight +
            escalationScore * cfg.escalationWeight
        ).coerceIn(0.0, 1.0)

        val severityIdx = (severityScore * 4).toInt().coerceIn(0, SeverityLevel.entries.size - 1)
        val severity = SeverityLevel.entries[severityIdx]

        val narrative = buildNarrative(events, primaryType, province, actors)

        val auditEntry = AuditEntry(
            agent   = agentName, action = "FUSED",
            details = "cluster_size=${events.size} spread=${maxDist.toInt()}km severity=$severity"
        )

        return Incident(
            centroid              = centroid,
            eventIds              = events.map { it.id },
            timeWindowStart       = timeStart,
            timeWindowEnd         = timeEnd,
            primaryType           = primaryType,
            secondaryTypes        = secondaryTypes,
            actors                = actors,
            mergedConfidence      = mergedConf,
            mergedDeceptionRisk   = mergedDeception,
            severity              = severity,
            severityScore         = severityScore,
            geographicSpreadKm    = maxDist,
            escalationIndicators  = escalation,
            province              = province,
            narrative             = narrative,
            auditTrail            = listOf(auditEntry),
            infrastructureImpact  = if (infraScore > 0.5) "Possible infrastructure damage detected" else null
        )
    }

    private fun buildEscalationIndicators(events: List<Event>): List<String> {
        val indicators = mutableListOf<String>()
        val types = events.map { it.type }.toSet()
        if (EventType.MISSILE_LAUNCH in types || EventType.AIRSTRIKE in types) indicators.add("Air/missile campaign")
        if (EventType.DRONE_STRIKE in types) indicators.add("Drone engagement")
        if (EventType.SEISMIC_EVENT in types && events.count { it.type == EventType.SEISMIC_EVENT } > 1) indicators.add("Multiple seismic events (possible blast signatures)")
        if (events.size > 5) indicators.add("High-frequency event clustering")
        if (EventType.MARITIME_INCIDENT in types) indicators.add("Maritime domain activation")
        return indicators
    }

    private fun buildNarrative(events: List<Event>, type: EventType, province: IranProvince?, actors: List<String>): String {
        val loc = province?.name?.replace("_", " ") ?: "Iran"
        val count = events.size
        val actorStr = if (actors.isNotEmpty()) " Actors: ${actors.take(3).joinToString(", ")}." else ""
        return "$count correlated ${type.name.lowercase().replace("_", " ")} event(s) near $loc.$actorStr"
    }

    companion object {
        private val infraKeywords = listOf("pipeline","refinery","power plant","grid","dam",
            "bridge","port","airport","nuclear","base","facility")
    }
}


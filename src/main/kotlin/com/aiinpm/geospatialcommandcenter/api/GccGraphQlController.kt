package com.aiinpm.geospatialcommandcenter.api

import com.aiinpm.geospatialcommandcenter.agent.CommandBriefOrchestrator
import com.aiinpm.geospatialcommandcenter.bus.EventBus
import com.aiinpm.geospatialcommandcenter.pipeline.PipelineOrchestrator
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.stereotype.Controller

/**
 * GraphQL query resolvers — read-only COP interrogation.
 */
@Controller
class GccGraphQlController(
    private val eventBus: EventBus,
    private val commandBrief: CommandBriefOrchestrator,
    private val orchestrator: PipelineOrchestrator
) {

    @QueryMapping
    fun recentEvents(@Argument limit: Int?): List<Map<String, Any?>> =
        eventBus.recentEvents(limit ?: 100).map { e ->
            mapOf(
                "id"             to e.id,
                "latitude"       to e.geometry.latitude,
                "longitude"      to e.geometry.longitude,
                "timeStart"      to e.timeStart.toString(),
                "timeEnd"        to e.timeEnd?.toString(),
                "type"           to e.type.name,
                "confidence"     to e.confidence,
                "deceptionRisk"  to e.deceptionRisk,
                "severity"       to e.severity.name,
                "province"       to e.province?.name,
                "narrative"      to e.narrative,
                "pipelineStatus" to e.pipelineStatus.name,
                "sourceCount"    to e.sources.size
            )
        }

    @QueryMapping
    fun recentIncidents(@Argument limit: Int?): List<Map<String, Any?>> =
        eventBus.recentIncidents(limit ?: 50).map { i ->
            mapOf(
                "id"                    to i.id,
                "latitude"              to i.centroid.latitude,
                "longitude"             to i.centroid.longitude,
                "timeWindowStart"       to i.timeWindowStart.toString(),
                "timeWindowEnd"         to i.timeWindowEnd.toString(),
                "primaryType"           to i.primaryType.name,
                "severity"              to i.severity.name,
                "severityScore"         to i.severityScore,
                "mergedConfidence"      to i.mergedConfidence,
                "mergedDeceptionRisk"   to i.mergedDeceptionRisk,
                "geographicSpreadKm"    to i.geographicSpreadKm,
                "province"              to i.province?.name,
                "narrative"             to i.narrative,
                "eventCount"            to i.eventIds.size,
                "actors"                to i.actors,
                "escalationIndicators"  to i.escalationIndicators,
                "infrastructureImpact"  to i.infrastructureImpact
            )
        }

    @QueryMapping
    fun allAlerts(@Argument limit: Int?): List<Map<String, Any?>> =
        commandBrief.recentAlerts(limit ?: 20).map { a ->
            mapOf(
                "priority"   to a.priority.name,
                "incidentId" to a.incidentId,
                "province"   to a.province?.name,
                "message"    to a.message,
                "confidence" to a.confidence,
                "severity"   to a.severity.name,
                "issuedAt"   to a.issuedAt.toString()
            )
        }

    @QueryMapping
    fun latestSitrep(): Map<String, Any?>? = commandBrief.latestSitrep()?.let { s ->
        mapOf("body" to s.body, "issuedAt" to s.issuedAt.toString(),
              "incidentCount" to s.incidentCount, "highSeverityCount" to s.highSeverityCount)
    }

    @QueryMapping
    fun mapSnapshot(): Map<String, Any?> {
        val snap = commandBrief.getMapSnapshot()
        return mapOf(
            "strikes"          to snap.strikes.map { mapOf("lat" to it.latitude, "lon" to it.longitude) },
            "fireHotspots"     to snap.fireHotspots.map { mapOf("lat" to it.latitude, "lon" to it.longitude) },
            "seismicEvents"    to snap.seismicEvents.map { mapOf("lat" to it.latitude, "lon" to it.longitude) },
            "protests"         to snap.protests.map { mapOf("lat" to it.latitude, "lon" to it.longitude) },
            "maritimeIncidents" to snap.maritimeIncidents.map { mapOf("lat" to it.latitude, "lon" to it.longitude) },
            "totalIncidents"   to snap.allIncidents.size
        )
    }

    @QueryMapping
    fun agentHealth(): List<Map<String, Any>> =
        orchestrator.agentHealth().map { (name, healthy) -> mapOf("name" to name, "healthy" to healthy) }

    @QueryMapping
    fun pipelineCycleCount(): Long = orchestrator.cycleCount()

    @QueryMapping
    fun activeStrikes(): List<Map<String, Any?>> =
        eventBus.activeStrikeTracks().map { strikeToMap(it) }

    @QueryMapping
    fun strikeTimeline(@Argument limit: Int?): List<Map<String, Any?>> =
        eventBus.allStrikeTracks(limit ?: 50).map { strikeToMap(it) }

    private fun strikeToMap(t: com.aiinpm.geospatialcommandcenter.model.StrikeTrack): Map<String, Any?> =
        mapOf(
            "id"                 to t.id,
            "weaponType"         to t.weaponType.name,
            "category"           to t.category.name,
            "status"             to t.status.name,
            "originLat"          to t.originLat,
            "originLon"          to t.originLon,
            "targetLat"          to t.targetLat,
            "targetLon"          to t.targetLon,
            "currentLat"         to t.currentLat,
            "currentLon"         to t.currentLon,
            "altitudeM"          to t.altitudeM,
            "speedKmh"           to t.speedKmh,
            "heading"            to t.heading,
            "completionFraction" to t.completionFraction,
            "detectedAt"         to t.detectedAt.toString(),
            "estimatedImpactAt"  to t.estimatedImpactAt?.toString(),
            "confidence"         to t.confidence,
            "dataQuality"        to t.dataQuality.name,
            "threatLevel"        to t.threatLevel.name,
            "targetProvince"     to t.targetProvince?.name,
            "originRegion"       to t.originRegion,
            "narrative"          to t.narrative
        )
}


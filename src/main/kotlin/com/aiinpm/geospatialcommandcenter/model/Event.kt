package com.aiinpm.geospatialcommandcenter.model

import java.time.Instant
import java.util.UUID

/**
 * Canonical Event — the shared schema across all five agents.
 *
 * Event {
 *   id, geometry(point/polygon), time_start, time_end, type, actors[],
 *   sources[], media_refs[], confidence, deception_risk, severity,
 *   narrative, audit_trail[]
 * }
 */
data class Event(
    val id: String = UUID.randomUUID().toString(),
    val geometry: GeoPoint,
    val boundingPolygon: List<GeoPoint>? = null,
    val timeStart: Instant,
    val timeEnd: Instant? = null,
    val type: EventType,
    val actors: List<String> = emptyList(),
    val sources: List<EventSource> = emptyList(),
    val mediaRefs: List<MediaReference> = emptyList(),
    val confidence: Double = 0.0,
    val deceptionRisk: Double = 0.0,
    val severity: SeverityLevel = SeverityLevel.MINIMAL,
    val severityScore: Double = 0.0,
    val narrative: String = "",
    val auditTrail: List<AuditEntry> = emptyList(),
    val pipelineStatus: PipelineStatus = PipelineStatus.INGESTED,
    val province: IranProvince? = null,
    val tags: Set<String> = emptySet(),
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
)

data class EventSource(
    val sourceType: SourceType,
    val sourceId: String,
    val url: String? = null,
    val retrievedAt: Instant = Instant.now(),
    val credibilityScore: Double = 0.5
)

data class MediaReference(
    val url: String,
    val type: MediaType,
    val perceptualHash: String? = null,
    val exifTimestamp: Instant? = null,
    val exifLocation: GeoPoint? = null,
    val knownRecycled: Boolean = false
) {
    enum class MediaType { IMAGE, VIDEO, AUDIO, DOCUMENT }
}

data class AuditEntry(
    val timestamp: Instant = Instant.now(),
    val agent: String,
    val action: String,
    val details: String,
    val previousConfidence: Double? = null,
    val newConfidence: Double? = null
)


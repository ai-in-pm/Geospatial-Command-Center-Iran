package com.aiinpm.geospatialcommandcenter.model

import java.time.Instant
import java.util.UUID

/**
 * An Incident is a fused cluster of correlated Events produced by Agent 4.
 * Represents a coherent real-world occurrence (e.g., a single strike reported by 5 sources).
 */
data class Incident(
    val id: String = UUID.randomUUID().toString(),
    val centroid: GeoPoint,
    val eventIds: List<String> = emptyList(),
    val timeWindowStart: Instant,
    val timeWindowEnd: Instant,
    val primaryType: EventType,
    val secondaryTypes: Set<EventType> = emptySet(),
    val actors: List<String> = emptyList(),
    val mergedConfidence: Double = 0.0,
    val mergedDeceptionRisk: Double = 0.0,
    val severity: SeverityLevel = SeverityLevel.MINIMAL,
    val severityScore: Double = 0.0,
    val casualtyEstimate: CasualtyEstimate? = null,
    val infrastructureImpact: String? = null,
    val geographicSpreadKm: Double = 0.0,
    val escalationIndicators: List<String> = emptyList(),
    val narrative: String = "",
    val province: IranProvince? = null,
    val auditTrail: List<AuditEntry> = emptyList(),
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
)

data class CasualtyEstimate(
    val killed: IntRange? = null,
    val injured: IntRange? = null,
    val displaced: IntRange? = null,
    val source: String = "aggregated",
    val credibility: Double = 0.0
)


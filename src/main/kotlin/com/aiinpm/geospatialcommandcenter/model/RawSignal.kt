package com.aiinpm.geospatialcommandcenter.model

import java.time.Instant
import java.util.UUID

/**
 * Raw signal from any OSINT source, pre-validation.
 * Produced by Agent 1 (Pulse Ingestor) and consumed by Agent 2.
 */
data class RawSignal(
    val id: String = UUID.randomUUID().toString(),
    val sourceType: SourceType,
    val sourceId: String,
    val sourceUrl: String? = null,
    val rawText: String? = null,
    val rawJson: String? = null,
    val mediaUrls: List<String> = emptyList(),
    val candidateLatitude: Double? = null,
    val candidateLongitude: Double? = null,
    val candidateLocationName: String? = null,
    val candidateTimestamp: Instant? = null,
    val ingestedAt: Instant = Instant.now(),
    val perceptualHash: String? = null,
    val textEmbeddingHash: String? = null,
    val metadata: Map<String, String> = emptyMap()
) {
    /** Check whether this signal has minimum viable location information. */
    fun hasLocationHint(): Boolean =
        (candidateLatitude != null && candidateLongitude != null) ||
        !candidateLocationName.isNullOrBlank()
}


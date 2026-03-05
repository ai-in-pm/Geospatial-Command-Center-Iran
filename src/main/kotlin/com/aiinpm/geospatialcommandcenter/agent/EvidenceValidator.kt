package com.aiinpm.geospatialcommandcenter.agent

import com.aiinpm.geospatialcommandcenter.bus.EventBus
import com.aiinpm.geospatialcommandcenter.config.GccProperties
import com.aiinpm.geospatialcommandcenter.model.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Agent 3 — Evidence Validator
 *
 * Prevents bad intel from contaminating the map.
 * Applies:
 *   1. Cross-source corroboration scoring
 *   2. Media forensics flags (recycled content detection)
 *   3. Temporal sanity checks
 *   4. Deception-risk scoring
 *
 * Outputs confidence ∈ [0,1] and deception_risk ∈ [0,1].
 * Events below the confidence floor or above deception threshold
 * are REJECTED or FLAGGED.
 */
@Component
class EvidenceValidator(
    private val eventBus: EventBus,
    private val props: GccProperties
) : GccAgent {

    override val agentName   = "Evidence Validator"
    override val agentNumber = 3
    private val log = LoggerFactory.getLogger(EvidenceValidator::class.java)

    /** Known recycled-media perceptual hashes (populated by red-team drills). */
    private val recycledHashes = mutableSetOf<String>()

    /** Sliding window: eventId → list of source types that reported it (for corroboration). */
    private val corroborationWindow = java.util.concurrent.ConcurrentHashMap<String, MutableSet<SourceType>>()

    override suspend fun executeCycle(): Int {
        val candidates = eventBus.drainCandidateEvents()
        if (candidates.isEmpty()) return 0
        var passed = 0; var rejected = 0
        for (event in candidates) {
            val (validated, status) = validate(event)
            when (status) {
                PipelineStatus.VALIDATED -> { eventBus.publishValidatedEvent(validated); passed++ }
                PipelineStatus.REJECTED  -> { log.debug("[Agent3] REJECTED {}: conf={}", event.id, validated.confidence); rejected++ }
                else                     -> { eventBus.publishValidatedEvent(validated); passed++ }  // FLAGGED passes with warning
            }
        }
        log.info("[Agent3] Validated {}, rejected {} of {} candidates", passed, rejected, candidates.size)
        return passed
    }

    private fun validate(event: Event): Pair<Event, PipelineStatus> {
        val cfg = props.agent3

        // 1. Source credibility baseline
        val avgCredibility = event.sources.map { it.credibilityScore }.average().takeIf { !it.isNaN() } ?: 0.4

        // 2. Cross-source corroboration: find events with close geo/time match
        val corrobCount = countCorroboration(event)
        val corrobBonus = when {
            corrobCount >= 4 -> 0.30
            corrobCount >= 3 -> 0.20
            corrobCount >= 2 -> 0.10
            else             -> 0.0
        }

        // 3. Multi-source diversity bonus (FIRMS + GDELT + ACLED = strong)
        val sourceTypes = event.sources.map { it.sourceType }.toSet()
        val diversityBonus = when {
            sourceTypes.size >= 3 -> 0.15
            sourceTypes.size == 2 -> 0.08
            else                  -> 0.0
        }

        // 4. Media forensics — check for recycled hashes
        val hasRecycledMedia = event.mediaRefs.any { it.knownRecycled || recycledHashes.contains(it.perceptualHash) }
        val mediaDeceptionRisk = if (hasRecycledMedia) 0.70 else 0.0

        // 5. Temporal sanity — event shouldn't be older than 30 days when ingested
        val ageHours = ChronoUnit.HOURS.between(event.timeStart, Instant.now())
        val temporalPenalty = when {
            ageHours > 720 -> 0.30  // >30 days: heavy penalty
            ageHours > 168 -> 0.15  // >7 days
            ageHours > 48  -> 0.05  // >2 days
            else           -> 0.0
        }

        // 6. USGS/FIRMS are sensor-grade — trust floor raised
        val sensorBonus = if (sourceTypes.any { it == SourceType.USGS || it == SourceType.NASA_FIRMS }) 0.20 else 0.0

        val rawConfidence = (avgCredibility + corrobBonus + diversityBonus + sensorBonus - temporalPenalty)
            .coerceIn(cfg.confidenceFloor, 1.0)

        val deceptionRisk = (mediaDeceptionRisk + (if (corrobCount == 0 && !sourceTypes.contains(SourceType.NASA_FIRMS) && !sourceTypes.contains(SourceType.USGS)) 0.25 else 0.0))
            .coerceIn(0.0, 1.0)

        val whatWouldChangeMind = buildWwcm(corrobCount, hasRecycledMedia, sourceTypes)

        val auditEntry = AuditEntry(
            agent  = agentName, action = "SCORED",
            details = "conf=${"%.2f".format(rawConfidence)} deception=${"%.2f".format(deceptionRisk)} corrobSources=$corrobCount wwcm=$whatWouldChangeMind",
            previousConfidence = event.confidence, newConfidence = rawConfidence
        )

        val newStatus = when {
            deceptionRisk >= cfg.deceptionHighThreshold -> PipelineStatus.FLAGGED_DECEPTION
            rawConfidence < cfg.confidenceFloor         -> PipelineStatus.REJECTED
            else                                        -> PipelineStatus.VALIDATED
        }

        val validated = event.copy(
            confidence     = rawConfidence,
            deceptionRisk  = deceptionRisk,
            pipelineStatus = newStatus,
            auditTrail     = event.auditTrail + auditEntry,
            updatedAt      = Instant.now()
        )
        return validated to newStatus
    }

    private fun countCorroboration(event: Event): Int {
        val key = "${event.type}:${event.province}:${event.timeStart.truncatedTo(ChronoUnit.HOURS)}"
        val bucket = corroborationWindow.getOrPut(key) { mutableSetOf() }
        event.sources.forEach { bucket.add(it.sourceType) }
        return bucket.size
    }

    private fun buildWwcm(corrobCount: Int, recycled: Boolean, types: Set<SourceType>): String {
        val items = mutableListOf<String>()
        if (corrobCount < 2) items.add("independent second-source confirmation")
        if (recycled) items.add("verified-original media with valid EXIF")
        if (!types.contains(SourceType.NASA_FIRMS) && !types.contains(SourceType.USGS))
            items.add("sensor-corroborated evidence (FIRMS/USGS)")
        return if (items.isEmpty()) "none — high confidence" else items.joinToString("; ")
    }

    /** Register a known recycled perceptual hash (called by red-team drills). */
    fun registerRecycledHash(hash: String) { recycledHashes.add(hash) }
}


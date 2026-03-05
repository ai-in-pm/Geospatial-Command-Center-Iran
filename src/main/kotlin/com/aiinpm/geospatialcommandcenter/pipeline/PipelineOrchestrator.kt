package com.aiinpm.geospatialcommandcenter.pipeline

import com.aiinpm.geospatialcommandcenter.agent.*
import com.aiinpm.geospatialcommandcenter.bus.EventBus
import com.aiinpm.geospatialcommandcenter.config.GccProperties
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicLong

/**
 * Pipeline Orchestrator — Control Loop
 *
 * Runs the canonical 5-agent loop every [gcc.pipeline.interval-seconds] seconds:
 *
 *   loop every Δt:
 *     Agent1: pull GDELT + ACLED + FIRMS + USGS → RawSignal → bus
 *     Agent2: drain RawSignals → geo-resolve → CandidateEvent → bus
 *     Agent3: drain CandidateEvents → validate/score → ValidatedEvent → bus
 *     Agent4: drain ValidatedEvents → cluster/fuse → Incident → bus
 *     Agent5: drain Incidents → alerts + map update + SITREP
 *   end loop
 *
 * Safeguards enforced:
 *   - Resolution throttling (city-level by default)
 *   - No person-level tracking
 *   - All map pins auditable to source
 *   - Dedup window cleared every 6 hours
 */
@Component
class PipelineOrchestrator(
    private val agent1: PulseIngestor,
    private val agent2: GeoTemporalResolver,
    private val agent3: EvidenceValidator,
    private val agent4: FusionForecaster,
    private val agent5: CommandBriefOrchestrator,
    private val agent6: StrikeWatchAgent,         // manages its own 15 s schedule
    private val agent7: AirspaceMonitorAgent,     // manages its own 30 s schedule
    private val agent8: NavalFleetAgent,          // manages its own 60 s schedule
    private val eventBus: EventBus,
    private val props: GccProperties
) {
    private val log        = LoggerFactory.getLogger(PipelineOrchestrator::class.java)
    private val cycleCount = AtomicLong(0)
    private var lastDedupClear = System.currentTimeMillis()

    @Scheduled(fixedDelayString = "\${gcc.pipeline.interval-seconds:60}000",
               initialDelayString = "5000")
    fun runCycle() = runBlocking {
        val cycle = cycleCount.incrementAndGet()
        val t0    = System.currentTimeMillis()
        log.info("══ Pipeline cycle #{} START ══", cycle)

        try {
            // ── Stage 1: Ingest ──────────────────────────────────────
            val ingested = safeRun("Agent1", agent1)

            // ── Stage 2: Geo-Resolve ─────────────────────────────────
            val resolved = safeRun("Agent2", agent2)

            // ── Stage 3: Validate ────────────────────────────────────
            val validated = safeRun("Agent3", agent3)

            // ── Stage 4: Fuse ────────────────────────────────────────
            val fused = safeRun("Agent4", agent4)

            // ── Stage 5: Brief & Map ─────────────────────────────────
            val briefed = safeRun("Agent5", agent5)

            val elapsed = System.currentTimeMillis() - t0
            log.info("══ Pipeline cycle #{} DONE in {}ms | ingested={} resolved={} validated={} fused={} briefed={} ══",
                cycle, elapsed, ingested, resolved, validated, fused, briefed)

            // ── Maintenance: clear dedup window every 6 hours ────────
            val now = System.currentTimeMillis()
            if (now - lastDedupClear > 6 * 3600_000L) {
                eventBus.clearDedupOlderThan(6 * 3600_000L)
                lastDedupClear = now
                log.info("Dedup registry cleared; remaining size={}", eventBus.dedupSize())
            }
        } catch (e: Exception) {
            log.error("Pipeline cycle #{} FAILED: {}", cycle, e.message, e)
        }
    }

    private suspend fun safeRun(name: String, agent: GccAgent): Int = try {
        if (!agent.isHealthy()) { log.warn("{} is not healthy — skipping", name); 0 }
        else agent.executeCycle()
    } catch (e: Exception) {
        log.error("{} threw exception: {}", name, e.message, e)
        0
    }

    fun cycleCount(): Long = cycleCount.get()
    fun agentHealth(): Map<String, Boolean> = mapOf(
        agent1.agentName to agent1.isHealthy(),
        agent2.agentName to agent2.isHealthy(),
        agent3.agentName to agent3.isHealthy(),
        agent4.agentName to agent4.isHealthy(),
        agent5.agentName to agent5.isHealthy(),
        agent6.agentName to agent6.isHealthy(),
        agent7.agentName to agent7.isHealthy(),
        agent8.agentName to agent8.isHealthy()
    )
}


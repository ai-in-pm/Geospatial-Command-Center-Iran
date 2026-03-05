package com.aiinpm.geospatialcommandcenter.agent

/**
 * Common contract for all five Geospatial Command Center agents.
 */
interface GccAgent {

    /** Human-readable name, e.g. "Pulse Ingestor". */
    val agentName: String

    /** Pipeline position 1–5. */
    val agentNumber: Int

    /**
     * Execute one pipeline cycle.
     * Called every Δt seconds by the PipelineOrchestrator.
     * @return number of items processed this cycle.
     */
    suspend fun executeCycle(): Int

    /** Health-check — returns true when the agent is operational. */
    fun isHealthy(): Boolean = true
}


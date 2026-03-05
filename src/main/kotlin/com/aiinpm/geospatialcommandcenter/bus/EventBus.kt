package com.aiinpm.geospatialcommandcenter.bus

import com.aiinpm.geospatialcommandcenter.model.AircraftAlertLevel
import com.aiinpm.geospatialcommandcenter.model.AircraftTrack
import com.aiinpm.geospatialcommandcenter.model.Event
import com.aiinpm.geospatialcommandcenter.model.Incident
import com.aiinpm.geospatialcommandcenter.model.MissileLaunch
import com.aiinpm.geospatialcommandcenter.model.MissileLaunchStatus
import com.aiinpm.geospatialcommandcenter.model.NavalVessel
import com.aiinpm.geospatialcommandcenter.model.RawSignal
import com.aiinpm.geospatialcommandcenter.model.StrikeStatus
import com.aiinpm.geospatialcommandcenter.model.StrikeTrack
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList

/**
 * In-process event bus connecting all five agents.
 * Thread-safe queues for each pipeline stage.
 */
@Component
class EventBus {

    private val log = LoggerFactory.getLogger(EventBus::class.java)

    private val rawSignalQueue      = ConcurrentLinkedQueue<RawSignal>()
    private val candidateEventQueue = ConcurrentLinkedQueue<Event>()
    private val validatedEventQueue = ConcurrentLinkedQueue<Event>()
    private val incidentQueue       = ConcurrentLinkedQueue<Incident>()

    /** Dedup registry: "sourceType:sourceId" → ingest epoch-ms. */
    private val dedupRegistry = ConcurrentHashMap<String, Long>()

    private val eventStore    = CopyOnWriteArrayList<Event>()
    private val incidentStore = CopyOnWriteArrayList<Incident>()

    /** Strike track store: id → latest StrikeTrack snapshot. */
    private val strikeTrackStore = ConcurrentHashMap<String, StrikeTrack>()

    // ── Agent 1 → Agent 2 ──

    fun publishRawSignal(signal: RawSignal): Boolean {
        val key = "${signal.sourceType}:${signal.sourceId}"
        if (dedupRegistry.putIfAbsent(key, signal.ingestedAt.toEpochMilli()) != null) {
            log.trace("Dup suppressed: {}", key)
            return false
        }
        rawSignalQueue.offer(signal)
        return true
    }

    fun drainRawSignals(max: Int = 500): List<RawSignal> = drain(rawSignalQueue, max)

    // ── Agent 2 → Agent 3 ──

    fun publishCandidateEvent(event: Event) { candidateEventQueue.offer(event) }
    fun drainCandidateEvents(max: Int = 500): List<Event> = drain(candidateEventQueue, max)

    // ── Agent 3 → Agent 4 ──

    fun publishValidatedEvent(event: Event) {
        validatedEventQueue.offer(event)
        eventStore.add(event)
    }
    fun drainValidatedEvents(max: Int = 500): List<Event> = drain(validatedEventQueue, max)

    // ── Agent 4 → Agent 5 ──

    fun publishIncident(incident: Incident) {
        incidentQueue.offer(incident)
        incidentStore.add(incident)
    }
    fun drainIncidents(max: Int = 500): List<Incident> = drain(incidentQueue, max)

    // ── Read-only stores ──

    fun allEvents(): List<Event>     = eventStore.toList()
    fun allIncidents(): List<Incident> = incidentStore.toList()

    fun recentEvents(limit: Int = 100): List<Event> =
        eventStore.sortedByDescending { it.createdAt }.take(limit)
    fun recentIncidents(limit: Int = 50): List<Incident> =
        incidentStore.sortedByDescending { it.createdAt }.take(limit)

    // ── Strike Track Store ──

    /** Insert or replace a strike track by id. */
    fun upsertStrikeTrack(track: StrikeTrack) { strikeTrackStore[track.id] = track }

    /** All tracks currently TRACKED or INBOUND. */
    fun activeStrikeTracks(): List<StrikeTrack> =
        strikeTrackStore.values
            .filter { it.status == StrikeStatus.TRACKED || it.status == StrikeStatus.INBOUND }
            .sortedByDescending { it.threatLevel.ordinal }

    /** All tracks (active + resolved), newest first. */
    fun allStrikeTracks(limit: Int = 100): List<StrikeTrack> =
        strikeTrackStore.values.sortedByDescending { it.detectedAt }.take(limit)

    /**
     * Remove tracks that have reached terminal status (IMPACT / INTERCEPTED / ABORTED)
     * and whose [updatedAt] is older than [olderThanSeconds].
     */
    fun expireOldStrikeTracks(olderThanSeconds: Long = 1800) {
        val cutoff = Instant.now().minusSeconds(olderThanSeconds)
        val terminal = setOf(StrikeStatus.IMPACT, StrikeStatus.INTERCEPTED, StrikeStatus.ABORTED)
        strikeTrackStore.entries.removeIf { it.value.status in terminal && it.value.updatedAt.isBefore(cutoff) }
    }

    // ── Aircraft Track Store (Agent 7 — Airspace Monitor) ──────────────

    /** Live ADS-B aircraft state vectors keyed by ICAO 24-bit address. */
    private val aircraftTrackStore = ConcurrentHashMap<String, AircraftTrack>()

    /** Insert or replace a live aircraft track. */
    fun upsertAircraftTrack(track: AircraftTrack) { aircraftTrackStore[track.icao24] = track }

    /** All current airborne aircraft tracks, sorted highest alert first. */
    fun liveAircraftTracks(): List<AircraftTrack> =
        aircraftTrackStore.values
            .sortedByDescending { it.alertLevel.ordinal }

    /** Only tracks with alertLevel above ROUTINE (WATCH, WARNING, CRITICAL). */
    fun alertAircraftTracks(): List<AircraftTrack> =
        aircraftTrackStore.values.filter { it.alertLevel != AircraftAlertLevel.ROUTINE }

    /** Total number of aircraft currently in the track store. */
    fun aircraftTrackCount(): Int = aircraftTrackStore.size

    /**
     * Evict stale aircraft tracks not updated within [olderThanSeconds].
     * Default 90 s — OpenSky state vectors age out quickly.
     */
    fun expireOldAircraftTracks(olderThanSeconds: Long = 90) {
        val cutoff = Instant.now().minusSeconds(olderThanSeconds)
        aircraftTrackStore.entries.removeIf { it.value.updatedAt.isBefore(cutoff) }
    }

    // ── Naval Vessel Store (Agent 8 — Naval Fleet Monitor) ──────────────────

    /** Live naval vessel tracks keyed by MMSI. */
    private val navalVesselStore = ConcurrentHashMap<String, NavalVessel>()

    /** Insert or replace a naval vessel track. */
    fun upsertNavalVessel(vessel: NavalVessel) { navalVesselStore[vessel.mmsi] = vessel }

    /** All tracked naval vessels, highest alert level first. */
    fun liveNavalVessels(): List<NavalVessel> =
        navalVesselStore.values.sortedByDescending { it.alertLevel.ordinal }

    /** Total number of naval vessels currently in the track store. */
    fun navalVesselCount(): Int = navalVesselStore.size

    /**
     * Evict stale naval vessel tracks not updated within [olderThanSeconds].
     * Default 180 s — OSINT synthetic positions refresh every 60 s.
     */
    fun expireOldNavalVessels(olderThanSeconds: Long = 180) {
        val cutoff = Instant.now().minusSeconds(olderThanSeconds)
        navalVesselStore.entries.removeIf { it.value.updatedAt.isBefore(cutoff) }
    }

    // ── Missile Launch Store (Agent 9 — Missile Launch Monitor) ────────────────

    /** Live Iranian missile launch events keyed by launch id. */
    private val missileLaunchStore = ConcurrentHashMap<String, MissileLaunch>()

    /** Insert or replace a missile launch event. */
    fun upsertMissileLaunch(launch: MissileLaunch) { missileLaunchStore[launch.id] = launch }

    /** All launches currently active (not yet IMPACT / INTERCEPTED / ABORTED). */
    fun activeMissileLaunches(): List<MissileLaunch> =
        missileLaunchStore.values
            .filter { it.launchStatus !in setOf(
                MissileLaunchStatus.IMPACT, MissileLaunchStatus.INTERCEPTED, MissileLaunchStatus.ABORTED
            )}
            .sortedByDescending { it.launchDetectedAt }

    /** All launches including terminal/resolved, newest first. */
    fun allMissileLaunches(limit: Int = 50): List<MissileLaunch> =
        missileLaunchStore.values.sortedByDescending { it.launchDetectedAt }.take(limit)

    /** Total number of missile launches currently tracked. */
    fun missileLaunchCount(): Int = missileLaunchStore.size

    /**
     * Evict resolved missile launch events older than [olderThanSeconds].
     * Default 30 min — keeps the map clean after impact/intercept.
     */
    fun expireOldMissileLaunches(olderThanSeconds: Long = 1800) {
        val cutoff   = Instant.now().minusSeconds(olderThanSeconds)
        val terminal = setOf(MissileLaunchStatus.IMPACT, MissileLaunchStatus.INTERCEPTED, MissileLaunchStatus.ABORTED)
        missileLaunchStore.entries.removeIf { it.value.launchStatus in terminal && it.value.updatedAt.isBefore(cutoff) }
    }

    fun dedupSize(): Int = dedupRegistry.size
    fun clearDedupOlderThan(ageMs: Long) {
        val cutoff = System.currentTimeMillis() - ageMs
        dedupRegistry.entries.removeIf { it.value < cutoff }
    }

    private fun <T> drain(queue: ConcurrentLinkedQueue<T>, max: Int): List<T> {
        val batch = ArrayList<T>(minOf(max, 64))
        repeat(max) { queue.poll()?.let { batch.add(it) } ?: return batch }
        return batch
    }
}


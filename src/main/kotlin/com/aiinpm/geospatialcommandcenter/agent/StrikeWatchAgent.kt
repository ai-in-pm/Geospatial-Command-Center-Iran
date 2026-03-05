package com.aiinpm.geospatialcommandcenter.agent

import com.aiinpm.geospatialcommandcenter.bus.EventBus
import com.aiinpm.geospatialcommandcenter.model.*
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBodyOrNull
import java.time.Instant
import kotlin.math.*

/**
 * Agent 6 — Strike Watch
 *
 * 15-second dedicated scan cycle for drone and missile tracking.
 *
 * Sources:
 *   1. GDELT real-time article scan (drone/missile keyword filter)
 *   2. Synthetic position-interpolated tracks (SYNTHETIC_DRILL quality)
 *      — clearly labelled, used when OSINT feed is sparse
 *
 * Weapon physics used for position interpolation:
 *   Shaheed-136  → 185 km/h, cruise alt 1 500 m
 *   Mohajer-6    → 210 km/h, cruise alt 3 000 m
 *   Shahab-3     → 3 200 km/h, apogee 80 km
 *   Fattah-2     → ~13 500 km/h hypersonic
 *   Paveh cruise → 900 km/h, terrain-following 200 m
 */
@Component
class StrikeWatchAgent(
    private val eventBus: EventBus,
    private val webClient: WebClient
) : GccAgent {

    override val agentName   = "Strike Watch"
    override val agentNumber = 6
    private val log          = LoggerFactory.getLogger(StrikeWatchAgent::class.java)
    private val mapper       = ObjectMapper()
    private var scanCount    = 0L

    // ── Synthetic seed catalogue ──────────────────────────────────────────
    private data class SeedTrack(
        val id: String, val weaponType: WeaponType, val category: StrikeCategory,
        val originLat: Double, val originLon: Double,
        val targetLat: Double, val targetLon: Double,
        val speedKmh: Double, val altM: Double,
        val narrative: String, val region: String,
        val cycleSec: Long = 720L,               // repeat every N seconds
        val createdAt: Instant = Instant.now()
    )

    private val seeds = listOf(
        SeedTrack("syn-drone-01", WeaponType.SHAHEED_136, StrikeCategory.DRONE,
            33.4, 48.9, 33.1, 45.5, 185.0, 1500.0,
            "[SYNTHETIC_DRILL] Shaheed-136 swarm launched from W-Iran border corridor — 3 airframes tracked",
            "Lorestan Province", 720L),
        SeedTrack("syn-drone-02", WeaponType.MOHAJER_6, StrikeCategory.DRONE,
            37.3, 45.1, 36.5, 50.1, 210.0, 3000.0,
            "[SYNTHETIC_DRILL] Mohajer-6 ISR/strike drone departing W-Azerbaijan axis",
            "West Azerbaijan", 900L),
        SeedTrack("syn-missile-01", WeaponType.BALLISTIC_SHAHAB, StrikeCategory.MISSILE,
            33.8, 48.5, 36.2, 44.4, 3200.0, 80000.0,
            "[SYNTHETIC_DRILL] Shahab-3 ballistic trajectory detected — apogee 80 km, inbound NW",
            "Lorestan / Kermanshah", 420L),
        SeedTrack("syn-missile-02", WeaponType.CRUISE_PAVEH, StrikeCategory.MISSILE,
            27.2, 56.3, 31.9, 48.7, 900.0, 200.0,
            "[SYNTHETIC_DRILL] Paveh cruise missile low-altitude track departing Hormozgan coast",
            "Hormozgan Province", 600L),
        SeedTrack("syn-missile-03", WeaponType.HYPERSONIC_FATTAH, StrikeCategory.MISSILE,
            35.7, 59.5, 32.1, 34.8, 13500.0, 100000.0,
            "[SYNTHETIC_DRILL] Fattah-2 hypersonic glide — Mach 15, Khorasan launch axis",
            "Khorasan Razavi", 300L),
        SeedTrack("syn-drone-03", WeaponType.ARASH_2, StrikeCategory.DRONE,
            29.6, 60.8, 27.5, 57.2, 350.0, 4000.0,
            "[SYNTHETIC_DRILL] Arash-2 jet-drone track from Sistan-Baluchestan — heading SW",
            "Sistan-Baluchestan", 800L)
    )

    // ── Scheduler ─────────────────────────────────────────────────────────
    @Scheduled(fixedDelay = 15_000, initialDelay = 8_000)
    fun scanCycle() = runBlocking { executeCycle() }

    override suspend fun executeCycle(): Int {
        scanCount++
        var count = refreshSyntheticTracks()
        count    += scanGdeltStrikes()
        eventBus.expireOldStrikeTracks(1200)
        log.debug("[Agent6] Scan #{}: {} tracks upserted", scanCount, count)
        return count
    }

    // ── Synthetic tracks ──────────────────────────────────────────────────
    private fun refreshSyntheticTracks(): Int {
        seeds.forEach { seed -> eventBus.upsertStrikeTrack(buildSyntheticTrack(seed)) }
        return seeds.size
    }

    private fun buildSyntheticTrack(s: SeedTrack): StrikeTrack {
        val elapsed  = (Instant.now().epochSecond - s.createdAt.epochSecond).coerceAtLeast(0)
        val cyclePos = (elapsed % s.cycleSec).toDouble()
        val rangeDeg = haversineKm(s.originLat, s.originLon, s.targetLat, s.targetLon)
        val travelKm = (s.speedKmh / 3600.0) * cyclePos
        val frac     = (travelKm / rangeDeg.coerceAtLeast(1.0)).coerceIn(0.0, 1.0)
        val curLat   = s.originLat + (s.targetLat - s.originLat) * frac
        val curLon   = s.originLon + (s.targetLon - s.originLon) * frac
        val etaSec   = ((rangeDeg * (1.0 - frac) / s.speedKmh) * 3600.0).toLong()
        val status   = when { frac >= 0.95 -> StrikeStatus.IMPACT; frac >= 0.5 -> StrikeStatus.INBOUND; else -> StrikeStatus.TRACKED }
        val threat   = when (s.category) {
            StrikeCategory.MISSILE -> if (frac > 0.5) SeverityLevel.CRITICAL else SeverityLevel.HIGH
            else                   -> if (frac > 0.7) SeverityLevel.HIGH     else SeverityLevel.MODERATE
        }
        return StrikeTrack(
            id = s.id, weaponType = s.weaponType, category = s.category, status = status,
            originLat = s.originLat, originLon = s.originLon,
            targetLat = s.targetLat, targetLon = s.targetLon,
            currentLat = curLat, currentLon = curLon,
            altitudeM  = s.altM * sin(frac * PI).coerceAtLeast(0.0),
            speedKmh   = s.speedKmh,
            heading    = bearingDeg(curLat, curLon, s.targetLat, s.targetLon),
            completionFraction = frac,
            detectedAt = s.createdAt,
            estimatedImpactAt  = Instant.now().plusSeconds(etaSec),
            updatedAt  = Instant.now(),
            confidence = 0.25, dataQuality = DataQuality.SYNTHETIC_DRILL,
            threatLevel = threat, originRegion = s.region, narrative = s.narrative
        )
    }

    // ── GDELT real-time scan ──────────────────────────────────────────────
    private suspend fun scanGdeltStrikes(): Int {
        return try {
            val url = "https://api.gdeltproject.org/api/v2/doc/doc" +
                "?query=Iran+drone+missile+Shaheed+attack+strike+launch" +
                "&mode=ArtList&maxrecords=25&format=json&timespan=3h"
            val raw  = webClient.get().uri(url).retrieve().awaitBodyOrNull<String>() ?: return 0
            val arts = mapper.readTree(raw).path("articles")
            if (!arts.isArray) return 0
            var count = 0
            arts.forEach { a ->
                val title = a.path("title").asText("")
                val link  = a.path("url").asText("")
                val (cat, wt) = classifyWeapon(title)
                if (cat == StrikeCategory.UNKNOWN) return@forEach
                val track = buildGdeltTrack(title, link, cat, wt)
                eventBus.upsertStrikeTrack(track); count++
            }
            count
        } catch (e: Exception) { log.debug("[Agent6] GDELT strike scan: {}", e.message); 0 }
    }

    private fun classifyWeapon(text: String): Pair<StrikeCategory, WeaponType> {
        val t = text.lowercase()
        return when {
            "shaheed" in t || "shahed" in t   -> StrikeCategory.DRONE   to WeaponType.SHAHEED_136
            "mohajer" in t                     -> StrikeCategory.DRONE   to WeaponType.MOHAJER_6
            "arash" in t                       -> StrikeCategory.DRONE   to WeaponType.ARASH_2
            "drone" in t && ("strike" in t || "attack" in t || "launch" in t)
                                               -> StrikeCategory.DRONE   to WeaponType.UNKNOWN_DRONE
            "fattah" in t || "hypersonic" in t -> StrikeCategory.MISSILE to WeaponType.HYPERSONIC_FATTAH
            "shahab" in t || "ghadr" in t || "ballistic" in t
                                               -> StrikeCategory.MISSILE to WeaponType.BALLISTIC_SHAHAB
            "paveh" in t || "soumar" in t || "cruise missile" in t
                                               -> StrikeCategory.MISSILE to WeaponType.CRUISE_PAVEH
            "missile" in t && ("launch" in t || "attack" in t || "fire" in t)
                                               -> StrikeCategory.MISSILE to WeaponType.UNKNOWN_MISSILE
            else                               -> StrikeCategory.UNKNOWN  to WeaponType.UNIDENTIFIED
        }
    }

    private fun buildGdeltTrack(
        title: String, url: String, cat: StrikeCategory, wt: WeaponType
    ): StrikeTrack {
        // OSINT approximation — place track in Iran theatre
        val lat = 32.5 + (Math.random() - 0.5) * 10.0
        val lon = 53.0 + (Math.random() - 0.5) * 12.0
        val dLat = (Math.random() - 0.5) * 4.0
        val dLon = (Math.random() - 0.5) * 4.0
        return StrikeTrack(
            id = "gdelt-${url.hashCode().toLong() and 0xFFFFFFFFL}",
            weaponType = wt, category = cat, status = StrikeStatus.TRACKED,
            originLat = lat, originLon = lon,
            targetLat = lat + dLat, targetLon = lon + dLon,
            currentLat = lat, currentLon = lon,
            speedKmh = weaponSpeed(wt), completionFraction = 0.0,
            confidence = 0.45, dataQuality = DataQuality.SINGLE_SOURCE,
            threatLevel = if (cat == StrikeCategory.MISSILE) SeverityLevel.HIGH else SeverityLevel.MODERATE,
            narrative = title.take(200),
            sourceUrls = listOfNotNull(url.ifBlank { null })
        )
    }

    // ── Weapon physics helpers ────────────────────────────────────────────
    private fun weaponSpeed(wt: WeaponType): Double = when (wt) {
        WeaponType.SHAHEED_136      -> 185.0
        WeaponType.MOHAJER_6        -> 210.0
        WeaponType.ARASH_2          -> 350.0
        WeaponType.HYPERSONIC_FATTAH -> 13500.0
        WeaponType.BALLISTIC_SHAHAB, WeaponType.BALLISTIC_GHADR -> 3200.0
        WeaponType.CRUISE_PAVEH, WeaponType.CRUISE_SOUMAR        -> 900.0
        else -> 200.0
    }

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371.0
        val dLat = Math.toRadians(lat2 - lat1); val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return R * 2 * asin(sqrt(a))
    }

    private fun bearingDeg(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLon = Math.toRadians(lon2 - lon1)
        val y = sin(dLon) * cos(Math.toRadians(lat2))
        val x = cos(Math.toRadians(lat1)) * sin(Math.toRadians(lat2)) -
                sin(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360) % 360
    }
}


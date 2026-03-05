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
 * Agent 9 — Missile Launch Monitor
 *
 * Dedicated 15-second scan cycle for Iranian ballistic and cruise missile launches.
 *
 * Sources:
 *   1. Known IRGC/IRIAF launch sites seeded at province-level resolution (OSINT-derived)
 *   2. GDELT real-time article scan (Iran missile/launch keyword filter)
 *   3. Synthetic lifecycle progression — launches advance through flight phases each cycle
 *
 * Weapon parameters used for trajectory interpolation:
 *   Shahab-3        → 3 200 km/h, MRBM, range ~2 000 km
 *   Ghadr-110       → 3 500 km/h, IRBM, range ~2 500 km
 *   Fattah-2        → 13 500 km/h, hypersonic, range ~1 500 km
 *   Paveh cruise    → 900 km/h, terrain-following, range ~1 650 km
 *   Soumar cruise   → 900 km/h, strategic, range ~3 000 km
 *   Emad IRBM      → 3 200 km/h, precision-guided, range ~1 700 km
 */
@Component
class MissileLaunchMonitorAgent(
    private val eventBus: EventBus,
    private val webClient: WebClient
) : GccAgent {

    override val agentName   = "Missile Launch Monitor"
    override val agentNumber = 9
    private val log          = LoggerFactory.getLogger(MissileLaunchMonitorAgent::class.java)
    private val mapper       = ObjectMapper()
    private var scanCount    = 0L

    // ── Known IRGC / IRIAF launch sites (OSINT-derived, province-level) ──────
    private data class LaunchSite(
        val name: String, val province: String, val lat: Double, val lon: Double,
        val systems: List<WeaponType>
    )

    private val launchSites = listOf(
        LaunchSite("IRGC Aerospace Command, Khorrambad", "Lorestan", 33.42, 48.31,
            listOf(WeaponType.BALLISTIC_SHAHAB, WeaponType.BALLISTIC_GHADR)),
        LaunchSite("IRGC Ballistic Missile Base, Semnan", "Semnan", 35.57, 53.39,
            listOf(WeaponType.HYPERSONIC_FATTAH, WeaponType.BALLISTIC_SHAHAB)),
        LaunchSite("IRGC Naval Missile Base, Bandar Abbas", "Hormozgan", 27.19, 56.27,
            listOf(WeaponType.CRUISE_PAVEH, WeaponType.CRUISE_SOUMAR)),
        LaunchSite("IRGC Launch Site, Tabriz Axis", "East Azerbaijan", 38.07, 46.29,
            listOf(WeaponType.BALLISTIC_GHADR, WeaponType.BALLISTIC_SHAHAB)),
        LaunchSite("IRGC Aerospace, Ahvaz Corridor", "Khuzestan", 31.32, 48.67,
            listOf(WeaponType.CRUISE_PAVEH, WeaponType.BALLISTIC_SHAHAB)),
        LaunchSite("IRGC Strategic Base, Kerman", "Kerman", 30.22, 57.08,
            listOf(WeaponType.HYPERSONIC_FATTAH, WeaponType.CRUISE_SOUMAR))
    )

    // ── Target regions (OSINT threat-matrix — no targeting data) ─────────────
    private data class TargetRegion(
        val name: String, val lat: Double, val lon: Double
    )

    private val targets = listOf(
        TargetRegion("Israel (Central)", 32.08, 34.78),
        TargetRegion("US Forces, Iraq (Ain al-Asad)", 33.79, 42.44),
        TargetRegion("US Al-Udeid Base, Qatar", 25.12, 51.31),
        TargetRegion("US Forces, Red Sea Theatre", 22.10, 38.50),
        TargetRegion("Saudi Arabia (Riyadh)", 24.68, 46.72),
        TargetRegion("US Base, UAE (Al Dhafra)", 24.24, 54.55)
    )

    // ── Synthetic launch scenarios ────────────────────────────────────────────
    private data class ScenarioSeed(
        val id: String, val siteIndex: Int, val targetIndex: Int,
        val weaponType: WeaponType, val cycleSec: Long,
        val narrative: String, val createdAt: Instant = Instant.now()
    )

    private val scenarios = listOf(
        ScenarioSeed("ml-bal-01", 0, 0, WeaponType.BALLISTIC_SHAHAB, 900L,
            "[SYNTHETIC_DRILL] Shahab-3 MRBM launch detected from Lorestan — inbound Israel axis"),
        ScenarioSeed("ml-hyp-01", 1, 2, WeaponType.HYPERSONIC_FATTAH, 450L,
            "[SYNTHETIC_DRILL] Fattah-2 hypersonic glide vehicle detected from Semnan — Mach 13"),
        ScenarioSeed("ml-cru-01", 2, 1, WeaponType.CRUISE_PAVEH, 3600L,
            "[SYNTHETIC_DRILL] Paveh cruise missile low-altitude track — departing Hormozgan coast"),
        ScenarioSeed("ml-bal-02", 3, 5, WeaponType.BALLISTIC_GHADR, 720L,
            "[SYNTHETIC_DRILL] Ghadr-110 IRBM launch axis — Tabriz corridor, heading SW"),
        ScenarioSeed("ml-cru-02", 4, 3, WeaponType.CRUISE_PAVEH, 2700L,
            "[SYNTHETIC_DRILL] Paveh low-level cruise track — Khuzestan launch, Red Sea vector"),
        ScenarioSeed("ml-hyp-02", 5, 0, WeaponType.HYPERSONIC_FATTAH, 360L,
            "[SYNTHETIC_DRILL] Fattah-2 launch from Kerman Strategic Base — hypersonic glide phase")
    )

    // ── Weapon physics ────────────────────────────────────────────────────────
    private fun speedKmh(wt: WeaponType) = when (wt) {
        WeaponType.HYPERSONIC_FATTAH                               -> 13_500.0
        WeaponType.BALLISTIC_SHAHAB, WeaponType.BALLISTIC_GHADR   -> 3_200.0
        WeaponType.CRUISE_PAVEH, WeaponType.CRUISE_SOUMAR          -> 900.0
        else                                                        -> 1_800.0
    }

    private fun weaponSystem(wt: WeaponType) = when (wt) {
        WeaponType.BALLISTIC_SHAHAB  -> "Shahab-3 MRBM"
        WeaponType.BALLISTIC_GHADR   -> "Ghadr-110 IRBM"
        WeaponType.HYPERSONIC_FATTAH -> "Fattah-2 HGV"
        WeaponType.CRUISE_PAVEH      -> "Paveh Cruise"
        WeaponType.CRUISE_SOUMAR     -> "Soumar Cruise"
        else                         -> "Unknown System"
    }

    // ── Scheduler ─────────────────────────────────────────────────────────────
    @Scheduled(fixedDelay = 15_000, initialDelay = 12_000)
    fun scanCycle() = runBlocking { executeCycle() }

    override suspend fun executeCycle(): Int {
        scanCount++
        val synthetic = refreshSyntheticLaunches()
        val osint     = scanGdeltLaunches()
        eventBus.expireOldMissileLaunches(1800)
        log.debug("[Agent9] Scan #{}: {} synthetic + {} OSINT launches", scanCount, synthetic, osint)
        return synthetic + osint
    }

    // ── Synthetic launch lifecycle ────────────────────────────────────────────
    private fun refreshSyntheticLaunches(): Int {
        scenarios.forEach { s ->
            val site   = launchSites[s.siteIndex]
            val target = targets[s.targetIndex]
            val elapsed = (Instant.now().epochSecond - s.createdAt.epochSecond).coerceAtLeast(0)
            val cycPos  = (elapsed % s.cycleSec).toDouble()
            val speed   = speedKmh(s.weaponType)
            val rangeKm = haversineKm(site.lat, site.lon, target.lat, target.lon)
            val travelKm = (speed / 3600.0) * cycPos
            val frac     = (travelKm / rangeKm.coerceAtLeast(1.0)).coerceIn(0.0, 1.0)
            val curLat   = site.lat + (target.lat - site.lat) * frac
            val curLon   = site.lon + (target.lon - site.lon) * frac
            val altKm    = when (s.weaponType) {
                WeaponType.HYPERSONIC_FATTAH -> 60.0 * sin(frac * PI)
                WeaponType.BALLISTIC_SHAHAB,
                WeaponType.BALLISTIC_GHADR   -> 80.0 * sin(frac * PI)
                else                         -> 0.2  // cruise terrain-following
            }
            val etaSec = ((rangeKm * (1.0 - frac) / speed) * 3600.0).toLong()
            val status = when {
                frac >= 0.95 -> MissileLaunchStatus.IMPACT
                frac >= 0.80 -> MissileLaunchStatus.TERMINAL_PHASE
                frac >= 0.05 -> MissileLaunchStatus.IN_FLIGHT
                else         -> MissileLaunchStatus.LAUNCH_DETECTED
            }
            eventBus.upsertMissileLaunch(MissileLaunch(
                id = s.id, weaponSystem = weaponSystem(s.weaponType), weaponType = s.weaponType,
                launchSiteName = site.name, launchProvince = site.province,
                launchLat = site.lat, launchLon = site.lon,
                targetRegion = target.name, targetLat = target.lat, targetLon = target.lon,
                currentLat = curLat, currentLon = curLon, altitudeKm = altKm,
                rangeKm = rangeKm, speedKmh = speed, completionFraction = frac,
                launchStatus = status,
                launchDetectedAt = s.createdAt,
                estimatedImpactAt = Instant.now().plusSeconds(etaSec),
                updatedAt = Instant.now(),
                confidence = 0.25, dataQuality = DataQuality.SYNTHETIC_DRILL,
                narrative = s.narrative
            ))
        }
        return scenarios.size
    }

    // ── GDELT real-time scan ──────────────────────────────────────────────────
    private suspend fun scanGdeltLaunches(): Int {
        return try {
            val url = "https://api.gdeltproject.org/api/v2/doc/doc" +
                "?query=Iran+missile+launch+Shahab+Ghadr+Fattah+Paveh+IRGC+ballistic+hypersonic+fires" +
                "&mode=ArtList&maxrecords=20&format=json&timespan=6h"
            val raw  = webClient.get().uri(url).retrieve().awaitBodyOrNull<String>() ?: return 0
            val arts = mapper.readTree(raw).path("articles")
            if (!arts.isArray) return 0
            var count = 0
            arts.forEach { a ->
                val title = a.path("title").asText("")
                val link  = a.path("url").asText("")
                val (wt, sys) = classifyFromText(title)
                if (wt == WeaponType.UNIDENTIFIED) return@forEach
                // Place in Iran at province-level — OSINT approximation
                val site = launchSites.random()
                val target = targets.random()
                val rangeKm = haversineKm(site.lat, site.lon, target.lat, target.lon)
                eventBus.upsertMissileLaunch(MissileLaunch(
                    id = "gdelt-ml-${link.hashCode().toLong() and 0xFFFFFFFFL}",
                    weaponSystem = sys, weaponType = wt,
                    launchSiteName = site.name, launchProvince = site.province,
                    launchLat = site.lat, launchLon = site.lon,
                    targetRegion = target.name, targetLat = target.lat, targetLon = target.lon,
                    currentLat = site.lat, currentLon = site.lon,
                    rangeKm = rangeKm, speedKmh = speedKmh(wt),
                    launchStatus = MissileLaunchStatus.LAUNCH_DETECTED,
                    confidence = 0.45, dataQuality = DataQuality.SINGLE_SOURCE,
                    narrative = title.take(200),
                    sourceUrls = listOfNotNull(link.ifBlank { null })
                ))
                count++
            }
            count
        } catch (e: Exception) {
            log.debug("[Agent9] GDELT missile scan: {}", e.message); 0
        }
    }

    private fun classifyFromText(text: String): Pair<WeaponType, String> {
        val t = text.lowercase()
        return when {
            "fattah" in t || "hypersonic" in t -> WeaponType.HYPERSONIC_FATTAH to "Fattah-2 HGV"
            "shahab" in t                      -> WeaponType.BALLISTIC_SHAHAB  to "Shahab-3 MRBM"
            "ghadr" in t || "emad" in t        -> WeaponType.BALLISTIC_GHADR   to "Ghadr-110 IRBM"
            "paveh" in t                       -> WeaponType.CRUISE_PAVEH      to "Paveh Cruise"
            "soumar" in t                      -> WeaponType.CRUISE_SOUMAR     to "Soumar Cruise"
            ("missile" in t || "ballistic" in t) && ("iran" in t || "irgc" in t)
                                               -> WeaponType.BALLISTIC_SHAHAB  to "Unknown IRBM"
            else                               -> WeaponType.UNIDENTIFIED      to "Unknown"
        }
    }

    // ── Geo helpers ───────────────────────────────────────────────────────────
    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371.0
        val dLat = Math.toRadians(lat2 - lat1); val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return R * 2 * asin(sqrt(a))
    }
}


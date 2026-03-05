package com.aiinpm.geospatialcommandcenter.agent

import com.aiinpm.geospatialcommandcenter.bus.EventBus
import com.aiinpm.geospatialcommandcenter.config.GccProperties
import com.aiinpm.geospatialcommandcenter.model.*
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBodyOrNull
import java.time.Instant
import java.time.ZoneId

/**
 * Agent 2 — Geo-Temporal Resolver
 *
 * Consumes RawSignals from Agent 1; performs NER-based geoparsing,
 * geocoding (Nominatim-rate-limited), and temporal normalisation to
 * produce structured Event objects published for Agent 3.
 */
@Component
class GeoTemporalResolver(
    private val eventBus: EventBus,
    private val props: GccProperties,
    private val webClient: WebClient
) : GccAgent {

    override val agentName   = "Geo-Temporal Resolver"
    override val agentNumber = 2
    private val log    = LoggerFactory.getLogger(GeoTemporalResolver::class.java)
    private val mapper = ObjectMapper()

    /** Simple Iran province keyword gazetteer for fast lookup. */
    private val provinceGazetteer: Map<String, IranProvince> = buildGazetteer()

    private var lastGeocode = 0L   // rate-limit tracker

    override suspend fun executeCycle(): Int {
        val signals = eventBus.drainRawSignals()
        if (signals.isEmpty()) return 0
        var resolved = 0
        for (signal in signals) {
            val event = resolveSignal(signal)
            if (event != null) {
                eventBus.publishCandidateEvent(event)
                resolved++
            }
        }
        log.info("[Agent2] Resolved {}/{} signals to geo-events", resolved, signals.size)
        return resolved
    }

    private suspend fun resolveSignal(signal: RawSignal): Event? {
        // 1. Determine coordinates
        val (lat, lon) = when {
            signal.candidateLatitude != null && signal.candidateLongitude != null ->
                signal.candidateLatitude to signal.candidateLongitude
            !signal.candidateLocationName.isNullOrBlank() ->
                geocode(signal.candidateLocationName) ?: return null
            !signal.rawText.isNullOrBlank() -> {
                val loc = extractLocationFromText(signal.rawText) ?: return null
                geocode(loc) ?: return null
            }
            else -> return null
        }

        val geoPoint = GeoPoint(lat, lon).throttle(GeoPoint.ResolutionLevel.CITY)
        if (!geoPoint.isWithinIran() && !isNearIran(lat, lon)) return null

        // 2. Normalise time
        val eventTime = signal.candidateTimestamp ?: signal.ingestedAt

        // 3. Determine event type from source + metadata
        val eventType = inferEventType(signal)

        // 4. Determine province
        val province = inferProvince(signal, lat, lon)

        val source = EventSource(
            sourceType = signal.sourceType,
            sourceId   = signal.sourceId,
            url        = signal.sourceUrl,
            retrievedAt = signal.ingestedAt,
            credibilityScore = sourceCredibility(signal.sourceType)
        )

        return Event(
            geometry  = geoPoint,
            timeStart = eventTime,
            type      = eventType,
            sources   = listOf(source),
            province  = province,
            narrative = signal.rawText ?: "",
            pipelineStatus = PipelineStatus.GEO_RESOLVED,
            auditTrail = listOf(AuditEntry(
                agent   = agentName,
                action  = "GEO_RESOLVED",
                details = "lat=$lat lon=$lon via ${props.agent2.geocoder.provider}"
            ))
        )
    }

    private suspend fun geocode(location: String): Pair<Double, Double>? {
        val rateLimitMs = props.agent2.geocoder.rateLimitMs
        val now = System.currentTimeMillis()
        if (now - lastGeocode < rateLimitMs) {
            kotlinx.coroutines.delay(rateLimitMs - (now - lastGeocode))
        }
        return try {
            val url = "${props.agent2.geocoder.nominatimUrl}" +
                "?q=${java.net.URLEncoder.encode(location, "UTF-8")}&format=json&limit=1&countrycodes=ir"
            lastGeocode = System.currentTimeMillis()
            val json = webClient.get()
                .uri(url).header("User-Agent", "GCC-OSINT/1.0")
                .retrieve().awaitBodyOrNull<String>() ?: return null
            val arr = mapper.readTree(json)
            if (!arr.isArray || arr.size() == 0) return null
            val lat = arr[0].path("lat").asDouble()
            val lon = arr[0].path("lon").asDouble()
            if (lat == 0.0 && lon == 0.0) null else lat to lon
        } catch (e: Exception) {
            log.debug("[Agent2] Geocode failed for '{}': {}", location, e.message)
            null
        }
    }



    // ── Helpers ────────────────────────────────────────────────────────────

    private fun extractLocationFromText(text: String): String? {
        val keywords = listOf("Tehran","Isfahan","Khuzestan","Hormozgan","Bushehr","Kerman",
            "Mashhad","Tabriz","Shiraz","Ahvaz","Bandar Abbas","Zahedan","Kermanshah",
            "Rasht","Qom","Karaj","Ardabil","Yazd","Semnan","Iran","Hormuz","IRGC")
        return keywords.firstOrNull { text.contains(it, ignoreCase = true) }
    }

    private fun inferEventType(signal: RawSignal): EventType {
        val t = ((signal.rawText ?: "") + " " + signal.metadata.values.joinToString(" ")).lowercase()
        return when {
            signal.sourceType == SourceType.NASA_FIRMS -> EventType.THERMAL_ANOMALY
            signal.sourceType == SourceType.USGS       -> EventType.SEISMIC_EVENT
            listOf("airstrike","air strike","bombing").any { t.contains(it) }    -> EventType.AIRSTRIKE
            listOf("missile","ballistic","rocket").any { t.contains(it) }        -> EventType.MISSILE_LAUNCH
            listOf("drone","uav","shahed").any { t.contains(it) }               -> EventType.DRONE_STRIKE
            listOf("artillery","shelling","mortar").any { t.contains(it) }      -> EventType.SHELLING_ARTILLERY
            listOf("protest","demonstration","march").any { t.contains(it) }    -> EventType.PROTEST
            listOf("explosion","blast","boom").any { t.contains(it) }           -> EventType.EXPLOSION_UNKNOWN
            listOf("maritime","tanker","ship","vessel").any { t.contains(it) }  -> EventType.MARITIME_INCIDENT
            listOf("riot","clash","unrest").any { t.contains(it) }             -> EventType.RIOT
            else -> EventType.OTHER
        }
    }

    private fun inferProvince(signal: RawSignal, lat: Double, lon: Double): IranProvince? {
        val text = (signal.candidateLocationName ?: "") + " " + (signal.rawText ?: "")
        provinceGazetteer.entries.firstOrNull { text.contains(it.key, ignoreCase = true) }
            ?.let { return it.value }
        return when {
            lat in 35.0..36.5 && lon in 50.5..52.5 -> IranProvince.TEHRAN
            lat in 29.0..34.0 && lon in 50.0..56.0 -> IranProvince.ISFAHAN
            lat in 29.5..33.5 && lon in 46.0..50.0 -> IranProvince.KHUZESTAN
            else -> null
        }
    }

    private fun sourceCredibility(type: SourceType): Double = when (type) {
        SourceType.USGS            -> 0.95
        SourceType.NASA_FIRMS      -> 0.90
        SourceType.ACLED           -> 0.80
        SourceType.NGO_REPORT      -> 0.75
        SourceType.NEWS_WIRE       -> 0.70
        SourceType.GDELT           -> 0.60
        SourceType.GOVERNMENT_STATEMENT -> 0.55
        else                       -> 0.40
    }

    private fun isNearIran(lat: Double, lon: Double): Boolean =
        lat in 22.0..42.0 && lon in 42.0..66.0

    private fun buildGazetteer(): Map<String, IranProvince> = mapOf(
        "Tehran" to IranProvince.TEHRAN, "Isfahan" to IranProvince.ISFAHAN,
        "Esfahan" to IranProvince.ISFAHAN, "Khuzestan" to IranProvince.KHUZESTAN,
        "Ahvaz" to IranProvince.KHUZESTAN, "Fars" to IranProvince.FARS,
        "Shiraz" to IranProvince.FARS, "Hormozgan" to IranProvince.HORMOZGAN,
        "Bandar Abbas" to IranProvince.HORMOZGAN, "Bushehr" to IranProvince.BUSHEHR,
        "Kurdistan" to IranProvince.KURDISTAN, "Sanandaj" to IranProvince.KURDISTAN,
        "Kermanshah" to IranProvince.KERMANSHAH, "Mashhad" to IranProvince.KHORASAN_RAZAVI,
        "Khorasan" to IranProvince.KHORASAN_RAZAVI, "Sistan" to IranProvince.SISTAN_BALUCHESTAN,
        "Zahedan" to IranProvince.SISTAN_BALUCHESTAN, "Tabriz" to IranProvince.EAST_AZERBAIJAN,
        "Azerbaijan" to IranProvince.EAST_AZERBAIJAN, "Ardabil" to IranProvince.ARDABIL,
        "Gilan" to IranProvince.GUILAN, "Rasht" to IranProvince.GUILAN,
        "Mazandaran" to IranProvince.MAZANDARAN, "Alborz" to IranProvince.ALBORZ,
        "Karaj" to IranProvince.ALBORZ, "Qom" to IranProvince.QOM,
        "Yazd" to IranProvince.YAZD, "Kerman" to IranProvince.KERMAN,
        "Ilam" to IranProvince.ILAM, "Lorestan" to IranProvince.LORESTAN
    )
}
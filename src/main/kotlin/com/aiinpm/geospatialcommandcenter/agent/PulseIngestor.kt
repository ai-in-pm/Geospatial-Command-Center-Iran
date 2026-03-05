package com.aiinpm.geospatialcommandcenter.agent

import com.aiinpm.geospatialcommandcenter.bus.EventBus
import com.aiinpm.geospatialcommandcenter.config.GccProperties
import com.aiinpm.geospatialcommandcenter.model.RawSignal
import com.aiinpm.geospatialcommandcenter.model.SourceType
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBodyOrNull
import java.time.Instant

/**
 * Agent 1 — Pulse Ingestor
 *
 * Pulls GDELT, ACLED, NASA FIRMS, USGS; normalises; deduplicates;
 * publishes RawSignal objects into the EventBus for Agent 2.
 */
@Component
class PulseIngestor(
    private val eventBus: EventBus,
    private val props: GccProperties,
    private val webClient: WebClient
) : GccAgent {

    override val agentName   = "Pulse Ingestor"
    override val agentNumber = 1
    private val log    = LoggerFactory.getLogger(PulseIngestor::class.java)
    private val mapper = ObjectMapper()

    override suspend fun executeCycle(): Int {
        val total = ingestGdelt() + ingestAcled() + ingestFirms() + ingestUsgs()
        log.info("[Agent1] {} new signals ingested this cycle", total)
        return total
    }

    // ── GDELT ─────────────────────────────────────────────────────────────

    private suspend fun ingestGdelt(): Int = try {
        val c   = props.agent1.gdelt
        val url = "${c.baseUrl}?query=${c.query}&mode=ArtList&maxrecords=${c.maxRecords}&format=json"
        val raw = webClient.get().uri(url).retrieve().awaitBodyOrNull<String>() ?: return 0
        parseGdelt(raw).count { eventBus.publishRawSignal(it) }
    } catch (e: Exception) { log.warn("[Agent1] GDELT: {}", e.message); 0 }

    private fun parseGdelt(json: String): List<RawSignal> = try {
        val arts = mapper.readTree(json).path("articles")
        if (!arts.isArray) emptyList()
        else arts.map { a ->
            RawSignal(
                sourceType = SourceType.GDELT,
                sourceId   = "gdelt-${a.path("url").asText("").hashCode()}",
                sourceUrl  = a.path("url").asText("").ifBlank { null },
                rawText    = a.path("title").asText(""),
                candidateLocationName = a.path("sourcecountry").asText("").ifBlank { null },
                candidateTimestamp    = parseTs(a.path("seendate").asText("")),
                metadata   = mapOf("domain" to a.path("domain").asText(""))
            )
        }
    } catch (_: Exception) { emptyList() }

    // ── ACLED ─────────────────────────────────────────────────────────────

    private suspend fun ingestAcled(): Int = try {
        val c   = props.agent1.acled
        val url = "${c.baseUrl}?key=${c.key}&email=${c.email}&country=Iran&limit=200"
        val raw = webClient.get().uri(url).retrieve().awaitBodyOrNull<String>() ?: return 0
        parseAcled(raw).count { eventBus.publishRawSignal(it) }
    } catch (e: Exception) { log.warn("[Agent1] ACLED: {}", e.message); 0 }

    private fun parseAcled(json: String): List<RawSignal> = try {
        val data = mapper.readTree(json).path("data")
        if (!data.isArray) emptyList()
        else data.map { d ->
            val lat = d.path("latitude").asDouble(0.0)
            val lon = d.path("longitude").asDouble(0.0)
            RawSignal(
                sourceType  = SourceType.ACLED,
                sourceId    = "acled-${d.path("data_id").asText("")}",
                rawText     = d.path("notes").asText(""),
                candidateLatitude     = lat.takeIf { it != 0.0 },
                candidateLongitude    = lon.takeIf { it != 0.0 },
                candidateLocationName = d.path("location").asText("").ifBlank { null },
                metadata = mapOf(
                    "event_type"     to d.path("event_type").asText(""),
                    "sub_event_type" to d.path("sub_event_type").asText(""),
                    "actor1"         to d.path("actor1").asText(""),
                    "fatalities"     to d.path("fatalities").asText("0")
                )
            )
        }
    } catch (_: Exception) { emptyList() }

    // ── NASA FIRMS ────────────────────────────────────────────────────────

    private suspend fun ingestFirms(): Int = try {
        val c   = props.agent1.firms
        val url = "${c.baseUrl}/${c.mapKey}/${c.source}/world/1"
        val csv = webClient.get().uri(url).retrieve().awaitBodyOrNull<String>() ?: return 0
        parseFirms(csv).count { eventBus.publishRawSignal(it) }
    } catch (e: Exception) { log.warn("[Agent1] FIRMS: {}", e.message); 0 }

    private fun parseFirms(csv: String): List<RawSignal> {
        val lines = csv.lines(); if (lines.size < 2) return emptyList()
        val h = lines[0].split(",")
        val li = h.indexOf("latitude");  val lo = h.indexOf("longitude")
        val ci = h.indexOf("confidence"); val di = h.indexOf("acq_date")
        val ti = h.indexOf("acq_time");  val fi = h.indexOf("frp")
        return lines.drop(1).mapNotNull { line ->
            val c   = line.split(",")
            val lat = c.getOrNull(li)?.toDoubleOrNull() ?: return@mapNotNull null
            val lon = c.getOrNull(lo)?.toDoubleOrNull() ?: return@mapNotNull null
            if (lat !in 25.0..40.0 || lon !in 44.0..64.0) return@mapNotNull null
            RawSignal(
                sourceType = SourceType.NASA_FIRMS,
                sourceId   = "firms-$lat-$lon-${c.getOrNull(di)}",
                candidateLatitude  = lat, candidateLongitude = lon,
                metadata = mapOf("confidence" to (c.getOrNull(ci) ?: ""),
                    "frp" to (c.getOrNull(fi) ?: ""), "acq_date" to (c.getOrNull(di) ?: ""),
                    "acq_time" to (c.getOrNull(ti) ?: ""))
            )
        }
    }

    // ── USGS Seismic ──────────────────────────────────────────────────────

    private suspend fun ingestUsgs(): Int = try {
        val c   = props.agent1.usgs
        val url = "${c.baseUrl}?format=geojson&minlatitude=${c.minLatitude}" +
            "&maxlatitude=${c.maxLatitude}&minlongitude=${c.minLongitude}" +
            "&maxlongitude=${c.maxLongitude}&minmagnitude=${c.minMagnitude}" +
            "&starttime=${Instant.now().minusSeconds(86400)}&orderby=time&limit=50"
        val raw = webClient.get().uri(url).retrieve().awaitBodyOrNull<String>() ?: return 0
        parseUsgs(raw).count { eventBus.publishRawSignal(it) }
    } catch (e: Exception) { log.warn("[Agent1] USGS: {}", e.message); 0 }

    private fun parseUsgs(json: String): List<RawSignal> = try {
        val features = mapper.readTree(json).path("features")
        if (!features.isArray) emptyList()
        else features.mapNotNull { f ->
            val p      = f.path("properties")
            val coords = f.path("geometry").path("coordinates")
            val lon    = coords.get(0)?.asDouble() ?: return@mapNotNull null
            val lat    = coords.get(1)?.asDouble() ?: return@mapNotNull null
            val mag    = p.path("mag").asDouble(0.0)
            val ts     = p.path("time").asLong(0)
            RawSignal(
                sourceType = SourceType.USGS,
                sourceId   = "usgs-${f.path("id").asText()}",
                sourceUrl  = p.path("url").asText("").ifBlank { null },
                rawText    = "M$mag - ${p.path("place").asText("")}",
                candidateLatitude  = lat, candidateLongitude = lon,
                candidateTimestamp = if (ts > 0) Instant.ofEpochMilli(ts) else null,
                metadata = mapOf("magnitude" to "$mag",
                    "place" to p.path("place").asText(""), "type" to p.path("type").asText(""))
            )
        }
    } catch (_: Exception) { emptyList() }

    private fun parseTs(s: String): Instant? = try {
        Instant.parse(s.replace(" ", "T").trimEnd('Z') + "Z")
    } catch (_: Exception) { null }
}


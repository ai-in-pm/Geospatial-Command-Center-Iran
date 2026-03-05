package com.aiinpm.geospatialcommandcenter.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "gcc")
data class GccProperties(
    val pipeline: PipelineProps = PipelineProps(),
    val agent1: Agent1Props = Agent1Props(),
    val agent2: Agent2Props = Agent2Props(),
    val agent3: Agent3Props = Agent3Props(),
    val agent4: Agent4Props = Agent4Props(),
    val agent5: Agent5Props = Agent5Props()
)

data class PipelineProps(
    val intervalSeconds: Long = 60,
    val alertConfidenceThreshold: Double = 0.6,
    val alertSeverityThreshold: String = "MODERATE"
)

data class Agent1Props(
    val gdelt: GdeltProps = GdeltProps(),
    val acled: AcledProps = AcledProps(),
    val firms: FirmsProps = FirmsProps(),
    val usgs: UsgsProps = UsgsProps()
)

data class GdeltProps(
    val baseUrl: String = "https://api.gdeltproject.org/api/v2/doc/doc",
    val query: String = "Iran conflict strike protest",
    val maxRecords: Int = 250
)

data class AcledProps(
    val baseUrl: String = "https://api.acleddata.com/acled/read",
    val key: String = "demo",
    val email: String = "demo@example.com"
)

data class FirmsProps(
    val baseUrl: String = "https://firms.modaps.eosdis.nasa.gov/api/area/csv",
    val mapKey: String = "DEMO_KEY",
    val source: String = "VIIRS_SNPP_NRT",
    val areaCoords: String = "25,44,40,64"
)

data class UsgsProps(
    val baseUrl: String = "https://earthquake.usgs.gov/fdsnws/event/1/query",
    val minLatitude: Double = 25.0,
    val maxLatitude: Double = 40.0,
    val minLongitude: Double = 44.0,
    val maxLongitude: Double = 64.0,
    val minMagnitude: Double = 2.5
)

data class Agent2Props(
    val geocoder: GeocoderProps = GeocoderProps(),
    val ner: NerProps = NerProps()
)

data class GeocoderProps(
    val provider: String = "nominatim",
    val nominatimUrl: String = "https://nominatim.openstreetmap.org/search",
    val geonamesUsername: String = "demo",
    val rateLimitMs: Long = 1100
)

data class NerProps(val model: String = "en-ner-location.bin")

data class Agent3Props(
    val minCorroborationSources: Int = 2,
    val deceptionHighThreshold: Double = 0.7,
    val confidenceFloor: Double = 0.1,
    val recycledMediaWindowDays: Long = 180
)

data class Agent4Props(
    val cluster: ClusterProps = ClusterProps(),
    val severity: SeverityWeights = SeverityWeights()
)

data class ClusterProps(
    val epsKm: Double = 25.0,
    val minPoints: Int = 2,
    val timeWindowMinutes: Long = 120
)

data class SeverityWeights(
    val casualtyWeight: Double = 0.35,
    val infraWeight: Double = 0.25,
    val spreadWeight: Double = 0.20,
    val escalationWeight: Double = 0.20
)

data class Agent5Props(
    val sitrep: SitrepProps = SitrepProps(),
    val alert: AlertProps = AlertProps()
)

data class SitrepProps(val intervalMinutes: Long = 60, val flashIntervalMinutes: Long = 15)

data class AlertProps(val flashConfidence: Double = 0.85, val flashSeverity: String = "CRITICAL")


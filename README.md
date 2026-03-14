# Geospatial Command Center — Iran OSINT Monitor

<video src="https://github.com/ai-in-pm/Geospatial-Command-Center-Iran/releases/download/gcc-iran/GeoSpatial.Command.Center.-.Iran-3-4-2026.mp4" controls width="100%"></video>

A real-time, multi-agent OSINT intelligence platform built on **Spring Boot 3 + Kotlin** that ingests, geo-resolves, validates, fuses, and displays conflict events across Iran on a live interactive map.

## Why build it

This project exists to provide a single, unified command dashboard that aggregates open-source intelligence from disparate feeds (GDELT, ACLED, NASA FIRMS, USGS, ADS-B, AIS) into a correlated, confidence-scored, deception-aware operational picture — eliminating the need to manually monitor dozens of sources and enabling faster situational awareness for analysts.

---

## Architecture

```
OSINT Sources → Agent 1 (Ingest) → Agent 2 (Geo-Resolve) → Agent 3 (Validate)
                                                                      ↓
                              UI ← GraphQL/REST ← Agent 5 (Brief) ← Agent 4 (Fuse)
                                                                      ↑
                     Agent 6 (Strike) · Agent 7 (Airspace) · Agent 8 (Naval) · Agent 9 (Missile)
```

---

## Agent Pipeline

| # | Agent | Role | Data Sources |
|---|-------|------|--------------|
| 1 | **Pulse Ingestor** | Polls OSINT feeds every 60 s, produces `RawSignal` records | GDELT, ACLED, NASA FIRMS, USGS |
| 2 | **Geo-Temporal Resolver** | Geocodes location hints via NER + Nominatim/GeoNames; sets WGS-84 coordinates | OpenStreetMap Nominatim, GeoNames, OpenNLP |
| 3 | **Evidence Validator** | Cross-checks corroboration (≥2 sources), deception-risk scoring, recycled-media detection | Internal + perceptual hash registry |
| 4 | **Fusion & Forecast** | Clusters validated events into `Incident` records (25 km / 120 min window); severity scoring | Internal |
| 5 | **Command Brief** | Emits SITREP every 60 min; FLASH alerts every 15 min for CRITICAL/≥0.85 confidence events | Internal |
| 6 | **Strike Tracker** | Live drone/missile/strike tracks with trajectory, speed, heading, and impact ETA | Synthetic drill + live feeds |
| 7 | **Airspace Monitor** | ADS-B aircraft positions, threat classification (CRITICAL/WARNING/WATCH/ROUTINE) | OpenSky Network |
| 8 | **Naval Fleet Monitor** | Vessel positions, readiness level (HIGH/ELEVATED/ROUTINE) | AISHub, MarineTraffic |
| 9 | **Missile Launch Monitor** | Detects and tracks Iranian ballistic/cruise missile launches with target province | Synthetic drill + live feeds |

---

## UI Panels

| Panel | Location | Description |
|-------|----------|-------------|
| Strike Watch | Top-left | Live strike/drone/missile tracks |
| Missile Launch Alert | Below Strike Watch | Active missile launches with IN FLIGHT status |
| Fleet Watch | Bottom-left | Naval vessel positions and readiness |
| Active Incidents | Right sidebar | Fused incident cards with severity |
| Airspace Live | Bottom-right | ADS-B aircraft count and threat breakdown |
| CIA Intel Feed | Bottom-center | FLASH/IMMEDIATE/PRIORITY/ROUTINE intel items |
| Map Layers | Draggable | Legend for all map markers — drag by title bar |

All dashboard panels can be dragged and dropped anywhere on the interface by grabbing each panel title bar.

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Runtime | Java 21, Kotlin 2.1.10 |
| Framework | Spring Boot 3.4.3, Spring WebFlux, Spring GraphQL |
| Database | H2 in-memory (`jdbc:h2:mem:gccdb`) |
| Geo | JTS Core 1.20, Haversine (custom), Nominatim |
| NLP | Apache OpenNLP 2.5.3 |
| Cache | Caffeine 3.1.8 |
| Math | Apache Commons Math 3.6.1 |
| Frontend | Leaflet.js 1.9.4, vanilla JS |
| Build | Gradle 8 (Kotlin DSL) |

---

## Prerequisites

- **Java 21** (`JAVA_HOME` must point to a JDK 21 installation)
- Optional API keys (see below) — the app runs with `demo` defaults

---

## Quick Start

```bash
# Windows
.\gradlew.bat bootRun

# Linux / macOS
./gradlew bootRun
```

Open **http://localhost:8081** in your browser.

### Native Desktop Mode

Run the native desktop shell instead of the browser-hosted workflow:

```bash
# Windows
.\gradlew.bat desktopRun
```

The desktop shell starts the local Spring Boot backend automatically, waits for health readiness, and opens the Iran OSINT Monitor as a native desktop experience.

---

## Windows Desktop Packaging

Build the native Windows launcher image:

```bash
.\gradlew.bat desktopPackageImage
```

Build the Windows installer:

```bash
.\gradlew.bat desktopPackageInstaller
```

Build both in one command:

```bash
.\gradlew.bat desktopPackage
```

Output locations:

- Launcher image: `build/desktop/jpackage/image/Geospatial Command Center/`
- Native launcher: `build/desktop/jpackage/image/Geospatial Command Center/Geospatial Command Center.exe`
- Installer: `build/desktop/jpackage/installer/`

Notes:

- Packaging requires **Java 21** with the `jpackage` tool available in the selected JDK.
- Building the installer additionally requires **WiX Toolset 3.x** (`candle.exe` and `light.exe`) on your `PATH`.
- The packaged launcher always starts in desktop mode and boots the embedded local backend automatically.
- If WiX is not installed yet, `desktopPackageImage` still produces a portable Windows launcher folder you can distribute directly.
- Unsigned Windows installers may show a SmartScreen warning until code signing is added.

---

## Deployment

- This app is now deployment-ready for Docker-compatible hosts.
- Public platforms should inject `PORT`; locally it still defaults to `8081`.
- Reverse proxy headers are enabled, and the H2 console is disabled by default.

```bash
docker build -t geospatialcommandcenter .
docker run -e PORT=8081 -p 8081:8081 geospatialcommandcenter
```

Local container URL: `http://localhost:8081`

After deploying the container, share the HTTPS URL assigned by your hosting provider.

---

## Environment Variables

| Variable | Required | Description |
|----------|----------|-------------|
| `ACLED_API_KEY` | Recommended | ACLED conflict data API key |
| `ACLED_EMAIL` | Recommended | ACLED account email |
| `NASA_FIRMS_KEY` | Recommended | NASA FIRMS fire/thermal data key |
| `GEONAMES_USER` | Recommended | GeoNames geocoding username |
| `AISHUB_USERNAME` | Optional | AISHub naval AIS data username |
| `MARINETRAFFIC_API_KEY` | Optional | MarineTraffic extended API key |
| `PORT` | Platform-provided | HTTP port used by the deployment platform |
| `APP_LOG_LEVEL` | Optional | App log level, defaults to `INFO` |
| `H2_CONSOLE_ENABLED` | Optional | Set to `true` only for trusted local debugging |

Set as OS environment variables or pass via `application.properties`.

---

## Key Endpoints

| Endpoint | Description |
|----------|-------------|
| `GET /` | Main command dashboard |
| `POST /graphql` | GraphQL API (incidents, events, alerts, sitreps, strike tracks) |
| `GET /actuator/health` | Health check |
| `GET /h2-console` | H2 database console for trusted local debugging only when `H2_CONSOLE_ENABLED=true` |

---

## Ownership

**Owner:** Darrell Mesa
**Email:** darrell.mesa@pm-ss.org
**GitHub:** [https://github.com/ai-in-pm](https://github.com/ai-in-pm)


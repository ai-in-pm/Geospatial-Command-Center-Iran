/**
 * Geospatial Command Center — Command Center Dashboard
 * Main map refresh: 30 s  |  Strike Watch refresh: 10 s  |  Airspace refresh: 30 s  |  Fleet refresh: 60 s
 */
const API               = '/api/v1';
const REFRESH_MS        = 30_000;
const STRIKE_POLL_MS    = 10_000;
const AIRSPACE_POLL_MS  = 30_000;
const FLEET_POLL_MS     = 60_000;
const MISSILE_POLL_MS   = 15_000;

// ── Map init ─────────────────────────────────────────────────────────────────
const map = L.map('map', { center: [32.5, 54.0], zoom: 5, zoomControl: false });

L.tileLayer('https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png', {
  attribution: '© OpenStreetMap © CARTO',
  maxZoom: 18
}).addTo(map);

// Layer groups
const layerDrones     = L.layerGroup().addTo(map);   // live drone tracks
const layerMissiles   = L.layerGroup().addTo(map);   // live missile tracks
const layerStrikes    = L.layerGroup().addTo(map);
const layerFire       = L.layerGroup().addTo(map);
const layerSeismic    = L.layerGroup().addTo(map);
const layerProtests   = L.layerGroup().addTo(map);
const layerMaritime   = L.layerGroup().addTo(map);
const layerIncidents  = L.layerGroup().addTo(map);
const layerAircraft   = L.layerGroup().addTo(map);   // live ADS-B aircraft
const layerNaval      = L.layerGroup().addTo(map);   // naval vessel tracks
const layerMissileArc = L.layerGroup().addTo(map);   // missile launch arcs + site markers
const layerNuclear    = L.layerGroup().addTo(map);   // Iran nuclear facilities

L.control.layers(null, {
  '🛸 Drone Tracks (live)': layerDrones,
  '🚀 Missile Tracks (live)': layerMissiles,
  '🔴 Missile Launch Arcs': layerMissileArc,
  'Strikes / Explosions': layerStrikes,
  'Thermal Anomalies': layerFire,
  'Seismic Events': layerSeismic,
  'Protests / Riots': layerProtests,
  'Maritime Incidents': layerMaritime,
  'Incident Clusters': layerIncidents,
  '✈ Live Airspace (ADS-B)': layerAircraft,
  '⚓ Naval Fleet (OSINT)': layerNaval,
  '☢ Iran Nuclear Facilities': layerNuclear
}).addTo(map);

// ── Iran Nuclear Facilities ──────────────────────────────────────────────────
const IRAN_NUCLEAR_SITES = [
  { name:'Natanz Uranium Enrichment',   lat:33.723, lon:51.727, type:'ENRICHMENT',  status:'ACTIVE',
    detail:'Primary centrifuge complex — IR-1 / IR-2m cascades. Underground halls A&B, 3,000+ centrifuges. IAEA monitored.' },
  { name:'Fordow (Qom) Enrichment',     lat:34.884, lon:50.567, type:'ENRICHMENT',  status:'ACTIVE',
    detail:'Deeply buried facility near Qom. 60% U-235 enrichment confirmed 2023. ~1,044 IR-6 centrifuges.' },
  { name:'Arak IR-40 Heavy Water',      lat:34.869, lon:49.069, type:'REACTOR',     status:'MODIFIED',
    detail:'Heavy water reactor redesigned under JCPOA 2015. Core filled with concrete. New core design in development.' },
  { name:'Bushehr Nuclear Power Plant', lat:28.834, lon:50.886, type:'POWER',       status:'OPERATIONAL',
    detail:'1,000 MWe VVER-1000 PWR. Russian-built, commercially operational since 2011. IAEA safeguards apply.' },
  { name:'Isfahan Nuclear Tech Center', lat:32.670, lon:51.730, type:'CONVERSION',  status:'ACTIVE',
    detail:'Uranium conversion facility (UCF). UF6 production, fuel fabrication. UCF-1 and UCF-2 production lines.' },
  { name:'Parchin Military Complex',    lat:35.520, lon:51.770, type:'MILITARY',    status:'SUSPECTED',
    detail:'IRGC Aerospace Research complex. Suspected HE detonation testing. Satellite imagery shows remediation activity.' },
  { name:'Tehran Research Reactor',     lat:35.740, lon:51.380, type:'RESEARCH',    status:'ACTIVE',
    detail:'5 MW light-water research reactor, operated by AEOI since 1967. Produces medical isotopes.' },
  { name:'Saghand Uranium Mine',        lat:32.560, lon:55.480, type:'MINING',      status:'ACTIVE',
    detail:'Primary uranium ore mining site — annual capacity ~50 t U₃O₈. Connected to Ardakan YPC.' },
  { name:'Ardakan Yellow Cake Facility',lat:32.310, lon:54.030, type:'PROCESSING',  status:'ACTIVE',
    detail:'Uranium Ore Concentration plant — converts Saghand ore to yellowcake (U₃O₈) for Isfahan UCF.' },
  { name:'Gchine Mine',                 lat:27.530, lon:55.730, type:'MINING',      status:'ACTIVE',
    detail:'Southern uranium mine near Bandar Abbas. In-situ leaching operation. Supplies Esfahan UCF.' },
];

const NUCLEAR_TYPE_COLOR = {
  ENRICHMENT: '#ff5722',  // deep orange — highest proliferation risk
  REACTOR:    '#ffd740',  // amber
  POWER:      '#69f0ae',  // green — civilian
  CONVERSION: '#ff9800',  // orange
  MILITARY:   '#ff1744',  // red — suspected weapons-related
  RESEARCH:   '#40c4ff',  // light blue
  MINING:     '#a5d6a7',  // light green
  PROCESSING: '#ce93d8',  // purple
};

/** Render all Iranian nuclear facility markers on layerNuclear. */
function renderNuclearSites() {
  layerNuclear.clearLayers();
  IRAN_NUCLEAR_SITES.forEach(site => {
    const col  = NUCLEAR_TYPE_COLOR[site.type] || '#69f0ae';
    const icon = L.divIcon({
      html: `<div style="font-size:16px;line-height:1;filter:drop-shadow(0 0 4px ${col});
                         animation:acPulse 3s infinite">☢</div>`,
      iconSize: [18, 18], iconAnchor: [9, 9], className: ''
    });
    const marker = L.marker([site.lat, site.lon], { icon });
    marker.bindPopup(`
      <div style="min-width:220px">
        <b style="color:${col};font-size:11px">☢ ${site.name}</b><br>
        <span style="color:#546e7a;font-size:9px">${site.type} · <b style="color:${col}">${site.status}</b></span>
        <hr style="border-color:#1b3a1b;margin:4px 0">
        <div style="color:#90a4ae;font-size:10px;line-height:1.4">${site.detail}</div>
        <div style="color:#37474f;font-size:9px;margin-top:4px;font-family:monospace">
          ${site.lat.toFixed(3)}°N ${site.lon.toFixed(3)}°E
        </div>
      </div>`, { maxWidth: 280, className: 'nuclear-popup' });
    marker.bindTooltip(`☢ ${site.name}`, { sticky: true });
    marker.addTo(layerNuclear);
  });
}

// ── Live-data caches (shared across all refresh loops) ───────────────────────
let cachedAircraft = [];   // latest from /airspace/live
let cachedVessels  = [];   // latest from /naval/fleet
let cachedStrikes  = [];   // latest from /strikes/active
let cachedMissiles = [];   // latest from /missiles/live
let lastIncidents  = [];   // latest from /incidents
let lastAlerts     = [];   // latest from /alerts

// ── Marker factory ────────────────────────────────────────────────────────────
function dot(lat, lon, color, title, layer) {
  L.circleMarker([lat, lon], {
    radius: 7, fillColor: color, color: '#fff', weight: 1,
    opacity: 0.9, fillOpacity: 0.85
  }).bindTooltip(title, { sticky: true }).addTo(layer);
}

// ── Confidence-graded incident marker ────────────────────────────────────────
function incidentMarker(inc) {
  const colors = { CRITICAL:'#ef5350', HIGH:'#ffa726', MODERATE:'#ffee58', LOW:'#66bb6a', MINIMAL:'#78909c' };
  const c = colors[inc.severity] || '#78909c';
  const r = 6 + inc.severityScore * 10;
  const marker = L.circleMarker([inc.latitude, inc.longitude], {
    radius: r, fillColor: c, color: '#fff', weight: 1, opacity: 1, fillOpacity: 0.7
  });
  marker.bindPopup(`
    <b style="color:${c}">${inc.primaryType.replace(/_/g,' ')}</b><br>
    <small>${inc.province || 'Unknown province'}</small><br>
    ${inc.narrative}<br>
    <hr style="border-color:#333;margin:4px 0">
    Confidence: ${(inc.mergedConfidence*100).toFixed(0)}%&nbsp;|&nbsp;
    Severity: ${inc.severity}<br>
    Events fused: ${inc.eventCount}&nbsp;|&nbsp;Spread: ${inc.geographicSpreadKm.toFixed(0)} km<br>
    ${inc.escalationIndicators.length ? '<b>⚠ '+inc.escalationIndicators.join(', ')+'</b>' : ''}
  `, { maxWidth: 280 });
  marker.addTo(layerIncidents);
}

// ── Sidebar renderers ─────────────────────────────────────────────────────────

/** Build synthetic incident cards from live aircraft / vessel / strike / missile caches. */
function buildLiveIncidentCards() {
  const cards = [];
  const ALERT_SEV = { CRITICAL:'CRITICAL', WARNING:'HIGH', WATCH:'MODERATE', ROUTINE:'LOW' };

  // Missile launches (highest priority — above strike tracks)
  cachedMissiles
    .filter(m => m.launchStatus === 'LAUNCH_DETECTED' || m.launchStatus === 'IN_FLIGHT' || m.launchStatus === 'TERMINAL_PHASE')
    .slice(0, 4)
    .forEach(m => {
      const col = ML_STATUS_COLOR[m.launchStatus] || '#ff1744';
      const qualTag = m.dataQuality === 'SYNTHETIC_DRILL' ? ' <span style="color:#ffa726;font-size:8px">[SIM]</span>' : '';
      cards.push(`
        <div class="incident-card CRITICAL" onclick="map.flyTo([${m.currentLat},${m.currentLon}],6)" style="border-left-color:${col}">
          <div class="inc-header">
            <span class="inc-type">🚀 MISSILE LAUNCH${qualTag}</span>
            <span class="inc-sev" style="color:${col}">${m.launchStatus.replace(/_/g,' ')}</span>
          </div>
          <div class="inc-narr">${m.weaponSystem} — ${m.launchProvince} → ${m.targetRegion}</div>
          <div class="inc-meta">IRGC &nbsp;|&nbsp; ${(m.confidence*100).toFixed(0)}% conf &nbsp;|&nbsp; ${m.rangeKm.toFixed(0)} km range</div>
        </div>`);
    });

  // Active strike tracks (highest priority)
  cachedStrikes
    .filter(t => t.status === 'INBOUND' || t.status === 'TRACKED')
    .forEach(t => {
      const col = t.category === 'DRONE' ? '#ff6d00' : '#d50000';
      const icon = t.category === 'DRONE' ? '🛸' : '🚀';
      cards.push(`
        <div class="incident-card CRITICAL" onclick="map.flyTo([${t.currentLat},${t.currentLon}],8)" style="border-left-color:${col}">
          <div class="inc-header">
            <span class="inc-type">${icon} ${t.weaponType.replace(/_/g,' ')}</span>
            <span class="inc-sev" style="color:${col}">CRITICAL</span>
          </div>
          <div class="inc-narr">${t.narrative}</div>
          <div class="inc-meta">STRIKE TRACK &nbsp;|&nbsp; ${(t.confidence*100).toFixed(0)}% conf &nbsp;|&nbsp; ${t.status}</div>
        </div>`);
    });

  // UAV tracks → HIGH interest
  cachedAircraft
    .filter(a => !a.onGround && a.category === 'UAV')
    .slice(0, 3)
    .forEach(a => {
      const cs = a.callsign || a.icao24;
      cards.push(`
        <div class="incident-card HIGH" onclick="map.flyTo([${a.lat},${a.lon}],8)" style="border-left-color:#ce93d8">
          <div class="inc-header">
            <span class="inc-type" style="color:#ce93d8">🛸 UAV / DRONE TRACK</span>
            <span class="inc-sev" style="color:#ce93d8">${a.alertLevel}</span>
          </div>
          <div class="inc-narr">${a.alertReason || cs + ' — UAV loiter/transit'}</div>
          <div class="inc-meta">${a.country} &nbsp;|&nbsp; ${cs} &nbsp;|&nbsp; ${(a.altM/1000).toFixed(2)} km alt &nbsp;|&nbsp; ${a.speedKmh.toFixed(0)} km/h</div>
        </div>`);
    });

  // ISR tracks → HIGH interest
  cachedAircraft
    .filter(a => !a.onGround && a.category === 'ISR_SURVEILLANCE')
    .slice(0, 3)
    .forEach(a => {
      const cs = a.callsign || a.icao24;
      cards.push(`
        <div class="incident-card HIGH" onclick="map.flyTo([${a.lat},${a.lon}],8)" style="border-left-color:#4dd0e1">
          <div class="inc-header">
            <span class="inc-type" style="color:#4dd0e1">👁 ISR / RECCE TRACK</span>
            <span class="inc-sev" style="color:#4dd0e1">${a.alertLevel}</span>
          </div>
          <div class="inc-narr">${a.alertReason || cs + ' — ISR surveillance pattern'}</div>
          <div class="inc-meta">${a.country} &nbsp;|&nbsp; ${cs} &nbsp;|&nbsp; ${(a.altM/1000).toFixed(1)} km alt &nbsp;|&nbsp; ${a.speedKmh.toFixed(0)} km/h</div>
        </div>`);
    });

  // Aircraft CRITICAL / WARNING alerts
  cachedAircraft
    .filter(a => !a.onGround && (a.alertLevel === 'CRITICAL' || a.alertLevel === 'WARNING') && a.category !== 'UAV' && a.category !== 'ISR_SURVEILLANCE')
    .slice(0, 5)
    .forEach(a => {
      const col = a.alertLevel === 'CRITICAL' ? '#ef5350' : '#ffa726';
      const sev = ALERT_SEV[a.alertLevel] || 'MODERATE';
      const cs  = a.callsign || a.icao24;
      cards.push(`
        <div class="incident-card ${sev}" onclick="map.flyTo([${a.lat},${a.lon}],8)" style="border-left-color:${col}">
          <div class="inc-header">
            <span class="inc-type">✈ AIRSPACE ${a.alertLevel}</span>
            <span class="inc-sev" style="color:${col}">${sev}</span>
          </div>
          <div class="inc-narr">${a.alertReason || cs + ' — ' + a.category.replace(/_/g,' ')}</div>
          <div class="inc-meta">${a.country} &nbsp;|&nbsp; ${cs} &nbsp;|&nbsp; ${(a.altM/1000).toFixed(1)} km alt</div>
        </div>`);
    });

  // Aircraft WATCH (cap 3)
  cachedAircraft
    .filter(a => !a.onGround && a.alertLevel === 'WATCH')
    .slice(0, 3)
    .forEach(a => {
      const cs = a.callsign || a.icao24;
      cards.push(`
        <div class="incident-card MODERATE" onclick="map.flyTo([${a.lat},${a.lon}],8)">
          <div class="inc-header">
            <span class="inc-type">✈ AIRSPACE WATCH</span>
            <span class="inc-sev">MODERATE</span>
          </div>
          <div class="inc-narr">${a.alertReason || cs + ' — ' + a.category.replace(/_/g,' ')}</div>
          <div class="inc-meta">${a.country} &nbsp;|&nbsp; ${cs} &nbsp;|&nbsp; ${(a.altM/1000).toFixed(1)} km alt</div>
        </div>`);
    });

  // Naval HIGH_READINESS
  cachedVessels
    .filter(v => v.alertLevel === 'HIGH_READINESS')
    .forEach(v => {
      const src = v.dataSource === 'AIS_LIVE'
        ? '<span style="color:#69f0ae">● AIS LIVE</span>'
        : '<span style="color:#ffa726">⚠ SYNTH</span>';
      cards.push(`
        <div class="incident-card HIGH" onclick="map.flyTo([${v.lat},${v.lon}],7)" style="border-left-color:#ef5350">
          <div class="inc-header">
            <span class="inc-type">⚓ NAVAL HIGH READINESS</span>
            <span class="inc-sev" style="color:#ef5350">HIGH</span>
          </div>
          <div class="inc-narr">${v.name} ${v.hullNumber} — ${v.operationalArea}</div>
          <div class="inc-meta">${v.flag} &nbsp;|&nbsp; ${v.speedKnots.toFixed(1)} kts &nbsp;|&nbsp; ${src}</div>
        </div>`);
    });

  return cards;
}

/** Build alert row objects from live aircraft / vessel / strike / missile caches. */
function buildLiveAlerts() {
  const items = [];

  // Missile launches → FLASH (highest priority)
  cachedMissiles
    .filter(m => m.launchStatus === 'LAUNCH_DETECTED' || m.launchStatus === 'IN_FLIGHT' || m.launchStatus === 'TERMINAL_PHASE')
    .slice(0, 4)
    .forEach(m => {
      const col = ML_STATUS_COLOR[m.launchStatus] || '#ff1744';
      const qualTag = m.dataQuality === 'SYNTHETIC_DRILL' ? '[SIM] ' : '';
      items.push({ priority: 'FLASH', color: col,
        msg: `🚀 ${qualTag}${m.weaponSystem} — ${m.launchProvince} → ${m.targetRegion} (${m.launchStatus.replace(/_/g,' ')})`,
        time: new Date(m.launchDetectedAt).toLocaleTimeString() });
    });

  // Strike tracks → FLASH
  cachedStrikes
    .filter(t => t.status === 'INBOUND' || t.status === 'TRACKED')
    .slice(0, 3)
    .forEach(t => {
      const icon = t.category === 'DRONE' ? '🛸' : '🚀';
      items.push({ priority:'FLASH', color:'#ef5350',
        msg: `${icon} ${t.weaponType.replace(/_/g,' ')} — ${t.status} (${(t.confidence*100).toFixed(0)}% conf)`,
        time: new Date(t.detectedAt).toLocaleTimeString() });
    });

  // UAV tracks → IMMEDIATE
  cachedAircraft
    .filter(a => !a.onGround && a.category === 'UAV')
    .slice(0, 3)
    .forEach(a => {
      items.push({ priority:'IMMEDIATE', color:'#ce93d8',
        msg: `🛸 UAV ${a.callsign || a.icao24}: ${a.alertReason || 'UAV track — ' + a.country}`,
        time: new Date(a.updatedAt).toLocaleTimeString() });
    });

  // ISR tracks → IMMEDIATE
  cachedAircraft
    .filter(a => !a.onGround && a.category === 'ISR_SURVEILLANCE')
    .slice(0, 3)
    .forEach(a => {
      items.push({ priority:'IMMEDIATE', color:'#4dd0e1',
        msg: `👁 ISR ${a.callsign || a.icao24}: ${a.alertReason || 'ISR surveillance — ' + a.country}`,
        time: new Date(a.updatedAt).toLocaleTimeString() });
    });

  // Aircraft CRITICAL → FLASH
  cachedAircraft
    .filter(a => !a.onGround && a.alertLevel === 'CRITICAL')
    .forEach(a => {
      items.push({ priority:'FLASH', color:'#ef5350',
        msg: `✈ ${a.callsign || a.icao24}: ${a.alertReason || 'Critical alert'}`,
        time: new Date(a.updatedAt).toLocaleTimeString() });
    });

  // Aircraft WARNING → IMMEDIATE (exclude UAV/ISR already listed above)
  cachedAircraft
    .filter(a => !a.onGround && a.alertLevel === 'WARNING' && a.category !== 'UAV' && a.category !== 'ISR_SURVEILLANCE')
    .slice(0, 4)
    .forEach(a => {
      items.push({ priority:'IMMEDIATE', color:'#ff7043',
        msg: `✈ ${a.callsign || a.icao24}: ${a.alertReason || 'Warning alert'}`,
        time: new Date(a.updatedAt).toLocaleTimeString() });
    });

  // Naval HIGH_READINESS → PRIORITY
  cachedVessels
    .filter(v => v.alertLevel === 'HIGH_READINESS')
    .slice(0, 5)
    .forEach(v => {
      items.push({ priority:'PRIORITY', color:'#ffa726',
        msg: `⚓ ${v.name} — ${v.operationalArea} [HIGH READINESS]`,
        time: new Date(v.updatedAt).toLocaleTimeString() });
    });

  // Naval ELEVATED → ROUTINE
  cachedVessels
    .filter(v => v.alertLevel === 'ELEVATED')
    .slice(0, 3)
    .forEach(v => {
      items.push({ priority:'ROUTINE', color:'#78909c',
        msg: `⚓ ${v.name} — ${v.operationalArea} [ELEVATED]`,
        time: new Date(v.updatedAt).toLocaleTimeString() });
    });

  return items;
}

function renderIncidents(incidents) {
  const el = document.getElementById('incident-list');
  const liveCards = buildLiveIncidentCards();

  const pipelineHtml = incidents.slice(0, 10).map(i => `
    <div class="incident-card ${i.severity}" onclick="map.flyTo([${i.latitude},${i.longitude}],8)">
      <div class="inc-header">
        <span class="inc-type">${i.primaryType.replace(/_/g,' ')}</span>
        <span class="inc-sev">${i.severity}</span>
      </div>
      <div class="inc-narr">${i.narrative}</div>
      <div class="inc-meta">${i.province||'?'} &nbsp;|&nbsp; conf ${(i.mergedConfidence*100).toFixed(0)}% &nbsp;|&nbsp; ${i.eventCount} events</div>
    </div>`).join('');

  if (!pipelineHtml && !liveCards.length) {
    el.innerHTML = '<div style="color:#546e7a;font-size:11px">Awaiting pipeline data…</div>';
    return;
  }
  el.innerHTML = pipelineHtml + liveCards.join('');
}

function renderAlerts(alerts) {
  const el = document.getElementById('alert-list');
  const liveAlerts = buildLiveAlerts();

  const pipelineHtml = alerts.slice(0, 10).map(a => {
    const col = { FLASH:'#ef5350', IMMEDIATE:'#ff7043', PRIORITY:'#ffa726', ROUTINE:'#78909c' }[a.priority] || '#546e7a';
    return `
      <div style="background:#0d1b2a;border-left:3px solid ${col};padding:6px;margin-bottom:5px;font-size:10px;border-radius:2px">
        <b style="color:${col}">${a.priority}</b> &nbsp;${a.message}
        <div style="color:#546e7a;margin-top:2px">${new Date(a.issuedAt).toLocaleTimeString()}</div>
      </div>`;
  }).join('');

  const liveHtml = liveAlerts.map(a => `
    <div style="background:#0d1b2a;border-left:3px solid ${a.color};padding:6px;margin-bottom:5px;font-size:10px;border-radius:2px">
      <b style="color:${a.color}">${a.priority}</b> &nbsp;${a.msg}
      <div style="color:#546e7a;margin-top:2px">${a.time}</div>
    </div>`).join('');

  if (!pipelineHtml && !liveHtml) {
    el.innerHTML = '<div style="color:#546e7a;font-size:11px">No alerts.</div>';
    return;
  }
  el.innerHTML = liveHtml + pipelineHtml;
}

// ── Strike track colours & icons ──────────────────────────────────────────────
const STRIKE_COLORS = {
  DRONE:       '#ff6d00',
  MISSILE:     '#d50000',
  UNKNOWN:     '#9e9e9e',
  INTERCEPTED: '#69f0ae',
  IMPACT:      '#795548'
};
const STRIKE_ICONS = { DRONE: '🛸', MISSILE: '🚀', INTERCEPTED: '🛡', IMPACT: '💥' };

function strikeColor(t) {
  if (t.status === 'INTERCEPTED') return STRIKE_COLORS.INTERCEPTED;
  if (t.status === 'IMPACT')      return STRIKE_COLORS.IMPACT;
  return STRIKE_COLORS[t.category] || STRIKE_COLORS.UNKNOWN;
}

/** Draw origin→target polyline + animated current-position dot. */
function renderStrikeTrack(t, layer) {
  const col = strikeColor(t);
  const isDrone = t.category === 'DRONE';

  // Trajectory line: dashed for drone, solid for missile
  L.polyline([[t.originLat, t.originLon], [t.targetLat, t.targetLon]], {
    color: col, weight: isDrone ? 1.5 : 2, opacity: 0.45,
    dashArray: isDrone ? '5 6' : null
  }).addTo(layer);

  // Faint "tail" from origin to current position
  L.polyline([[t.originLat, t.originLon], [t.currentLat, t.currentLon]], {
    color: col, weight: isDrone ? 2 : 2.5, opacity: 0.85,
    dashArray: isDrone ? '4 4' : null
  }).addTo(layer);

  // Pulsing current-position marker
  const r = t.category === 'MISSILE' ? 7 : 6;
  const marker = L.circleMarker([t.currentLat, t.currentLon], {
    radius: r, fillColor: col, color: '#fff', weight: 1.5,
    opacity: 1, fillOpacity: 0.9,
    className: t.status === 'INBOUND' ? 'strike-pulse' : ''
  });

  const eta = t.estimatedImpactAt
    ? Math.max(0, Math.round((new Date(t.estimatedImpactAt) - Date.now()) / 60000))
    : '?';
  const conf = (t.confidence * 100).toFixed(0);
  const qualBadge = t.dataQuality === 'SYNTHETIC_DRILL'
    ? '<b style="color:#ffa726">⚠ SYNTHETIC DRILL</b><br>'
    : (t.dataQuality === 'CONFIRMED_OSINT' ? '<b style="color:#69f0ae">✔ CONFIRMED OSINT</b><br>' : '');

  marker.bindPopup(`
    <b style="color:${col}">${STRIKE_ICONS[t.category] || '●'} ${t.weaponType.replace(/_/g,' ')}</b>
    &nbsp;<span style="color:#90a4ae;font-size:10px">${t.status}</span><br>
    ${qualBadge}
    <span style="color:#90a4ae;font-size:10px">${t.originRegion}</span><br>
    ${t.narrative}<br>
    <hr style="border-color:#333;margin:4px 0">
    Alt: ${(t.altitudeM/1000).toFixed(0)} km &nbsp;|&nbsp;
    Speed: ${t.speedKmh.toLocaleString()} km/h &nbsp;|&nbsp;
    ETA: ${eta} min<br>
    Progress: ${(t.completionFraction*100).toFixed(0)}% &nbsp;|&nbsp; Conf: ${conf}%
  `, { maxWidth: 280 });

  marker.addTo(layer);

  // Target marker (X)
  L.circleMarker([t.targetLat, t.targetLon], {
    radius: 5, fillColor: col, color: col, weight: 1, opacity: 0.5, fillOpacity: 0.2
  }).bindTooltip(`Target zone (${t.weaponType.replace(/_/g,' ')})`, { sticky: true }).addTo(layer);
}

/** Render the Strike Watch panel cards. */
function renderStrikePanel(tracks) {
  const el = document.getElementById('strike-list');
  const cnt = document.getElementById('sw-count');
  const active = tracks.filter(t => t.status === 'INBOUND' || t.status === 'TRACKED');
  cnt.textContent = `${active.length} ACTIVE`;

  if (!tracks.length) {
    el.innerHTML = '<div style="color:#546e7a;font-size:10px">No active strike tracks.</div>';
    return;
  }
  el.innerHTML = tracks.slice(0, 10).map(t => {
    const col = strikeColor(t);
    const icon = t.status === 'INTERCEPTED' ? '🛡' : (t.status === 'IMPACT' ? '💥' : STRIKE_ICONS[t.category] || '●');
    const eta = t.estimatedImpactAt
      ? Math.max(0, Math.round((new Date(t.estimatedImpactAt) - Date.now()) / 60000))
      : '?';
    const prog = Math.round(t.completionFraction * 100);
    const qualTag = t.dataQuality === 'SYNTHETIC_DRILL'
      ? '<span style="color:#ffa726;font-size:8px">SIM</span>' : '';
    return `
      <div class="strike-card ${t.category}" style="border-left-color:${col}" onclick="map.flyTo([${t.currentLat},${t.currentLon}],7)">
        <div class="sk-header">
          <span class="sk-icon">${icon}</span>
          <span class="sk-type">${t.weaponType.replace(/_/g,' ')}</span>
          ${qualTag}
          <span class="sk-status" style="color:${col}">${t.status}</span>
        </div>
        <div class="sk-narr">${t.narrative}</div>
        <div class="sk-meta">
          <span>✈ ${(t.speedKmh).toLocaleString()} km/h</span>
          <span>⬆ ${(t.altitudeM/1000).toFixed(0)} km</span>
          <span>⏱ ${eta}min</span>
          <span>🎯 ${(t.confidence*100).toFixed(0)}%</span>
        </div>
        <div class="sk-prog"><div class="sk-prog-bar" style="width:${prog}%"></div></div>
      </div>`;
  }).join('');
}

// ── Airspace / ADS-B layer ────────────────────────────────────────────────────

const ALERT_COLORS = {
  CRITICAL : '#ff1744',
  WARNING  : '#ff6d00',
  WATCH    : '#ffd740',
  ROUTINE  : '#29b6f6'
};

/** Per-category base colour (overrides alert-level colour for special types). */
const CATEGORY_COLORS = {
  UAV             : '#ce93d8',   // purple — MALE/HALE drones
  ISR_SURVEILLANCE: '#4dd0e1',   // cyan-teal — SIGINT/RECCE
  MILITARY        : '#ffa726',   // amber — military
  COMMERCIAL      : null,        // falls back to alert level colour
  CARGO           : null,
  GENERAL_AVIATION: '#a5d6a7',   // light green
  UNKNOWN         : '#90a4ae'
};

/** Glyph displayed on map per category. */
const CATEGORY_GLYPH = {
  UAV             : '🛸',
  ISR_SURVEILLANCE: '👁',
  MILITARY        : '✈',
  COMMERCIAL      : '✈',
  CARGO           : '✈',
  GENERAL_AVIATION: '✈',
  UNKNOWN         : '✈'
};

// ── Aircraft marker cache (keyed by icao24) ───────────────────────────────────
// Each entry: { marker: L.Marker, data: <aircraft object from API> }
// "data" is the last-known state vector including velocityMs, heading, updatedAt (epoch ms).
const aircraftMarkerCache = new Map();

/**
 * Dead-reckoning: extrapolate current position from last-known lat/lon,
 * heading (degrees true), and ground speed (m/s) using elapsed wall-clock time.
 *
 * Uses the flat-earth approximation — accurate to < 0.1 % over the 30 s poll window.
 *
 * @param {object} a — aircraft data object (must have lat, lon, heading, velocityMs, updatedAt)
 * @returns {[number,number]} — [latitude, longitude]
 */
function deadReckonPosition(a) {
  const elapsedS = (Date.now() - a.updatedAt) / 1000;
  // Do not extrapolate beyond 90 s (stale data) or if speed/heading are zero/missing
  if (elapsedS <= 0 || elapsedS > 90 || !a.velocityMs || !a.heading) return [a.lat, a.lon];
  const distKm     = (a.velocityMs * elapsedS) / 1000;
  const headingRad = (a.heading * Math.PI) / 180;
  const dLat = (distKm * Math.cos(headingRad)) / 111.32;
  const dLon = (distKm * Math.sin(headingRad)) / (111.32 * Math.cos(a.lat * Math.PI / 180));
  return [a.lat + dLat, a.lon + dLon];
}

/**
 * Fly to the current dead-reckoned position of an aircraft and open its popup.
 * Uses the marker cache so the target is the estimated real-time location,
 * not the stale API-reported position.
 * @param {string} icao24 — ICAO 24-bit address hex string
 */
function flyToAircraft(icao24) {
  const entry = aircraftMarkerCache.get(icao24);
  if (!entry) return;
  const [lat, lon] = deadReckonPosition(entry.data);
  map.flyTo([lat, lon], 9, { animate: true, duration: 0.8 });
  entry.marker.openPopup();
}

/**
 * Animation tick — called every 1 s.
 * 1. Advances each cached aircraft marker to its dead-reckoned position.
 * 2. Refreshes any open popup with the live position.
 * 3. Updates all visible .ac-pos[data-icao24] elements in the Airspace panel
 *    so card readouts show live lat/lon/heading rather than stale API values.
 */
function animateAircraftTracks() {
  const now = Date.now();
  aircraftMarkerCache.forEach(({ marker, data }) => {
    if (data.onGround || !data.velocityMs) return;
    const [lat, lon] = deadReckonPosition(data);
    marker.setLatLng([lat, lon]);

    // Refresh open popup content with dead-reckoned coordinates
    if (marker.isPopupOpen()) {
      marker.setPopupContent(buildAircraftPopup({ ...data, lat, lon }));
    }

    // Update the live position readout row in any visible panel card
    const posEl = document.querySelector(`.ac-pos[data-icao24="${data.icao24}"]`);
    if (posEl) {
      const ageS    = Math.round((now - data.updatedAt) / 1000);
      const hdg     = data.heading != null ? data.heading.toFixed(0) + '°' : '—';
      const altKm   = (data.altM / 1000).toFixed(1);
      const spdKmh  = data.speedKmh != null ? data.speedKmh.toFixed(0) : '—';
      posEl.textContent =
        `${lat.toFixed(3)}°N  ${lon.toFixed(3)}°E  · hdg ${hdg}  · alt ${altKm}km  · ${spdKmh}km/h  [+${ageS}s]`;
    }
  });
}

/** Rotated aircraft icon coloured by category and alert level. */
function aircraftIcon(a) {
  const catCol = CATEGORY_COLORS[a.category];
  const col    = catCol || ALERT_COLORS[a.alertLevel] || '#90a4ae';
  const glyph  = CATEGORY_GLYPH[a.category] || '✈';
  const rot    = (a.category === 'UAV' || a.category === 'ISR_SURVEILLANCE') ? 0 : (a.heading != null ? a.heading : 0);
  const size   = a.alertLevel === 'ROUTINE' ? 14 : 18;
  const glow   = (a.alertLevel === 'CRITICAL' || a.category === 'UAV' || a.category === 'ISR_SURVEILLANCE')
    ? `filter:drop-shadow(0 0 5px ${col})` : '';
  const pulse  = (a.category === 'UAV' || a.category === 'ISR_SURVEILLANCE')
    ? 'animation:acPulse 1.8s infinite;' : '';
  return L.divIcon({
    html: `<div style="transform:rotate(${rot}deg);color:${col};font-size:${size}px;` +
          `line-height:1;text-shadow:0 0 3px #000;${glow};${pulse}">${glyph}</div>`,
    iconSize   : [size, size],
    iconAnchor : [size / 2, size / 2],
    className  : ''
  });
}

/** Build popup HTML for an aircraft — called on first creation and on category change. */
function buildAircraftPopup(a) {
  const catCol = CATEGORY_COLORS[a.category];
  const col    = catCol || ALERT_COLORS[a.alertLevel] || '#29b6f6';
  const cs     = a.callsign || a.icao24;
  const altFt  = Math.round(a.altM * 3.281).toLocaleString();
  const spdKts = (a.speedKmh / 1.852).toFixed(0);
  const srcMap = ['ADS-B', 'ASTERIX', 'MLAT', 'FLARM'];
  const src    = srcMap[a.positionSource] || '?';
  const squawkBadge = a.squawk && ['7700','7600','7500'].includes(a.squawk)
    ? `<b style="color:#ff1744">🚨 SQUAWK ${a.squawk}</b><br>` : '';
  const alertTag = a.alertLevel !== 'ROUTINE' ? `<span style="color:${col}">[${a.alertLevel}]</span> ` : '';
  const reasonLine = a.alertReason ? `<em style="color:${col};font-size:10px">${a.alertReason}</em><br>` : '';
  const catBadge = a.category === 'UAV'
    ? `<b style="color:#ce93d8;font-size:11px">🛸 UAV / DRONE TRACK</b><br>`
    : a.category === 'ISR_SURVEILLANCE'
      ? `<b style="color:#4dd0e1;font-size:11px">👁 ISR / RECCE TRACK</b><br>` : '';
  const vertTag = a.vertRateMs > 0.5 ? '↑' : (a.vertRateMs < -0.5 ? '↓' : '→');
  return `
    ${squawkBadge}${catBadge}
    <b style="color:${col}">${alertTag}${cs}</b>
    <span style="color:#90a4ae;font-size:10px"> ${a.category.replace(/_/g,' ')}</span><br>
    ${reasonLine}
    <hr style="border-color:#1e3a5f;margin:4px 0">
    Country: ${a.country}<br>
    Alt: ${altFt} ft (${(a.altM/1000).toFixed(1)} km) ${vertTag}<br>
    Speed: ${a.speedKmh.toFixed(0)} km/h (${spdKts} kts)<br>
    Heading: ${a.heading != null ? a.heading.toFixed(0)+'°' : '—'}<br>
    ICAO24: <code>${a.icao24}</code> | Squawk: ${a.squawk || '—'}<br>
    Src: ${src}
    <div style="color:#546e7a;font-size:9px;margin-top:3px">
      Recv: ${new Date(a.updatedAt).toLocaleTimeString()} · Dead-reckoning active
    </div>`;
}

/**
 * Upsert one ADS-B aircraft track into the marker cache.
 * If the ICAO24 already has a marker, just update its icon and stored data
 * (no destroy/recreate → no flicker, continuous movement).
 * If new, create the marker and bind its popup.
 */
function renderAircraftTrack(a) {
  if (a.onGround) {
    // Remove any lingering airborne marker for this aircraft
    if (aircraftMarkerCache.has(a.icao24)) {
      aircraftMarkerCache.get(a.icao24).marker.remove();
      aircraftMarkerCache.delete(a.icao24);
    }
    return;
  }

  if (aircraftMarkerCache.has(a.icao24)) {
    // ── Update existing marker ────────────────────────────────────────────────
    const entry  = aircraftMarkerCache.get(a.icao24);
    entry.marker.setIcon(aircraftIcon(a));    // refresh icon if alertLevel changed
    entry.marker.setLatLng([a.lat, a.lon]);   // snap to new API-reported position
    entry.marker.setPopupContent(buildAircraftPopup(a));
    entry.data   = a;                         // store fresh state for dead reckoning
  } else {
    // ── Create new marker ─────────────────────────────────────────────────────
    const marker = L.marker([a.lat, a.lon], { icon: aircraftIcon(a) });
    marker.bindPopup(buildAircraftPopup(a), { maxWidth: 270 });
    marker.addTo(layerAircraft);
    aircraftMarkerCache.set(a.icao24, { marker, data: a });
  }
}

/**
 * Render the Airspace Live panel — shows EVERY tracked aircraft in real-time.
 * Sections (priority order): UAV · ISR · Military · Alerts · Commercial · Unknown
 * Each card carries a data-icao24 so the 1 s animation loop updates its position readout.
 */
function renderAirspacePanel(aircraft, total) {
  const el  = document.getElementById('airspace-list');
  const cnt = document.getElementById('as-count');

  const airborne    = aircraft.filter(a => !a.onGround);
  const uavTracks   = airborne.filter(a => a.category === 'UAV');
  const isrTracks   = airborne.filter(a => a.category === 'ISR_SURVEILLANCE');
  const milTracks   = airborne.filter(a => a.category === 'MILITARY');
  const alertTracks = airborne.filter(a =>
    (a.alertLevel === 'CRITICAL' || a.alertLevel === 'WARNING') &&
    a.category !== 'UAV' && a.category !== 'ISR_SURVEILLANCE' && a.category !== 'MILITARY'
  );
  // WATCH-level from watched countries (commercial + unknown, not already in mil/alert)
  const watchTracks = airborne.filter(a =>
    a.alertLevel === 'WATCH' &&
    a.category !== 'UAV' && a.category !== 'ISR_SURVEILLANCE' && a.category !== 'MILITARY'
  );
  // Purely routine commercial / general aviation
  const routineTracks = airborne.filter(a =>
    a.alertLevel === 'ROUTINE' &&
    a.category !== 'UAV' && a.category !== 'ISR_SURVEILLANCE'
  );
  // Sort routine by altitude descending (highest first)
  routineTracks.sort((a, b) => b.altM - a.altM);

  // ── Badge ──────────────────────────────────────────────────────────────────
  const badgeParts = [`${total} AC`];
  if (uavTracks.length)  badgeParts.push(`🛸${uavTracks.length}`);
  if (isrTracks.length)  badgeParts.push(`👁${isrTracks.length}`);
  if (milTracks.length)  badgeParts.push(`✈${milTracks.length}`);
  cnt.textContent = badgeParts.join(' · ');
  cnt.style.color = (uavTracks.length || isrTracks.length) ? '#ce93d8' : '#64b5f6';

  const sections = [];

  // ── 2×3 live-count summary grid ───────────────────────────────────────────
  sections.push(`
    <div style="display:grid;grid-template-columns:repeat(3,1fr);gap:3px;margin-bottom:6px;font-size:9px">
      <div style="background:#071220;border:1px solid #0d47a1;border-radius:2px;padding:3px;text-align:center">
        <div style="color:#64b5f6;font-size:12px;font-weight:bold">${airborne.length}</div>
        <div style="color:#546e7a">Airborne</div>
      </div>
      <div style="background:#1a0a1a;border:1px solid #6a1b9a;border-radius:2px;padding:3px;text-align:center">
        <div style="color:#ce93d8;font-size:12px;font-weight:bold">${uavTracks.length}</div>
        <div style="color:#546e7a">🛸 UAV</div>
      </div>
      <div style="background:#001a1a;border:1px solid #006064;border-radius:2px;padding:3px;text-align:center">
        <div style="color:#4dd0e1;font-size:12px;font-weight:bold">${isrTracks.length}</div>
        <div style="color:#546e7a">👁 ISR</div>
      </div>
      <div style="background:#1a1000;border:1px solid #e65100;border-radius:2px;padding:3px;text-align:center">
        <div style="color:#ffa726;font-size:12px;font-weight:bold">${milTracks.length}</div>
        <div style="color:#546e7a">✈ MIL</div>
      </div>
      <div style="background:#071220;border:1px solid #0d47a1;border-radius:2px;padding:3px;text-align:center">
        <div style="color:#29b6f6;font-size:12px;font-weight:bold">${routineTracks.length}</div>
        <div style="color:#546e7a">Commercial</div>
      </div>
      <div style="background:#111;border:1px solid #ff6d00;border-radius:2px;padding:3px;text-align:center">
        <div style="color:#ff6d00;font-size:12px;font-weight:bold">${alertTracks.length + watchTracks.length}</div>
        <div style="color:#546e7a">⚠ Alert</div>
      </div>
    </div>`);

  // ── UAV tracks ────────────────────────────────────────────────────────────
  if (uavTracks.length) {
    sections.push(`<div class="ac-section-hdr uav">🛸 UAV / DRONE — ${uavTracks.length} LIVE TRACKS</div>`);
    sections.push(...uavTracks.map(a => buildAcCard(a, '#ce93d8', '🛸')));
  }

  // ── ISR / RECCE tracks ────────────────────────────────────────────────────
  if (isrTracks.length) {
    sections.push(`<div class="ac-section-hdr isr">👁 ISR / RECCE — ${isrTracks.length} LIVE TRACKS</div>`);
    sections.push(...isrTracks.map(a => buildAcCard(a, '#4dd0e1', '👁')));
  }

  // ── Military tracks ───────────────────────────────────────────────────────
  if (milTracks.length) {
    sections.push(`<div class="ac-section-hdr mil">✈ MILITARY — ${milTracks.length} LIVE TRACKS</div>`);
    sections.push(...milTracks.map(a => buildAcCard(a, '#ffa726', '✈')));
  }

  // ── CRITICAL / WARNING alerts ─────────────────────────────────────────────
  if (alertTracks.length) {
    sections.push(`<div class="ac-section-hdr mil" style="border-left-color:#ff1744;color:#ff1744">🚨 ALERTS — ${alertTracks.length}</div>`);
    sections.push(...alertTracks.map(a => {
      const col = ALERT_COLORS[a.alertLevel] || '#29b6f6';
      return buildAcCard(a, col, '✈');
    }));
  }

  // ── WATCH — watched-country commercial / unknown ──────────────────────────
  if (watchTracks.length) {
    sections.push(`<div class="ac-section-hdr mil" style="border-left-color:#ff6d00;color:#ff9800">⚠ WATCH — ${watchTracks.length} FLAGGED</div>`);
    sections.push(...watchTracks.map(a => buildAcCard(a, '#ff9800', '✈')));
  }

  // ── Routine commercial / general aviation (ALL — sorted by altitude) ──────
  if (routineTracks.length) {
    sections.push(`<div class="ac-section-hdr" style="border-left-color:#1565c0;color:#64b5f6;background:#040e1a">
      ✈ ALL TRAFFIC — ${routineTracks.length} AIRCRAFT LIVE
      <span style="color:#37474f;font-size:9px;margin-left:6px">click any row to fly-to</span>
    </div>`);
    sections.push(...routineTracks.map(a => buildAcCard(a, '#29b6f6', '✈')));
  }

  // ── No data yet ───────────────────────────────────────────────────────────
  if (airborne.length === 0) {
    sections.push(`<div style="color:#546e7a;font-size:10px;padding:6px">
      Awaiting ADS-B state vectors from OpenSky Network…<br>
      <span style="font-size:9px">Next poll in ≤30 s</span>
    </div>`);
  }

  el.innerHTML = sections.join('');
}

/**
 * Build a single airspace card HTML string.
 * The card carries a data-icao24 attribute so the 1 s animation loop can
 * update the .ac-pos element with the live dead-reckoned position every tick.
 * Clicking the card calls flyToAircraft() which uses the cache position.
 */
function buildAcCard(a, col, glyph) {
  const cs     = a.callsign || a.icao24;
  const hdg    = a.heading  != null ? a.heading.toFixed(0) + '°' : '—';
  const altKm  = (a.altM / 1000).toFixed(1);
  const spdKmh = a.speedKmh != null ? a.speedKmh.toFixed(0) : '—';
  const ageS   = Math.round((Date.now() - a.updatedAt) / 1000);
  return `
    <div class="ac-card ${a.alertLevel}" data-icao24="${a.icao24}"
         onclick="flyToAircraft('${a.icao24}')"
         style="border-left-color:${col};cursor:pointer">
      <div class="ac-head">
        <span class="ac-cs" style="color:${col}">${glyph} ${cs}</span>
        <span class="ac-cat">${a.category.replace(/_/g,' ')}</span>
        <span class="ac-alert" style="color:${col}">${a.alertLevel}</span>
      </div>
      ${a.alertReason ? `<div class="ac-reason" style="color:${col}">${a.alertReason}</div>` : ''}
      <div class="ac-meta">
        <span>⬆ ${altKm}km</span>
        <span>${glyph} ${spdKmh}km/h</span>
        <span>hdg ${hdg}</span>
        <span>🌐 ${a.country}</span>
        ${a.squawk ? `<span style="color:${col}">SQ:${a.squawk}</span>` : ''}
      </div>
      <div class="ac-pos" data-icao24="${a.icao24}"
           style="color:#37474f;font-size:9px;margin-top:3px;font-family:monospace;
                  letter-spacing:0.02em;border-top:1px solid #0d2035;padding-top:2px">
        ${a.lat.toFixed(3)}°N&nbsp;&nbsp;${a.lon.toFixed(3)}°E&nbsp;·&nbsp;hdg&nbsp;${hdg}&nbsp;·&nbsp;alt&nbsp;${altKm}km&nbsp;·&nbsp;${spdKmh}km/h&nbsp;&nbsp;<span style="color:#1b5e20">[+${ageS}s]</span>
      </div>
    </div>`;
}

/** 30 s airspace-only refresh — diffs the marker cache to avoid flicker. */
async function refreshAirspace() {
  try {
    const res      = await fetch(`${API}/airspace/live`).then(r => r.json());
    const aircraft = res.aircraft || [];

    // Build a set of ICAO24s in the fresh response
    const freshIds = new Set(aircraft.map(a => a.icao24));

    // Remove markers for aircraft no longer in the feed (landed / out of range)
    aircraftMarkerCache.forEach((entry, icao24) => {
      if (!freshIds.has(icao24)) {
        entry.marker.remove();
        aircraftMarkerCache.delete(icao24);
      }
    });

    // Upsert each aircraft (create or update — no full clear)
    aircraft.forEach(renderAircraftTrack);

    renderAirspacePanel(aircraft, res.total || 0);

    // ── Header badge ──────────────────────────────────────────────────────────
    const uavCount  = aircraft.filter(a => !a.onGround && a.category === 'UAV').length;
    const isrCount  = aircraft.filter(a => !a.onGround && a.category === 'ISR_SURVEILLANCE').length;
    const alertCount = res.alerts || 0;
    const badgeEl   = document.getElementById('badge-airspace');
    const parts     = [`✈ ${res.total || 0}`];
    if (uavCount)   parts.push(`🛸${uavCount}`);
    if (isrCount)   parts.push(`👁${isrCount}`);
    if (alertCount) parts.push(`⚠ ${alertCount}`);
    badgeEl.textContent    = parts.join(' / ');
    badgeEl.style.borderColor = (uavCount || isrCount) ? '#ce93d8' : (alertCount ? '#ff6d00' : '#1565c0');
    badgeEl.style.color       = (uavCount || isrCount) ? '#ce93d8' : (alertCount ? '#ff6d00' : '#64b5f6');
    document.getElementById('sb-airspace').textContent =
      `✈ Aircraft: ${res.total || 0}  🛸 UAV: ${uavCount}  👁 ISR: ${isrCount}  ⚠ Alerts: ${alertCount}`;

    // Update sidebar cache and re-render incidents/alerts with fresh data
    cachedAircraft = aircraft;
    renderIncidents(lastIncidents);
    renderAlerts(lastAlerts);
  } catch (e) { console.debug('Airspace poll error:', e.message); }
}

/** Fast 10 s strike-only refresh. */
async function refreshStrikes() {
  try {
    const res = await fetch(`${API}/strikes/active`).then(r => r.json());
    const tracks = res.tracks || [];

    // Redraw strike layers
    layerDrones.clearLayers();
    layerMissiles.clearLayers();
    tracks.forEach(t => {
      const layer = t.category === 'DRONE' ? layerDrones : layerMissiles;
      renderStrikeTrack(t, layer);
    });

    renderStrikePanel(tracks);
    // Update sidebar cache and re-render
    cachedStrikes = tracks;
    renderIncidents(lastIncidents);
    renderAlerts(lastAlerts);
  } catch (e) { console.debug('Strike poll error:', e.message); }
}

// ── Main refresh loop ─────────────────────────────────────────────────────────
async function refresh() {
  try {
    const [incRes, alertRes, snapRes, healthRes] = await Promise.all([
      fetch(`${API}/incidents?limit=50`).then(r=>r.json()),
      fetch(`${API}/alerts?limit=20`).then(r=>r.json()),
      fetch(`${API}/map/snapshot`).then(r=>r.json()),
      fetch(`${API}/health`).then(r=>r.json())
    ]);

    // Clear dynamic layers (not strike layers — those are managed by refreshStrikes())
    [layerStrikes, layerFire, layerSeismic, layerProtests, layerMaritime, layerIncidents]
      .forEach(l => l.clearLayers());

    // Plot map layers
    (snapRes.strikes       || []).forEach(p => dot(p.lat, p.lon, '#ef5350', 'Strike/Explosion', layerStrikes));
    (snapRes.droneStrikes  || []).forEach(p => dot(p.lat, p.lon, '#ff6d00', 'Drone Strike (confirmed)', layerStrikes));
    (snapRes.missileStrikes|| []).forEach(p => dot(p.lat, p.lon, '#d50000', 'Missile Strike (confirmed)', layerStrikes));
    (snapRes.fireHotspots  || []).forEach(p => dot(p.lat, p.lon, '#ff9800', 'Thermal Anomaly', layerFire));
    (snapRes.seismicEvents || []).forEach(p => dot(p.lat, p.lon, '#ffeb3b', 'Seismic Event', layerSeismic));
    (snapRes.protests      || []).forEach(p => dot(p.lat, p.lon, '#66bb6a', 'Protest/Riot', layerProtests));
    (snapRes.maritimeIncidents || []).forEach(p => dot(p.lat, p.lon, '#29b6f6', 'Maritime', layerMaritime));
    (incRes.incidents || []).forEach(incidentMarker);

    // Sidebar — store in module-level cache so live-data refreshes can re-render
    lastIncidents = incRes.incidents || [];
    lastAlerts    = alertRes.alerts  || [];
    renderIncidents(lastIncidents);
    renderAlerts(lastAlerts);

    // Header badges
    document.getElementById('badge-cycle').textContent = `Cycle: ${healthRes.cycleCount}`;
    document.getElementById('badge-events').textContent = `Events: ${healthRes.totalEvents}`;
    document.getElementById('badge-alerts').textContent = `Alerts: ${alertRes.alerts?.length || 0}`;
    document.getElementById('badge-time').textContent = new Date().toUTCString().slice(0,25);

    // Status bar
    document.getElementById('sb-total-events').textContent    = `Total Events: ${healthRes.totalEvents}`;
    document.getElementById('sb-total-incidents').textContent = `Total Incidents: ${healthRes.totalIncidents}`;
    document.getElementById('sb-dedup').textContent           = `Dedup Registry: ${healthRes.dedupSize}`;
    const agentOk = Object.values(healthRes.agentHealth || {}).every(Boolean);
    document.getElementById('sb-agents').textContent = `Agents: ${agentOk ? 'ALL UP' : 'DEGRADED'}`;

  } catch (e) {
    console.warn('GCC refresh error:', e.message);
  }
}

// ── Naval Fleet layer ─────────────────────────────────────────────────────────

const NAVAL_ALERT_COLORS = {
  HIGH_READINESS : '#ef5350',
  ELEVATED       : '#ffa726',
  ROUTINE        : '#7986cb'
};

const VESSEL_CLASS_LABEL = {
  AIRCRAFT_CARRIER         : 'CVN',
  GUIDED_MISSILE_DESTROYER : 'DDG',
  GUIDED_MISSILE_CRUISER   : 'CG',
  AMPHIBIOUS_ASSAULT       : 'LHD/LHA',
  AMPHIBIOUS_TRANSPORT     : 'LPD/LSD',
  FAST_ATTACK_SUB          : 'SSN',
  LOGISTICS_SUPPORT        : 'AOE/T-AO',
  MINE_COUNTERMEASURES     : 'MCM',
  ALLIED_SURFACE           : 'Allied',
  UNKNOWN                  : '?'
};

/** Rotated ship icon div, sized and coloured by alert level. */
function shipIcon(v) {
  const col  = NAVAL_ALERT_COLORS[v.alertLevel] || '#7986cb';
  const rot  = v.heading != null ? v.heading : v.courseTrue || 0;
  const size = v.alertLevel === 'ROUTINE' ? 14 : 18;
  const glow = v.alertLevel === 'HIGH_READINESS'
    ? `filter:drop-shadow(0 0 5px ${col})` : '';
  return L.divIcon({
    html: `<div style="transform:rotate(${rot}deg);color:${col};font-size:${size}px;` +
          `line-height:1;text-shadow:0 0 3px #000;${glow}">${v.classIcon}</div>`,
    iconSize  : [size, size],
    iconAnchor: [size / 2, size / 2],
    className : ''
  });
}

/** Render one naval vessel on the map with a rich popup. */
function renderNavalVessel(v) {
  const col      = NAVAL_ALERT_COLORS[v.alertLevel] || '#7986cb';
  const classLbl = VESSEL_CLASS_LABEL[v.vesselClass] || v.vesselClass;
  const alertTag = v.alertLevel !== 'ROUTINE'
    ? `<span style="color:${col}">[${v.alertLevel.replace(/_/g,' ')}]</span> ` : '';
  const reasonLine = v.alertReason
    ? `<em style="color:#ffa726;font-size:10px">${v.alertReason}</em><br>` : '';
  const spdKts   = v.speedKnots.toFixed(1);
  const spdKmh   = v.speedKmh.toFixed(0);
  const srcBadge = v.dataSource === 'AIS_LIVE'
    ? '<b style="color:#69f0ae">● AIS LIVE</b>'
    : '<span style="color:#ffa726">⚠ OSINT SYNTHETIC</span>';

  const marker = L.marker([v.lat, v.lon], { icon: shipIcon(v) });
  marker.bindPopup(`
    <b style="color:${col}">${alertTag}${v.name}</b>
    <span style="color:#90a4ae;font-size:10px"> ${v.hullNumber} · ${classLbl}</span><br>
    ${reasonLine}
    ${srcBadge}<br>
    <hr style="border-color:#1e3a5f;margin:4px 0">
    Flag: ${v.flag}<br>
    Area: ${v.operationalArea}<br>
    Speed: ${spdKts} kts (${spdKmh} km/h)<br>
    Course: ${v.courseTrue != null ? v.courseTrue.toFixed(0)+'°T' : '—'}<br>
    Status: ${v.navStatus}<br>
    MMSI: <code>${v.mmsi}</code>
    <div style="color:#546e7a;font-size:9px;margin-top:3px">
      Updated: ${new Date(v.updatedAt).toLocaleTimeString()}
    </div>
  `, { maxWidth: 270 });
  marker.addTo(layerNaval);
}

/** Render the Fleet Watch panel (bottom-left sidebar cards). */
function renderFleetPanel(vessels, total) {
  const el  = document.getElementById('fleet-list');
  const cnt = document.getElementById('fl-count');
  cnt.textContent = `${total} VESSELS`;

  if (!vessels.length) {
    el.innerHTML = '<div style="color:#546e7a;font-size:10px">No vessel data available.</div>';
    return;
  }
  el.innerHTML = vessels.slice(0, 14).map(v => {
    const col      = NAVAL_ALERT_COLORS[v.alertLevel] || '#7986cb';
    const classLbl = VESSEL_CLASS_LABEL[v.vesselClass] || v.vesselClass;
    const srcBadge = v.dataSource === 'AIS_LIVE'
      ? '<span style="color:#69f0ae;font-size:9px;font-weight:700">● AIS LIVE</span>'
      : '<span style="color:#ffa726;font-size:9px">⚠ OSINT SYNTH</span>';
    return `
      <div class="sh-card ${v.alertLevel}"
           onclick="map.flyTo([${v.lat},${v.lon}],7)"
           style="border-left-color:${col}">
        <div class="sh-head">
          <span class="sh-name" style="color:${col}">${v.classIcon} ${v.name}</span>
          <span class="sh-hull">${v.hullNumber}</span>
          <span class="sh-alert" style="color:${col}">${v.alertLevel.replace(/_/g,' ')}</span>
        </div>
        ${v.alertReason ? `<div class="sh-reason">${v.alertReason.split('—')[0]}</div>` : ''}
        <div class="sh-meta">
          <span>${classLbl}</span>
          <span>⚓ ${v.speedKnots.toFixed(1)} kts</span>
          <span>📍 ${v.operationalArea}</span>
          <span style="color:#546e7a">${v.flag}</span>
          ${srcBadge}
        </div>
      </div>`;
  }).join('');
}

/** 60 s fleet-only refresh. */
async function refreshFleet() {
  try {
    const res     = await fetch(`${API}/naval/fleet`).then(r => r.json());
    const vessels = res.vessels || [];
    layerNaval.clearLayers();
    vessels.forEach(renderNavalVessel);
    renderFleetPanel(vessels, res.total || 0);

    const hr = res.highReadiness || 0;
    document.getElementById('badge-fleet').textContent =
      `⚓ ${res.total || 0}${hr ? ' / ⚠ ' + hr : ''}`;
    document.getElementById('badge-fleet').style.borderColor =
      hr ? '#ef5350' : '#1a237e';
    document.getElementById('badge-fleet').style.color =
      hr ? '#ef9a9a' : '#7986cb';
    document.getElementById('sb-fleet').textContent =
      `⚓ Vessels: ${res.total || 0} (${hr} high-readiness)`;
    // Update sidebar cache and re-render incidents/alerts with fresh vessel data
    cachedVessels = vessels;
    renderIncidents(lastIncidents);
    renderAlerts(lastAlerts);
  } catch (e) { console.debug('Fleet poll error:', e.message); }
}

// ── Missile Launch layer ──────────────────────────────────────────────────────

const ML_STATUS_COLOR = {
  LAUNCH_DETECTED : '#ffd740',
  IN_FLIGHT       : '#ff6d00',
  TERMINAL_PHASE  : '#ff1744',
  IMPACT          : '#795548',
  INTERCEPTED     : '#69f0ae',
  ABORTED         : '#546e7a'
};

/**
 * Compute a parabolic arc between two points on the map.
 * arcHeight controls the maximum latitude offset at the midpoint.
 */
function missileArcPoints(lat1, lon1, lat2, lon2, n = 24, arcHeight = 5.5) {
  const pts = [];
  for (let i = 0; i <= n; i++) {
    const t   = i / n;
    const lat = lat1 + (lat2 - lat1) * t;
    const lon = lon1 + (lon2 - lon1) * t;
    // Parabolic bulge — peaks at midpoint, northward (pole direction for MRBM trajectories)
    const offset = arcHeight * Math.sin(t * Math.PI);
    pts.push([lat + offset, lon]);
  }
  return pts;
}

/** Render one missile launch arc + site marker + current-position dot on the map. */
function renderMissileLaunchArc(m) {
  const col = ML_STATUS_COLOR[m.launchStatus] || '#ff6d00';
  const isHypersonic = m.weaponType === 'HYPERSONIC_FATTAH';

  // Arc height scales with range: longer range = higher arc
  const arcH = Math.min(8, m.rangeKm / 400);

  // Full trajectory arc (faint)
  L.polyline(missileArcPoints(m.launchLat, m.launchLon, m.targetLat, m.targetLon, 24, arcH), {
    color: col, weight: 1.5, opacity: 0.30, dashArray: isHypersonic ? null : '6 5'
  }).addTo(layerMissileArc);

  // Travelled portion (bright)
  if (m.completionFraction > 0.01) {
    const midLat = m.launchLat + (m.currentLat - m.launchLat) * 0.5;
    const midLon = m.launchLon + (m.currentLon - m.launchLon) * 0.5;
    const arcMid = arcH * Math.sin(m.completionFraction * 0.5 * Math.PI);
    L.polyline([
      [m.launchLat, m.launchLon],
      [midLat + arcMid, midLon],
      [m.currentLat, m.currentLon]
    ], {
      color: col, weight: isHypersonic ? 3 : 2.5, opacity: 0.9
    }).addTo(layerMissileArc);
  }

  // Launch site — explosion burst icon
  const burstIcon = L.divIcon({
    html: `<div style="font-size:16px;animation:mlPulse 1s infinite;color:${col}">💥</div>`,
    iconSize: [20, 20], iconAnchor: [10, 10], className: ''
  });
  L.marker([m.launchLat, m.launchLon], { icon: burstIcon })
    .bindTooltip(`🚀 ${m.weaponSystem} — ${m.launchSiteName}`, { sticky: true })
    .addTo(layerMissileArc);

  // Current position — pulsing missile dot
  const eta = m.estimatedImpactAt
    ? Math.max(0, Math.round((new Date(m.estimatedImpactAt) - Date.now()) / 60000))
    : '?';
  const conf = (m.confidence * 100).toFixed(0);
  const qualBadge = m.dataQuality === 'SYNTHETIC_DRILL'
    ? '<b style="color:#ffa726">⚠ SYNTHETIC DRILL</b><br>'
    : (m.dataQuality === 'SINGLE_SOURCE' ? '<em style="color:#ffd740">SINGLE SOURCE</em><br>' : '');
  const r = m.launchStatus === 'TERMINAL_PHASE' ? 9 : 7;
  L.circleMarker([m.currentLat, m.currentLon], {
    radius: r, fillColor: col, color: '#fff', weight: 2, opacity: 1, fillOpacity: 0.95
  }).bindPopup(`
    <b style="color:${col}">🚀 ${m.weaponSystem}</b>
    <span style="color:#90a4ae;font-size:10px"> ${m.launchStatus.replace(/_/g,' ')}</span><br>
    ${qualBadge}
    <span style="color:#90a4ae;font-size:10px">📍 ${m.launchSiteName}</span><br>
    <span style="color:#ef9a9a;font-size:10px">🎯 ${m.targetRegion}</span><br>
    ${m.narrative}<br>
    <hr style="border-color:#333;margin:4px 0">
    Alt: ${m.altitudeKm.toFixed(0)} km &nbsp;|&nbsp;
    Speed: ${m.speedKmh.toLocaleString()} km/h &nbsp;|&nbsp;
    ETA: ${eta} min<br>
    Range: ${m.rangeKm.toFixed(0)} km &nbsp;|&nbsp;
    Progress: ${(m.completionFraction * 100).toFixed(0)}% &nbsp;|&nbsp;
    Conf: ${conf}%
  `, { maxWidth: 280 }).addTo(layerMissileArc);

  // Target zone (hollow circle)
  if (m.launchStatus !== 'INTERCEPTED') {
    L.circleMarker([m.targetLat, m.targetLon], {
      radius: 6, fillColor: col, color: col, weight: 1, opacity: 0.4, fillOpacity: 0.1
    }).bindTooltip(`🎯 ${m.targetRegion}`, { sticky: true }).addTo(layerMissileArc);
  }
}

/** Render the Missile Launch Alert panel cards. */
function renderMissileLaunchPanel(launches) {
  const el  = document.getElementById('missile-list');
  const cnt = document.getElementById('ml-count');
  const active = launches.filter(m => m.launchStatus !== 'IMPACT' && m.launchStatus !== 'INTERCEPTED');
  cnt.textContent = `${active.length} ACTIVE`;

  if (!launches.length) {
    el.innerHTML = '<div style="color:#546e7a;font-size:10px">No active launch events.</div>';
    return;
  }
  el.innerHTML = launches.slice(0, 8).map(m => {
    const col  = ML_STATUS_COLOR[m.launchStatus] || '#ff6d00';
    const eta  = m.estimatedImpactAt
      ? Math.max(0, Math.round((new Date(m.estimatedImpactAt) - Date.now()) / 60000))
      : '?';
    const prog = Math.round(m.completionFraction * 100);
    const qualTag = m.dataQuality === 'SYNTHETIC_DRILL'
      ? '<span style="color:#ffa726;font-size:8px">SIM</span>' : '';
    return `
      <div class="ml-card ${m.launchStatus}"
           style="border-left-color:${col}"
           onclick="map.flyTo([${m.currentLat},${m.currentLon}],6)">
        <div class="ml-head">
          <span class="ml-sys">🚀 ${m.weaponSystem}</span>
          ${qualTag}
          <span class="ml-phase" style="color:${col}">${m.launchStatus.replace(/_/g,' ')}</span>
        </div>
        <div class="ml-narr">${m.launchProvince} → ${m.targetRegion}</div>
        <div class="ml-narr" style="color:#78909c">${m.narrative.replace('[SYNTHETIC_DRILL] ','')}</div>
        <div class="ml-meta">
          <span>⬆ ${m.altitudeKm.toFixed(0)} km</span>
          <span>✈ ${(m.speedKmh/1000).toFixed(0)}k km/h</span>
          <span>⏱ ${eta}min</span>
          <span>🎯 ${(m.confidence*100).toFixed(0)}%</span>
        </div>
        <div class="ml-prog"><div class="ml-prog-bar" style="width:${prog}%;background:${col}"></div></div>
      </div>`;
  }).join('');
}

/** 15 s missile-only refresh. */
async function refreshMissileLaunches() {
  try {
    const res     = await fetch(`${API}/missiles/live`).then(r => r.json());
    const launches = res.launches || [];

    layerMissileArc.clearLayers();
    launches.forEach(renderMissileLaunchArc);
    renderMissileLaunchPanel(launches);

    const active = res.active || 0;
    const badge  = document.getElementById('badge-missiles');
    if (badge) {
      badge.textContent = `🚀 ${launches.length}${active ? ' / ⚠ ' + active : ''}`;
      badge.style.borderColor = active ? '#ff1744' : '#b71c1c';
      badge.style.color       = active ? '#ff8a80' : '#ef9a9a';
    }

    // Inject missile FLASH alerts into sidebar
    cachedMissiles = launches;
    renderIncidents(lastIncidents);
    renderAlerts(lastAlerts);
  } catch (e) { console.debug('Missile poll error:', e.message); }
}

// ── CIA / Intelligence Feed ───────────────────────────────────────────────────
/**
 * Intelligence bulletin pool — OSINT-derived, publicly available.
 * Presented in CIA/DIA style for situational awareness on the command dashboard.
 * Each item: { pri, agency, cls, text, loc }
 */
const INTEL_POOL = [
  { pri:'FLASH',     agency:'NGA',  cls:'TS/SI/TK', loc:'Natanz',      text:'SATELLITE: Overhead imagery confirms increased centrifuge hall activity at Natanz FEP. IR signature elevated 12%. 24-hr cadence tasking active.' },
  { pri:'FLASH',     agency:'NSA',  cls:'TS/SI',    loc:'Fordow',      text:'SIGINT: Encrypted HF burst traffic from Fordow Enrichment Plant elevated ×4 baseline. Assessed: production milestone coordination.' },
  { pri:'IMMEDIATE', agency:'DIA',  cls:'S/NF',     loc:'Parchin',     text:'HUMINT: Source reporting indicates truck convoy activity at Parchin Site-7 overnight. Pattern consistent with material transfer. Confidence: MODERATE.' },
  { pri:'IMMEDIATE', agency:'CIA',  cls:'TS/SI',    loc:'Tabriz AB',   text:'HUMINT/SIGINT: IRGC Aerospace Command elevated REDCON-2 at Tabriz Air Base. F-4E and UAV units confirmed deployed forward.' },
  { pri:'IMMEDIATE', agency:'JSOC', cls:'S/NF',     loc:'Strait',      text:'ELINT: Iranian fast boat swarm pattern detected Strait of Hormuz grid reference 2647N-5632E. Assessed: blockade rehearsal exercise.' },
  { pri:'PRIORITY',  agency:'CIA',  cls:'TS',       loc:'Isfahan',     text:'SATELLITE: UCF-2 at Isfahan NTC showing 48-hr continuous operations. UF₆ cylinder staging area active. Consistent with feedstock preparation.' },
  { pri:'PRIORITY',  agency:'DIA',  cls:'S/NF',     loc:'Arak',        text:'GEOINT: New concrete pour detected at Arak IR-40 redesign project. Estimated completion T+6 weeks. Secondary containment structure visible.' },
  { pri:'PRIORITY',  agency:'NRO',  cls:'TS/SI/TK', loc:'Natanz',      text:'IMAGERY: Tunnel entrance at Natanz Pilot Fuel Enrichment Plant (PFEP) shows increased vehicle throughput. Underground expansion assessed ongoing.' },
  { pri:'PRIORITY',  agency:'NSA',  cls:'TS/SI',    loc:'Gulf',        text:'SIGINT: IRIAF ATC communications indicate 3× Su-24MK aircraft vectored to Gulf patrol sector. Potential reconnaissance of US carrier strike group.' },
  { pri:'PRIORITY',  agency:'CIA',  cls:'TS',       loc:'Qom',         text:'HUMINT: Fordow site personnel reportedly placed on extended duty rotation. Suggests 24-hr operational tempo increase. Corroborates SIGINT.' },
  { pri:'PRIORITY',  agency:'DIA',  cls:'S/NF',     loc:'W. Iran',     text:'HUMINT: Source reports IRGC ballistic missile brigade conducted night-movement rehearsal, western Iran. 4× Shahab-3 TELs observed on convoy.' },
  { pri:'ROUTINE',   agency:'NGA',  cls:'U/FOUO',   loc:'Bushehr',     text:'SATELLITE: Bushehr NPP secondary coolant loop maintenance visible in latest imagery. Normal operational cycle. No proliferation indicators.' },
  { pri:'ROUTINE',   agency:'CIA',  cls:'S/NF',     loc:'Tehran',      text:'OSINT: AEOI spokesperson statement assessed as strategic messaging — downplaying enrichment activity ahead of IAEA Board of Governors session.' },
  { pri:'ROUTINE',   agency:'DIA',  cls:'U/FOUO',   loc:'Saghand',     text:'SATELLITE: Saghand uranium mine haul road traffic nominal. Crusher facility operating. No anomalies. Quarterly yellowcake shipment to Isfahan expected.' },
  { pri:'IMMEDIATE', agency:'NSA',  cls:'TS/SI',    loc:'IRGC-N',      text:'SIGINT: IRGC Navy encrypted mesh-radio traffic elevated in Strait of Hormuz sector. Assessment: coordinated patrol surge, possible provocation drill.' },
  { pri:'FLASH',     agency:'DIA',  cls:'TS/SI',    loc:'Khondab',     text:'HUMINT: Corroborated reporting indicates experimental heavy water production resumed Khondab Site. Assessed with HIGH confidence.' },
];

let _intelIndex = 0;

/** Render the next batch of intelligence items in the CIA feed panel. */
function renderIntelFeed() {
  const el   = document.getElementById('intel-list');
  const tsEl = document.getElementById('intel-ts');
  if (!el) return;

  // Contextual items — inject alerts from live data into pool
  const liveItems = [];
  (cachedMissiles || []).forEach(m => {
    if (m.flightPhase === 'TERMINAL_PHASE' || m.flightPhase === 'IN_FLIGHT') {
      liveItems.push({ pri:'FLASH', agency:'NORAD', cls:'TS/SI', loc:m.launchProvince || 'IRN',
        text:`MASINT: Ballistic missile launch detected from ${m.launchSiteName || 'Iranian territory'}. System: ${m.weaponSystem}. Tracking active. Impact est. ${m.estimatedImpactAt ? new Date(m.estimatedImpactAt).toLocaleTimeString() : 'TBD'}.` });
    }
  });
  (cachedAircraft || []).filter(a => a.alertLevel === 'CRITICAL').forEach(a => {
    liveItems.push({ pri:'FLASH', agency:'FAA/NORAD', cls:'S/NF', loc:a.country,
      text:`ADS-B ALERT: ${a.callsign || a.icao24} squawk ${a.squawk || '????'} — ${a.alertReason || 'CRITICAL alert level'}. Position: ${a.lat?.toFixed(2)}°N ${a.lon?.toFixed(2)}°E.` });
  });

  const combined = [...liveItems, ...INTEL_POOL];
  // Show 4 items, rotating through the pool
  const items = [];
  for (let i = 0; i < 4; i++) {
    items.push(combined[(_intelIndex + i) % combined.length]);
  }
  _intelIndex = (_intelIndex + 1) % combined.length;

  const priColor = { FLASH:'#ff1744', IMMEDIATE:'#ff6d00', PRIORITY:'#ffd740', ROUTINE:'#546e7a' };
  el.innerHTML = items.map(item => `
    <div class="intel-item ${item.pri}">
      <div class="intel-hdr">
        <span class="intel-pri ${item.pri}">${item.pri}</span>
        <span class="intel-agency">${item.agency}</span>
        <span class="intel-class">[${item.cls}]</span>
        <span style="margin-left:auto;color:${priColor[item.pri] || '#546e7a'};font-size:8px">${item.loc}</span>
      </div>
      <div class="intel-text">${item.text}</div>
      <div class="intel-ts">${new Date().toUTCString().replace('GMT','Z').slice(0,-1)} · SOURCE PROTECTED · ORCON</div>
    </div>`).join('');

  if (tsEl) tsEl.textContent = new Date().toLocaleTimeString();
}

// Boot: run all loops immediately then at their respective intervals
renderNuclearSites();
renderIntelFeed();
refresh();
refreshStrikes();
refreshAirspace();
refreshFleet();
refreshMissileLaunches();
setInterval(refresh,                REFRESH_MS);
setInterval(refreshStrikes,         STRIKE_POLL_MS);
setInterval(refreshAirspace,        AIRSPACE_POLL_MS);
setInterval(refreshFleet,           FLEET_POLL_MS);
setInterval(refreshMissileLaunches, MISSILE_POLL_MS);

// 1 s dead-reckoning animation — moves aircraft markers continuously between API polls
setInterval(animateAircraftTracks,  1_000);

// Intel feed rotates every 20 s
setInterval(renderIntelFeed, 20_000);


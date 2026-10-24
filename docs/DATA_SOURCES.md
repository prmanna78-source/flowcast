# FlowCast Data Sources & Provider Integrations

FlowCast operates strictly on **genuine data providers**. This document details each external provider, API endpoint, credentials, and error-handling behavior.

---

## 1. TomTom Orbis Traffic Incidents API

- **Provider**: TomTom N.V.
- **Endpoint**: `https://api.tomtom.com/maps/orbis/traffic/incidents/details?apiVersion=2`
- **Required Header**: `Attributes: incidents` *(Crucial requirement for Orbis v2 API)*
- **Data Retrieved**:
  - Incident ID, category (`jam`, `roadClosed`, `accident`, etc.)
  - Severity (`minor`, `moderate`, `major`, `undefined`)
  - Description from events array (e.g., "Stationary traffic", "Bridge closed", "Queuing traffic")
  - Road naming from `from` and `to` properties (e.g., "Great Northen Trunk Road → Jyothi Nagar Street")
  - Delay in seconds (`delayInSeconds`)
  - GeoJSON coordinates for spatial rendering
- **Geographic Scope**: Bounding box covering Visakhapatnam (`83.0985, 17.5668` to `83.3385, 17.8068`).
- **Failure State**: Returns `CONFIG_MISSING` if key is absent; `RATE_LIMITED` on HTTP 429; `UNAVAILABLE` if network is unreachable.

---

## 2. TomTom Traffic Flow Services

- **Provider**: TomTom N.V.
- **Endpoints**:
  - Vector/Raster Tiles: `https://api.tomtom.com/maps/orbis/traffic/flow/raster/tile/{z}/{x}/{y}?apiVersion=2&style=dark&tileSize=256&key={key}`
  - Flow Segment Data: `https://api.tomtom.com/traffic/services/4/flowSegmentData/absolute/10/json?point={lat},{lon}&key={key}`
- **Data Retrieved**:
  - Real-time traffic flow congestion overlay
  - Observed speed vs. free-flow speed (km/h)
  - Travel time vs. free-flow baseline
  - Measurement confidence score (0-100)
- **Licensing/Restrictions**: Browser key must be restricted to authorized origin domains.

---

## 3. TomTom Routing API

- **Provider**: TomTom N.V.
- **Endpoint**: `https://api.tomtom.com/routing/1/calculateRoute/{origin_lat},{origin_lon}:{dest_lat},{dest_lon}/json?key={key}&traffic=true`
- **Data Retrieved**:
  - Distance in meters (`lengthInMeters`)
  - Estimated travel time with live traffic (`travelTimeInSeconds`)
  - Real-time delay component (`trafficDelayInSeconds`)
  - Full route polyline coordinates (`legs[].points`)
- **Usage**: Used to power the Route Comparison and Disruption Assessment engines.

---

## 4. TomTom Search & Geocoding API

- **Provider**: TomTom N.V.
- **Endpoint**: `https://api.tomtom.com/search/2/search/{query}.json?limit=5&countrySet=IN&typeahead=true&key={key}`
- **Data Retrieved**:
  - Freeform address and place name (POI)
  - Geographic latitude and longitude coordinates
  - Region bias: biased towards Visakhapatnam center coordinates (`17.6868, 83.2185`).

---

## 5. Open-Meteo Weather API

- **Provider**: Open-Meteo (Non-commercial open data / CC-BY 4.0)
- **Endpoints**:
  - Current Conditions: `https://api.open-meteo.com/v1/forecast?latitude=17.6868&longitude=83.2185&current=temperature_2m,relative_humidity_2m,precipitation,wind_speed_10m,weather_code&timezone=Asia/Kolkata`
  - 24-Hour Forecast: `https://api.open-meteo.com/v1/forecast?latitude=17.6868&longitude=83.2185&hourly=temperature_2m,precipitation_probability,precipitation,wind_speed_10m,weather_code&forecast_hours=24&timezone=Asia/Kolkata`
- **Authentication**: Keyless open API.
- **Data Retrieved**:
  - Temperature (°C), relative humidity (%)
  - Surface precipitation (mm)
  - Wind speed (km/h)
  - WMO weather codes (rain, thunderstorms, clear sky, overcast)
- **Rules**: Weather is never used to fabricate road conditions without matching spatial/traffic evidence.

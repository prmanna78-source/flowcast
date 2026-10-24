# FlowCast System Architecture & Design
**Real-Time Mobility Intelligence, Weather-Aware Routing & Road Disruption Assessment**
*Target Deployment: Visakhapatnam, Andhra Pradesh, India*

---

## 1. Architectural Overview

FlowCast is engineered with a layered, decoupled architecture combining a **Java 21 / Spring Boot 3.4.4** backend with a high-performance, client-side **MapLibre GL** geospatial visualization interface.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                       FLOWCAST CLIENT INTERFACE                             │
│     PWA Shell (sw.js, manifest) · MapLibre GL 5.6.0 · Responsive Dark CSS   │
│   Live Map · Route Planner · Disruption Assessment · Hazard Reporting       │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │ HTTP / JSON REST APIs (/api/v1)
┌──────────────────────────────────────▼──────────────────────────────────────┐
│                    FLOWCAST SPRING BOOT CORE SERVICES                       │
│  ┌────────────────────────┐  ┌────────────────────────┐  ┌────────────────┐ │
│  │   ProviderService      │  │DisruptionAssessmentSvc │  │ForecastService │ │
│  │(Traffic, Weather, Route│  │(Corridor, Exposure,    │  │(Chronological  │ │
│  │ Search & Incident Data)│  │ Weather Factors, Delay)│  │ Baseline & ML) │ │
│  └───────────┬────────────┘  └───────────┬────────────┘  └────────┬───────┘ │
│              │                           │                        │         │
│  ┌───────────▼────────────┐  ┌───────────▼────────────┐  ┌────────▼───────┐ │
│  │   CommunityReportSvc   │  │   SourceHealthService  │  │DataCollectorSvc│ │
│  │ (Rate limit, validation│  │ (Latency, Diagnostics, │  │ (Scheduled Vis-│ │
│  │ lifecycle state store) │  │  Coverage Transparency)│  │  akhapatnam)   │ │
│  └────────────────────────┘  └────────────────────────┘  └────────────────┘ │
└──────────────────────┬───────────────────────────────┬──────────────────────┘
                       │                               │
        ┌──────────────▼──────────────┐ ┌──────────────▼─────────────┐
        │    EXTERNAL DATA PROVIDERS  │ │   DATA PERSISTENCE TIER    │
        │  • TomTom Orbis Incidents   │ │  • PostgreSQL 16 / PostGIS │
        │  • TomTom Traffic Flow      │ │  • Flyway Migrations (V1,V2│
        │  • TomTom Routing API (v1)  │ │  • In-Memory Ring Buffer   │
        │  • TomTom Search/Geocoding  │ │    Failover Layer          │
        │  • Open-Meteo Weather API   │ └────────────────────────────┘
        └─────────────────────────────┘
```

---

## 2. Core Backend Modules

### 2.1. Provider Integration Layer (`com.flowcast.provider`)
- **`TomTomClient`**: Interfaces with TomTom's developer platform:
  - *Orbis Traffic Incidents*: Fetches geo-referenced incidents with mandatory `Attributes: incidents` headers, extracting severity, delay duration, events, and road segments.
  - *Traffic Flow Segments*: Queries `/traffic/services/4/flowSegmentData/absolute/10/json` for current vs. free-flow speeds.
  - *Traffic-Aware Routing*: Queries `/routing/1/calculateRoute/{locations}/json` with `traffic=true` to obtain real traffic delay times and exact geometry.
  - *Geocoding & Search*: Queries `/search/2/search/{query}.json` with location bias for Visakhapatnam landmarks and addresses.
- **`OpenMeteoClient`**: Queries Open-Meteo's API without API keys:
  - Current weather (temperature, relative humidity, wind speed, precipitation, WMO code).
  - 24-hour hourly forecast (precipitation probability, rain mm, wind speeds).

### 2.2. Disruption Assessment Engine (`DisruptionAssessmentService`)
FlowCast's signature feature. Evaluates journey disruption exposure by intersecting:
1. **Route Delays**: Calculates traffic delay duration as a percentage of total travel time.
2. **Corridor Incidents**: Projects an approximate 1.5 km buffer along route coordinates to identify intersecting jams, stationary traffic, or road closures.
3. **Weather Impacts**: Evaluates precipitation levels (slick surface warnings), high winds (>40 km/h), and thunderstorm conditions.
4. **Exposure Categorization**: Classifies routes into `LOWER_EXPOSURE`, `MODERATE_EXPOSURE`, `ELEVATED_EXPOSURE`, `HIGH_EXPOSURE`, or `INSUFFICIENT_DATA`.
5. **Confidence Rating**: Rates data confidence as `HIGH`, `MEDIUM`, `LOW`, or `UNCERTAIN` based on measurement freshness and coverage completeness.

### 2.3. Community Hazard Reporting (`CommunityReportService`)
- Provides user-submitted ground-truth hazards (`Pothole`, `Waterlogging`, `Road Obstruction`, `Broken Signal`, `Reported Accident`, `Road Closure`, `Other Hazard`).
- Enforces strict input validation and rate limiting (preventing spam).
- Manages lifecycle states: `REPORTED` -> `UNVERIFIED` -> `VERIFIED` -> `RESOLVED`.
- Clearly labelled separately from commercial provider data to preserve data integrity.

### 2.4. Historical Collector & Forecasting Pipeline (`DataCollectorService`, `ForecastService`)
- Scheduled Java service sampling 6 key Visakhapatnam arterial corridors (NAD Junction, Gajuwaka, Maddilapalem, Dwaraka Nagar, Beach Road, Pendurthi).
- Controlled by `FLOWCAST_TRAFFIC_HISTORY_ENABLED` and `FLOWCAST_TRAFFIC_TRAINING_PERMISSION`.
- Enforces strict chronological walk-forward evaluation (no random cross-validation to prevent data leakage).
- Evaluates baseline MAE against segment-hour medians.

---

## 3. Data Flow & Fault Tolerance
- **Fail-Closed Principle**: Missing or unavailable keys return explicit status codes (`CONFIG_MISSING`, `RATE_LIMITED`, `UNAVAILABLE`) rather than fabricated placeholder numbers.
- **Offline / PWA Resilience**: Service Worker partitions `/api/` network-only calls (preventing stale traffic from being passed off as real-time) and caches static app shell assets.

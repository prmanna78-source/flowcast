package com.flowcast.service;

import com.flowcast.config.FlowCastProperties;
import com.flowcast.dto.SourceStatusDto;
import com.flowcast.provider.OpenMeteoClient;
import com.flowcast.provider.TomTomClient;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class SourceHealthService {
    private final FlowCastProperties properties;
    private final TomTomClient tomtom;
    private final OpenMeteoClient weather;
    private final Optional<JdbcTemplate> jdbcTemplate;

    private Instant lastCheckTime = Instant.now();

    public SourceHealthService(FlowCastProperties properties, TomTomClient tomtom,
                               OpenMeteoClient weather, Optional<JdbcTemplate> jdbcTemplate) {
        this.properties = properties;
        this.tomtom = tomtom;
        this.weather = weather;
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<SourceStatusDto> checkSources() {
        List<SourceStatusDto> list = new ArrayList<>();
        Instant now = Instant.now();
        lastCheckTime = now;

        boolean serverKeyConfigured = !properties.getTomtomServerKey().isBlank();
        boolean browserKeyConfigured = !properties.getTomtomBrowserKey().isBlank();

        // 1. TomTom Display Tiles
        list.add(new SourceStatusDto(
                "TomTom Orbis Display Tiles",
                browserKeyConfigured ? "READY" : "CONFIG_MISSING",
                browserKeyConfigured,
                browserKeyConfigured ? now : null,
                now,
                browserKeyConfigured ? null : "MISSING_FLOWCAST_TOMTOM_BROWSER_KEY",
                null,
                0L,
                "Visakhapatnam, Andhra Pradesh, India",
                List.of("Interactive MapLibre GL Raster Tiles", "Dark Street Style", "Zoom levels 1-22"),
                browserKeyConfigured ? "Configured with browser display key." : "Add FLOWCAST_TOMTOM_MAP_KEY to .env."
        ));

        // 2. TomTom Traffic Flow
        list.add(new SourceStatusDto(
                "TomTom Traffic Flow",
                serverKeyConfigured ? "READY" : "CONFIG_MISSING",
                serverKeyConfigured,
                serverKeyConfigured ? now : null,
                now,
                serverKeyConfigured ? null : "MISSING_FLOWCAST_TOMTOM_SERVER_KEY",
                null,
                0L,
                "Visakhapatnam Metropolitan Region",
                List.of("Traffic Flow Raster Overlay", "Segment Current Speed (km/h)", "Free-flow Baseline Speed"),
                serverKeyConfigured ? "Configured with live server key." : "Add FLOWCAST_TOMTOM_SERVER_KEY to .env."
        ));

        // 3. TomTom Orbis Incidents
        list.add(new SourceStatusDto(
                "TomTom Orbis Traffic Incidents",
                serverKeyConfigured ? "READY" : "CONFIG_MISSING",
                serverKeyConfigured,
                serverKeyConfigured ? now : null,
                now,
                serverKeyConfigured ? null : "MISSING_FLOWCAST_TOMTOM_SERVER_KEY",
                null,
                0L,
                "Visakhapatnam (bbox: 83.0985, 17.5668 to 83.3385, 17.8068)",
                List.of("Real-time Delay Quantification", "Road Closures", "Stationary/Queuing Traffic Jams", "Incident Geometry"),
                serverKeyConfigured ? "Configured with live server key." : "Configure FLOWCAST_TOMTOM_SERVER_KEY in .env."
        ));

        // 4. TomTom Routing
        list.add(new SourceStatusDto(
                "TomTom Traffic-Aware Routing",
                serverKeyConfigured ? "READY" : "CONFIG_MISSING",
                serverKeyConfigured,
                serverKeyConfigured ? now : null,
                now,
                serverKeyConfigured ? null : "MISSING_FLOWCAST_TOMTOM_SERVER_KEY",
                null,
                0L,
                "India National & Regional Road Network",
                List.of("Real-Time Delay Duration", "Alternative Route Geometry", "Distance & Duration Calculation"),
                serverKeyConfigured ? "Configured with live server key." : "Configure FLOWCAST_TOMTOM_SERVER_KEY in .env."
        ));

        // 5. TomTom Search / Geocoding
        boolean searchConfigured = serverKeyConfigured || !properties.getTomtomSearchKey().isBlank();
        list.add(new SourceStatusDto(
                "TomTom Search & Geocoding",
                searchConfigured ? "READY" : "CONFIG_MISSING",
                searchConfigured,
                searchConfigured ? now : null,
                now,
                searchConfigured ? null : "MISSING_SEARCH_KEY",
                null,
                0L,
                "Visakhapatnam & India POI / Addresses",
                List.of("Typeahead Address Autocomplete", "Location Bias Coordinates", "Reverse Geocoding"),
                searchConfigured ? "Configured for location search & geocoding." : "Set FLOWCAST_TOMTOM_SEARCH_KEY (falls back to SERVER_KEY)."
        ));

        // 6. Open-Meteo Weather
        boolean weatherEnabled = properties.isOpenMeteoEnabled();
        list.add(new SourceStatusDto(
                "Open-Meteo Weather API",
                weatherEnabled ? "READY" : "DISABLED",
                weatherEnabled,
                weatherEnabled ? now : null,
                now,
                null,
                null,
                0L,
                "Visakhapatnam (17.6868° N, 83.2185° E)",
                List.of("Current Temperature & Humidity", "Precipitation Rate (mm)", "Wind Speed & Direction", "24-Hour Forecast"),
                "Enabled by default. Free public weather forecast provider."
        ));

        // 7. Database & Historical Storage
        boolean dbConnected = false;
        Long dbLatency = null;
        if (jdbcTemplate.isPresent()) {
            try {
                long start = System.currentTimeMillis();
                jdbcTemplate.get().queryForObject("SELECT 1", Integer.class);
                dbConnected = true;
                dbLatency = System.currentTimeMillis() - start;
            } catch (Exception ignored) {
            }
        }
        list.add(new SourceStatusDto(
                "PostgreSQL / Historical Storage",
                dbConnected ? "READY" : "IN_MEMORY_FALLBACK",
                true,
                dbConnected ? now : null,
                now,
                dbConnected ? null : "POSTGRES_OFFLINE_USING_SAFE_MEMORY_BUFFER",
                dbLatency,
                0L,
                "Local Instance",
                List.of("Flyway Schema Migrations", "Historical Traffic Observation Storage", "Community Report Retention"),
                dbConnected ? "Connected and operational." : "PostgreSQL offline; operating safely in volatile in-memory mode."
        ));

        return list;
    }

    public Instant getLastCheckTime() {
        return lastCheckTime;
    }
}

package com.flowcast.service;

import com.flowcast.config.FlowCastProperties;
import com.flowcast.dto.SegmentDto;
import com.flowcast.dto.WeatherDto;
import com.flowcast.provider.OpenMeteoClient;
import com.flowcast.provider.TomTomClient;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class DataCollectorService {
    private static final Logger log = LoggerFactory.getLogger(DataCollectorService.class);

    public record SamplingPoint(String name, double latitude, double longitude) {}

    private final List<SamplingPoint> samplingPoints = List.of(
            new SamplingPoint("Gajuwaka Junction (NH16)", 17.6868, 83.2185),
            new SamplingPoint("NAD Junction", 17.7475, 83.2185),
            new SamplingPoint("Maddilapalem Junction", 17.7315, 83.3185),
            new SamplingPoint("Dwaraka Nagar / RTC Complex", 17.7245, 83.3075),
            new SamplingPoint("Beach Road / RK Beach", 17.7125, 83.3215),
            new SamplingPoint("Pendurthi Junction", 17.8285, 83.2015)
    );

    private final FlowCastProperties properties;
    private final TomTomClient tomtom;
    private final OpenMeteoClient weather;
    private final ForecastService forecastService;
    private final Optional<JdbcTemplate> jdbcTemplate;

    public DataCollectorService(FlowCastProperties properties, TomTomClient tomtom,
                                OpenMeteoClient weather, ForecastService forecastService,
                                Optional<JdbcTemplate> jdbcTemplate) {
        this.properties = properties;
        this.tomtom = tomtom;
        this.weather = weather;
        this.forecastService = forecastService;
        this.jdbcTemplate = jdbcTemplate;
    }

    // Run every 5 minutes (300,000 ms)
    @Scheduled(fixedRate = 300000, initialDelay = 15000)
    public void collectObservations() {
        if (!properties.isTrafficHistoryEnabled()) {
            return;
        }

        if (!tomtom.configured()) {
            log.warn("Historical collector enabled but FLOWCAST_TOMTOM_SERVER_KEY is not configured.");
            return;
        }

        log.info("Starting scheduled observation collection for {} Visakhapatnam corridors...", samplingPoints.size());
        Instant startedAt = Instant.now();
        int collectedCount = 0;

        for (SamplingPoint pt : samplingPoints) {
            try {
                SegmentDto seg = tomtom.segment(pt.latitude(), pt.longitude());
                if (seg.currentSpeedKmh() != null) {
                    collectedCount++;
                    jdbcTemplate.ifPresent(jdbc -> {
                        try {
                            jdbc.update("INSERT INTO traffic_observations (sampling_name, latitude, longitude, current_speed, free_flow_speed, travel_time_seconds, free_flow_travel_time_seconds, confidence_score, road_class, observed_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                                    pt.name(), pt.latitude(), pt.longitude(), seg.currentSpeedKmh(), seg.freeFlowSpeedKmh(), seg.currentTravelTimeSeconds(), seg.freeFlowTravelTimeSeconds(), seg.confidence(), seg.segmentId(), java.sql.Timestamp.from(Instant.now()));
                        } catch (Exception ignored) {}
                    });
                }
            } catch (Exception ex) {
                log.warn("Failed collecting flow segment for {}: {}", pt.name(), ex.getMessage());
            }
        }

        // Collect matching weather observation
        try {
            Optional<WeatherDto> w = weather.current();
            w.ifPresent(curr -> jdbcTemplate.ifPresent(jdbc -> {
                try {
                    jdbc.update("INSERT INTO weather_observations (latitude, longitude, temperature_celsius, relative_humidity_percent, wind_speed_kmh, precipitation_mm, weather_code, observed_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                            properties.getLatitude(), properties.getLongitude(), curr.temperatureCelsius(), curr.relativeHumidityPercent(), curr.windSpeedKmh(), curr.precipitationMillimetres(), curr.weatherCode(), java.sql.Timestamp.from(curr.validAt()));
                } catch (Exception ignored) {}
            }));
        } catch (Exception ex) {
            log.warn("Failed collecting weather observation: {}", ex.getMessage());
        }

        forecastService.incrementObservationCount(collectedCount);

        // Record collector run
        int finalCount = collectedCount;
        jdbcTemplate.ifPresent(jdbc -> {
            try {
                jdbc.update("INSERT INTO collector_runs (collector_name, status, records_collected, started_at, completed_at) VALUES (?, ?, ?, ?, ?)",
                        "VisakhapatnamArterials", "SUCCESS", finalCount, java.sql.Timestamp.from(startedAt), java.sql.Timestamp.from(Instant.now()));
            } catch (Exception ignored) {}
        });

        log.info("Collection finished: recorded {} flow observations.", collectedCount);
    }
}

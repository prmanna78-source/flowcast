package com.flowcast.service;

import com.flowcast.dto.CommuteProfileDto;
import com.flowcast.dto.CommuteProfileRequest;
import com.flowcast.dto.DeparturePlanRequest;
import com.flowcast.dto.DeparturePlanResponse;
import com.flowcast.dto.TripFeedbackDto;
import com.flowcast.dto.TripFeedbackRequest;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class CommuteService {
    private static final Logger log = LoggerFactory.getLogger(CommuteService.class);
    private static final ZoneId ZONE_KOLKATA = ZoneId.of("Asia/Kolkata");

    private final DepartureRecommendationService departureService;
    private final Optional<JdbcTemplate> jdbcTemplate;

    private final AtomicLong commuteIdSequence = new AtomicLong(1);
    private final AtomicLong feedbackIdSequence = new AtomicLong(1);

    private final Map<Long, CommuteProfileDto> inMemoryCommutes = new ConcurrentHashMap<>();
    private final List<TripFeedbackDto> inMemoryFeedback = new ArrayList<>();

    public CommuteService(DepartureRecommendationService departureService, Optional<JdbcTemplate> jdbcTemplate) {
        this.departureService = departureService;
        this.jdbcTemplate = jdbcTemplate;
    }

    public CommuteProfileDto saveCommute(CommuteProfileRequest request) {
        long id = commuteIdSequence.getAndIncrement();
        Instant now = Instant.now();
        int buffer = request.safetyBufferMinutes() != null ? request.safetyBufferMinutes() : 15;
        String weekdays = request.weekdays() != null && !request.weekdays().isBlank()
                ? request.weekdays().trim().toUpperCase() : "MON,TUE,WED,THU,FRI";
        boolean enabled = request.enabled() == null || request.enabled();

        // Calculate initial departure recommendation
        DeparturePlanResponse plan = null;
        try {
            plan = departureService.planDeparture(new DeparturePlanRequest(
                    request.originName(), request.originLatitude(), request.originLongitude(),
                    request.destinationName(), request.destinationLatitude(), request.destinationLongitude(),
                    request.targetArrivalTime(), null, buffer
            ));
        } catch (Exception ex) {
            log.warn("Could not calculate initial departure for commute '{}': {}", request.name(), ex.getMessage());
        }

        String departureRecommendation = plan != null ? plan.recommendedDepartureTime() : null;
        Double durationMinutes = plan != null ? plan.travelDurationMinutes() : null;
        Integer delayMinutes = plan != null ? plan.trafficDelayMinutes() : null;
        String exposure = plan != null ? plan.exposureCategory() : null;
        String status = plan != null ? plan.journeyStatus() : "READY_TO_PLAN";

        CommuteProfileDto profile = new CommuteProfileDto(
                id,
                request.name().trim(),
                request.originName().trim(),
                request.originLatitude(),
                request.originLongitude(),
                request.destinationName().trim(),
                request.destinationLatitude(),
                request.destinationLongitude(),
                request.targetArrivalTime().trim(),
                weekdays,
                buffer,
                enabled,
                now,
                departureRecommendation,
                durationMinutes,
                delayMinutes,
                exposure,
                status,
                now
        );

        inMemoryCommutes.put(id, profile);

        jdbcTemplate.ifPresent(jdbc -> {
            try {
                jdbc.update("INSERT INTO saved_commutes (id, user_id, name, origin_name, origin_latitude, origin_longitude, destination_name, destination_latitude, destination_longitude, target_arrival_time, weekdays, safety_buffer_minutes, is_enabled, last_calculated_at, last_departure_recommendation, last_estimated_duration_minutes, last_traffic_delay_minutes, last_exposure_category, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        id, "local_user", profile.name(), profile.originName(), profile.originLatitude(), profile.originLongitude(),
                        profile.destinationName(), profile.destinationLatitude(), profile.destinationLongitude(),
                        profile.targetArrivalTime(), profile.weekdays(), profile.safetyBufferMinutes(), profile.enabled(),
                        java.sql.Timestamp.from(now), profile.lastDepartureRecommendation(), profile.lastEstimatedDurationMinutes(),
                        profile.lastTrafficDelayMinutes(), profile.lastExposureCategory(), java.sql.Timestamp.from(now), java.sql.Timestamp.from(now));
            } catch (Exception e) {
                log.debug("Database insert skipped for commute: {}", e.getMessage());
            }
        });

        return profile;
    }

    public List<CommuteProfileDto> listCommutes() {
        List<CommuteProfileDto> list = new ArrayList<>(inMemoryCommutes.values());
        list.sort(Comparator.comparing(CommuteProfileDto::createdAt).reversed());
        return list;
    }

    public Optional<CommuteProfileDto> getCommute(Long id) {
        return Optional.ofNullable(inMemoryCommutes.get(id));
    }

    public Optional<CommuteProfileDto> updateCommute(Long id, CommuteProfileRequest req) {
        CommuteProfileDto existing = inMemoryCommutes.get(id);
        if (existing == null) return Optional.empty();

        int buffer = req.safetyBufferMinutes() != null ? req.safetyBufferMinutes() : existing.safetyBufferMinutes();
        String weekdays = req.weekdays() != null ? req.weekdays().trim().toUpperCase() : existing.weekdays();
        boolean enabled = req.enabled() != null ? req.enabled() : existing.enabled();

        CommuteProfileDto updated = new CommuteProfileDto(
                id,
                req.name().trim(),
                req.originName().trim(),
                req.originLatitude(),
                req.originLongitude(),
                req.destinationName().trim(),
                req.destinationLatitude(),
                req.destinationLongitude(),
                req.targetArrivalTime().trim(),
                weekdays,
                buffer,
                enabled,
                existing.lastCalculatedAt(),
                existing.lastDepartureRecommendation(),
                existing.lastEstimatedDurationMinutes(),
                existing.lastTrafficDelayMinutes(),
                existing.lastExposureCategory(),
                existing.journeyStatus(),
                existing.createdAt()
        );

        inMemoryCommutes.put(id, updated);
        return Optional.of(updated);
    }

    public Optional<CommuteProfileDto> toggleCommute(Long id) {
        CommuteProfileDto existing = inMemoryCommutes.get(id);
        if (existing == null) return Optional.empty();

        boolean newEnabled = !existing.enabled();
        CommuteProfileDto toggled = new CommuteProfileDto(
                id,
                existing.name(),
                existing.originName(),
                existing.originLatitude(),
                existing.originLongitude(),
                existing.destinationName(),
                existing.destinationLatitude(),
                existing.destinationLongitude(),
                existing.targetArrivalTime(),
                existing.weekdays(),
                existing.safetyBufferMinutes(),
                newEnabled,
                existing.lastCalculatedAt(),
                existing.lastDepartureRecommendation(),
                existing.lastEstimatedDurationMinutes(),
                existing.lastTrafficDelayMinutes(),
                existing.lastExposureCategory(),
                existing.journeyStatus(),
                existing.createdAt()
        );

        inMemoryCommutes.put(id, toggled);
        return Optional.of(toggled);
    }

    public boolean deleteCommute(Long id) {
        boolean removed = inMemoryCommutes.remove(id) != null;
        jdbcTemplate.ifPresent(jdbc -> {
            try {
                jdbc.update("DELETE FROM saved_commutes WHERE id = ?", id);
            } catch (Exception ignored) {}
        });
        return removed;
    }

    public Optional<DeparturePlanResponse> recalculateCommute(Long id) {
        CommuteProfileDto c = inMemoryCommutes.get(id);
        if (c == null) return Optional.empty();

        DeparturePlanResponse plan = departureService.planDeparture(new DeparturePlanRequest(
                c.originName(), c.originLatitude(), c.originLongitude(),
                c.destinationName(), c.destinationLatitude(), c.destinationLongitude(),
                c.targetArrivalTime(), null, c.safetyBufferMinutes()
        ));

        // Update stored profile with fresh recalculation
        CommuteProfileDto refreshed = new CommuteProfileDto(
                c.id(), c.name(), c.originName(), c.originLatitude(), c.originLongitude(),
                c.destinationName(), c.destinationLatitude(), c.destinationLongitude(),
                c.targetArrivalTime(), c.weekdays(), c.safetyBufferMinutes(), c.enabled(),
                Instant.now(), plan.recommendedDepartureTime(), plan.travelDurationMinutes(),
                plan.trafficDelayMinutes(), plan.exposureCategory(), plan.journeyStatus(), c.createdAt()
        );
        inMemoryCommutes.put(id, refreshed);

        return Optional.of(plan);
    }

    public Optional<CommuteProfileDto> getNextUpcomingCommute() {
        ZonedDateTime now = ZonedDateTime.now(ZONE_KOLKATA);
        DayOfWeek today = now.getDayOfWeek();
        String todayCode = today.name().substring(0, 3); // "MON", "TUE", etc.

        return inMemoryCommutes.values().stream()
                .filter(CommuteProfileDto::enabled)
                .filter(c -> c.weekdays().contains(todayCode) || c.weekdays().contains("ALL"))
                .min(Comparator.comparing(c -> {
                    try {
                        LocalTime arrival = LocalTime.parse(c.targetArrivalTime().trim());
                        LocalTime nowTime = now.toLocalTime();
                        long diff = java.time.Duration.between(nowTime, arrival).toMinutes();
                        return diff >= -30 ? diff : diff + 1440; // prioritize today's remaining or upcoming tomorrow
                    } catch (Exception e) {
                        return 99999L;
                    }
                }));
    }

    public TripFeedbackDto recordFeedback(TripFeedbackRequest request) {
        long id = feedbackIdSequence.getAndIncrement();
        Instant now = Instant.now();
        TripFeedbackDto fb = new TripFeedbackDto(
                id, request.commuteId(), request.targetArrivalTime(), request.actualDepartureTime(),
                request.actualArrivalTime(), request.actualDurationMinutes(), request.wasOnTime(),
                request.wasDepartureUseful(), request.disruptionExperienced(), request.feedbackNotes(), now
        );
        inMemoryFeedback.add(fb);

        jdbcTemplate.ifPresent(jdbc -> {
            try {
                jdbc.update("INSERT INTO trip_feedback (id, commute_id, target_arrival_time, actual_departure_time, actual_arrival_time, actual_duration_minutes, was_on_time, was_departure_useful, disruption_experienced, feedback_notes, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        id, fb.commuteId(), fb.targetArrivalTime(), fb.actualDepartureTime(), fb.actualArrivalTime(),
                        fb.actualDurationMinutes(), fb.wasOnTime(), fb.wasDepartureUseful(), fb.disruptionExperienced(),
                        fb.feedbackNotes(), java.sql.Timestamp.from(now));
            } catch (Exception ignored) {}
        });

        return fb;
    }

    public List<TripFeedbackDto> listFeedback() {
        return new ArrayList<>(inMemoryFeedback);
    }
}

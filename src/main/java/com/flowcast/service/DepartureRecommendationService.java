package com.flowcast.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowcast.dto.DeparturePlanRequest;
import com.flowcast.dto.DeparturePlanResponse;
import com.flowcast.dto.DisruptionAssessmentDto;
import com.flowcast.dto.IncidentDto;
import com.flowcast.dto.WeatherDto;
import com.flowcast.provider.OpenMeteoClient;
import com.flowcast.provider.TomTomClient;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class DepartureRecommendationService {

    public static final ZoneId ZONE_KOLKATA = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter TIME_FORMAT_12H = DateTimeFormatter.ofPattern("hh:mm a");
    private static final DateTimeFormatter TIME_FORMAT_24H = DateTimeFormatter.ofPattern("HH:mm");

    private final TomTomClient tomtom;
    private final OpenMeteoClient weatherClient;
    private final DisruptionAssessmentService disruptionAssessmentService;

    public DepartureRecommendationService(TomTomClient tomtom, OpenMeteoClient weatherClient,
                                          DisruptionAssessmentService disruptionAssessmentService) {
        this.tomtom = tomtom;
        this.weatherClient = weatherClient;
        this.disruptionAssessmentService = disruptionAssessmentService;
    }

    public DeparturePlanResponse planDeparture(DeparturePlanRequest request) {
        int bufferMinutes = request.safetyBufferMinutes() != null && request.safetyBufferMinutes() >= 0
                ? request.safetyBufferMinutes() : 15;

        // Parse Target Arrival Time
        LocalTime targetTime;
        try {
            targetTime = LocalTime.parse(request.arrivalTime().trim(), TIME_FORMAT_24H);
        } catch (Exception e1) {
            try {
                targetTime = LocalTime.parse(request.arrivalTime().trim(), TIME_FORMAT_12H);
            } catch (Exception e2) {
                targetTime = LocalTime.of(9, 0); // default to 09:00 if unparseable
            }
        }

        LocalDate targetDate = LocalDate.now(ZONE_KOLKATA);
        if (request.arrivalDate() != null && !request.arrivalDate().isBlank()) {
            try {
                targetDate = LocalDate.parse(request.arrivalDate().trim());
            } catch (Exception ignored) {}
        }

        ZonedDateTime targetArrival = ZonedDateTime.of(LocalDateTime.of(targetDate, targetTime), ZONE_KOLKATA);
        ZonedDateTime nowKolkata = ZonedDateTime.now(ZONE_KOLKATA);

        // If target arrival today has already passed and no explicit date was provided, assume tomorrow's commute
        if (request.arrivalDate() == null && targetArrival.isBefore(nowKolkata.minusMinutes(30))) {
            targetArrival = targetArrival.plusDays(1);
        }

        // 1. Fetch real route from TomTom
        JsonNode routeResponse = null;
        if (tomtom.configured()) {
            try {
                routeResponse = tomtom.route(
                        request.originLatitude(), request.originLongitude(),
                        request.destinationLatitude(), request.destinationLongitude()
                );
            } catch (Exception ignored) {}
        }

        if (routeResponse == null || !routeResponse.has("routes") || routeResponse.path("routes").isEmpty()) {
            // Provider route unavailable
            return fallbackUnavailable(request, targetArrival, bufferMinutes, "TomTom routing service could not compute a route for these coordinates.");
        }

        JsonNode primaryRoute = routeResponse.path("routes").get(0);
        JsonNode summary = primaryRoute.path("summary");

        int travelTimeSeconds = summary.path("travelTimeInSeconds").asInt(1800);
        int trafficDelaySeconds = summary.path("trafficDelayInSeconds").asInt(0);
        double distanceMeters = summary.path("lengthInMeters").asDouble(10000.0);

        double travelDurationMinutes = travelTimeSeconds / 60.0;
        int trafficDelayMinutes = (int) Math.round(trafficDelaySeconds / 60.0);
        double distanceKm = Math.round((distanceMeters / 1000.0) * 10.0) / 10.0;

        // 2. Fetch corridor incidents & weather for disruption assessment
        double minLon = Math.min(request.originLongitude(), request.destinationLongitude()) - 0.05;
        double maxLon = Math.max(request.originLongitude(), request.destinationLongitude()) + 0.05;
        double minLat = Math.min(request.originLatitude(), request.destinationLatitude()) - 0.05;
        double maxLat = Math.max(request.originLatitude(), request.destinationLatitude()) + 0.05;

        List<IncidentDto> incidents = List.of();
        if (tomtom.configured()) {
            try {
                incidents = tomtom.incidents(minLon, minLat, maxLon, maxLat);
            } catch (Exception ignored) {}
        }

        WeatherDto weather = weatherClient.current().orElse(null);

        DisruptionAssessmentDto assessment = disruptionAssessmentService.assessRoute(primaryRoute, incidents, weather);

        // 3. Exact Departure Time Calculation:
        // Recommended Departure = Target Arrival - Travel Duration - Safety Buffer
        long totalDeductionMinutes = Math.round(travelDurationMinutes) + bufferMinutes;
        ZonedDateTime recommendedDeparture = targetArrival.minusMinutes(totalDeductionMinutes);

        long minutesUntilDeparture = Duration.between(nowKolkata, recommendedDeparture).toMinutes();

        // 4. Journey Status Classification
        String journeyStatus;
        String statusMessage;

        if ("HIGH_EXPOSURE".equals(assessment.category())) {
            journeyStatus = "DISRUPTION_DETECTED";
            statusMessage = "Severe road disruption or major traffic bottleneck reported on corridor.";
        } else if (minutesUntilDeparture < -15) {
            journeyStatus = "DEPARTURE_MISSED";
            statusMessage = "Recommended departure time has already passed.";
        } else if (minutesUntilDeparture <= 15) {
            journeyStatus = "READY_TO_DEPART";
            statusMessage = minutesUntilDeparture <= 0
                    ? "Leave now to stay on schedule!"
                    : "Depart in " + minutesUntilDeparture + " min to reach destination on time.";
        } else {
            journeyStatus = "DEPARTURE_UPCOMING";
            statusMessage = "Departure scheduled in " + (minutesUntilDeparture / 60 > 0 ? (minutesUntilDeparture / 60) + "h " : "")
                    + (minutesUntilDeparture % 60) + "m.";
        }

        // 5. Evidence-backed decision summary
        String decisionSummary = String.format(
                "Leave by %s to reach %s by %s. Estimated travel time is %d min (including %d min traffic delay) plus your %d min safety buffer.",
                recommendedDeparture.format(TIME_FORMAT_12H),
                request.destinationName(),
                targetArrival.format(TIME_FORMAT_12H),
                Math.round(travelDurationMinutes),
                trafficDelayMinutes,
                bufferMinutes
        );

        List<String> evidence = new ArrayList<>();
        evidence.add("Route distance: " + distanceKm + " km via TomTom Traffic-Aware Routing.");
        if (trafficDelayMinutes > 0) {
            evidence.add("Live traffic adds " + trafficDelayMinutes + " min delay compared to free-flow conditions.");
        } else {
            evidence.add("Free-flow conditions currently observed along this corridor.");
        }
        if (assessment.activeIncidentsCount() > 0) {
            evidence.add(assessment.activeIncidentsCount() + " confirmed traffic incident(s) intersect this corridor.");
        }
        if (weather != null && weather.precipitationMillimetres() > 0) {
            evidence.add("Precipitation observed (" + String.format("%.1f", weather.precipitationMillimetres()) + " mm): allow extra braking distance.");
        }

        List<String> uncertainties = new ArrayList<>();
        uncertainties.add("Traffic delay estimates are based on current conditions and may fluctuate closer to departure.");
        if (assessment.coverageLimitations() != null) {
            uncertainties.addAll(assessment.coverageLimitations());
        }

        return new DeparturePlanResponse(
                request.originName(),
                request.destinationName(),
                targetArrival.format(TIME_FORMAT_12H),
                targetArrival.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                recommendedDeparture.format(TIME_FORMAT_12H),
                recommendedDeparture.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                minutesUntilDeparture,
                travelDurationMinutes,
                trafficDelayMinutes,
                distanceKm,
                bufferMinutes,
                journeyStatus,
                statusMessage,
                decisionSummary,
                evidence,
                uncertainties,
                assessment.category(),
                assessment.weatherSummary(),
                assessment.activeIncidentsCount(),
                summary,
                routeResponse,
                Instant.now()
        );
    }

    private DeparturePlanResponse fallbackUnavailable(DeparturePlanRequest req, ZonedDateTime arrival, int buffer, String reason) {
        return new DeparturePlanResponse(
                req.originName(),
                req.destinationName(),
                arrival.format(TIME_FORMAT_12H),
                arrival.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                arrival.minusMinutes(30 + buffer).format(TIME_FORMAT_12H),
                arrival.minusMinutes(30 + buffer).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                0,
                0.0,
                0,
                0.0,
                buffer,
                "INSUFFICIENT_DATA",
                reason,
                "Unable to calculate precise departure recommendation because live routing data is unavailable.",
                List.of(),
                List.of("Routing provider could not generate a valid route."),
                "INSUFFICIENT_DATA",
                "Weather unavailable",
                0,
                null,
                null,
                Instant.now()
        );
    }
}

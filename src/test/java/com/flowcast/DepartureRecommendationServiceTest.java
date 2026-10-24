package com.flowcast;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flowcast.dto.DeparturePlanRequest;
import com.flowcast.dto.DeparturePlanResponse;
import com.flowcast.dto.IncidentDto;
import com.flowcast.dto.WeatherDto;
import com.flowcast.provider.OpenMeteoClient;
import com.flowcast.provider.TomTomClient;
import com.flowcast.service.DepartureRecommendationService;
import com.flowcast.service.DisruptionAssessmentService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.when;

class DepartureRecommendationServiceTest {

    private TomTomClient tomtom;
    private OpenMeteoClient weather;
    private DisruptionAssessmentService disruptionService;
    private DepartureRecommendationService departureService;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        tomtom = Mockito.mock(TomTomClient.class);
        weather = Mockito.mock(OpenMeteoClient.class);
        disruptionService = Mockito.mock(DisruptionAssessmentService.class);
        departureService = new DepartureRecommendationService(tomtom, weather, disruptionService);
        mapper = new ObjectMapper();
    }

    @Test
    void departureCalculationDeductsTravelDurationAndSafetyBuffer() {
        when(tomtom.configured()).thenReturn(true);

        // Mock 30 min (1800s) travel time, 300s traffic delay, 12km distance
        ObjectNode routeResp = mapper.createObjectNode();
        ArrayNode routes = routeResp.putArray("routes");
        ObjectNode r1 = routes.addObject();
        ObjectNode summary = r1.putObject("summary");
        summary.put("travelTimeInSeconds", 1800);
        summary.put("trafficDelayInSeconds", 300);
        summary.put("lengthInMeters", 12000);

        when(tomtom.route(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(routeResp);
        when(tomtom.incidents(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(List.of());
        when(weather.current()).thenReturn(Optional.of(new WeatherDto(29.0, 70.0, 0.0, 10.0, 1, Instant.now(), "Asia/Kolkata")));

        when(disruptionService.assessRoute(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(new com.flowcast.dto.DisruptionAssessmentDto(
                        "LOWER_EXPOSURE", "HIGH", Instant.now(), 30, 300, 12.0, 30.0,
                        "Clear", List.of(), List.of("Free flow"), List.of(), "Proceed normally"
                ));

        // Target arrival: 09:00 AM, buffer: 15 min
        // Expected departure: 09:00 - 30 min duration - 15 min buffer = 08:15 AM
        DeparturePlanRequest req = new DeparturePlanRequest(
                "Home - Siripuram", 17.7200, 83.3050,
                "GITAM University, Rushikonda", 17.7815, 83.3768,
                "09:00", "2026-10-25", 15
        );

        DeparturePlanResponse plan = departureService.planDeparture(req);

        assertThat(plan).isNotNull();
        assertThat(plan.targetArrivalTime()).isEqualTo("09:00 AM");
        assertThat(plan.recommendedDepartureTime()).isEqualTo("08:15 AM");
        assertThat(plan.travelDurationMinutes()).isEqualTo(30.0);
        assertThat(plan.trafficDelayMinutes()).isEqualTo(5);
        assertThat(plan.safetyBufferMinutes()).isEqualTo(15);
        assertThat(plan.distanceKm()).isEqualTo(12.0);
        assertThat(plan.decisionSummary()).contains("Leave by 08:15 AM");
        assertThat(plan.decisionSummary()).contains("GITAM University");
    }

    @Test
    void unconfiguredProviderReturnsStructuredInsufficientData() {
        when(tomtom.configured()).thenReturn(false);

        DeparturePlanRequest req = new DeparturePlanRequest(
                "NAD Junction", 17.7475, 83.2185,
                "RK Beach", 17.7125, 83.3215,
                "10:00", "2026-10-25", 10
        );

        DeparturePlanResponse plan = departureService.planDeparture(req);

        assertThat(plan).isNotNull();
        assertThat(plan.journeyStatus()).isEqualTo("INSUFFICIENT_DATA");
        assertThat(plan.exposureCategory()).isEqualTo("INSUFFICIENT_DATA");
        assertThat(plan.travelDurationMinutes()).isEqualTo(0.0);
    }
}

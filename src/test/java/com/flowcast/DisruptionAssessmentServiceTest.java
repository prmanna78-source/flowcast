package com.flowcast;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flowcast.dto.DisruptionAssessmentDto;
import com.flowcast.dto.IncidentDto;
import com.flowcast.dto.WeatherDto;
import com.flowcast.service.DisruptionAssessmentService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DisruptionAssessmentServiceTest {
    private DisruptionAssessmentService service;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        service = new DisruptionAssessmentService();
        mapper = new ObjectMapper();
    }

    @Test
    void assessRouteReturnsInsufficientDataWhenSummaryMissing() {
        ObjectNode emptyRoute = mapper.createObjectNode();
        DisruptionAssessmentDto assessment = service.assessRoute(emptyRoute, List.of(), null);

        assertThat(assessment.category()).isEqualTo("INSUFFICIENT_DATA");
        assertThat(assessment.confidence()).isEqualTo("UNCERTAIN");
    }

    @Test
    void assessRouteDetectsHighExposureWhenDelayAndClosuresPresent() {
        ObjectNode route = mapper.createObjectNode();
        ObjectNode summary = route.putObject("summary");
        summary.put("lengthInMeters", 15000.0);
        summary.put("travelTimeInSeconds", 2400); // 40 mins
        summary.put("trafficDelayInSeconds", 1200); // 20 mins delay (>30%)

        var legs = route.putArray("legs");
        var leg = legs.addObject();
        var points = leg.putArray("points");
        points.addObject().put("latitude", 17.6868).put("longitude", 83.2185);
        points.addObject().put("latitude", 17.7268).put("longitude", 83.3085);

        IncidentDto closure = new IncidentDto(
                "INC-1", "roadClosed", "Bridge closed", "Beach Road", "undefined",
                List.of(List.of(83.2185, 17.6868)), 600, null, null, "ACTIVE", "TomTom"
        );

        WeatherDto weather = new WeatherDto(29.0, 85.0, 8.5, 45.0, 63, Instant.now(), "Asia/Kolkata");

        DisruptionAssessmentDto assessment = service.assessRoute(route, List.of(closure), weather);

        assertThat(assessment.category()).isEqualTo("HIGH_EXPOSURE");
        assertThat(assessment.activeIncidentsCount()).isEqualTo(1);
        assertThat(assessment.reasons()).anyMatch(r -> r.contains("closure"));
        assertThat(assessment.reasons()).anyMatch(r -> r.contains("delay"));
    }

    @Test
    void assessRouteReportsLowerExposureWhenFreeFlowAndFavorableWeather() {
        ObjectNode route = mapper.createObjectNode();
        ObjectNode summary = route.putObject("summary");
        summary.put("lengthInMeters", 10000.0);
        summary.put("travelTimeInSeconds", 900); // 15 mins
        summary.put("trafficDelayInSeconds", 0); // No delay

        var legs = route.putArray("legs");
        var leg = legs.addObject();
        var points = leg.putArray("points");
        points.addObject().put("latitude", 17.6868).put("longitude", 83.2185);
        points.addObject().put("latitude", 17.7000).put("longitude", 83.2300);

        WeatherDto weather = new WeatherDto(27.0, 70.0, 0.0, 10.0, 0, Instant.now(), "Asia/Kolkata");

        DisruptionAssessmentDto assessment = service.assessRoute(route, List.of(), weather);

        assertThat(assessment.category()).isEqualTo("LOWER_EXPOSURE");
        assertThat(assessment.confidence()).isEqualTo("HIGH");
        assertThat(assessment.reasons()).anyMatch(r -> r.contains("free-flow"));
    }
}

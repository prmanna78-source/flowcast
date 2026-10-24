package com.flowcast;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowcast.dto.CommunityReportRequest;
import com.flowcast.dto.RouteRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void healthEndpointReturnsLiveStatus() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LIVE"))
                .andExpect(jsonPath("$.data").isNotEmpty());
    }

    @Test
    void mapConfigEndpointReturnsCoordinates() throws Exception {
        mockMvc.perform(get("/api/v1/map/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LIVE"))
                .andExpect(jsonPath("$.data.latitude").value(17.6868))
                .andExpect(jsonPath("$.data.longitude").value(83.2185));
    }

    @Test
    void weatherForecastEndpointReturns200() throws Exception {
        mockMvc.perform(get("/api/v1/weather/forecast"))
                .andExpect(status().isOk());
    }

    @Test
    void forecastReadinessEndpointReportsEvaluationPipeline() throws Exception {
        mockMvc.perform(get("/api/v1/forecasts/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LIVE"))
                .andExpect(jsonPath("$.data.requiredObservationsThreshold").value(1000));
    }

    @Test
    void communityReportsLifecycleEndpoints() throws Exception {
        CommunityReportRequest req = new CommunityReportRequest(
                "Pothole", "Deep road depression near Maddilapalem bus stop.", 17.7315, 83.3185);

        mockMvc.perform(post("/api/v1/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LIVE"))
                .andExpect(jsonPath("$.data.reportType").value("Pothole"));

        mockMvc.perform(get("/api/v1/reports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LIVE"))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void sourcesListReturnsComponentDiagnostics() throws Exception {
        mockMvc.perform(get("/api/v1/sources"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LIVE"))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void invalidBoundingBoxReturnsError() throws Exception {
        mockMvc.perform(get("/api/v1/incidents?west=90&south=10&east=80&north=20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INVALID_REQUEST"));
    }

    @Test
    void calculateDepartureEndpointReturnsValidPlan() throws Exception {
        var req = new com.flowcast.dto.DeparturePlanRequest(
                "Gajuwaka Junction", 17.6868, 83.2185,
                "Siripuram", 17.7200, 83.3050,
                "09:30", "2026-10-25", 15
        );

        mockMvc.perform(post("/api/v1/departures/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LIVE"))
                .andExpect(jsonPath("$.data.targetArrivalTime").value("09:30 AM"))
                .andExpect(jsonPath("$.data.safetyBufferMinutes").value(15));
    }

    @Test
    void savedCommuteEndpointsSupportLifecycle() throws Exception {
        var req = new com.flowcast.dto.CommuteProfileRequest(
                "Work Daily", "NAD Junction", 17.7475, 83.2185,
                "Beach Road", 17.7125, 83.3215,
                "09:00", "MON,TUE,WED,THU,FRI", 15, true
        );

        // 1. Create
        mockMvc.perform(post("/api/v1/commutes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LIVE"))
                .andExpect(jsonPath("$.data.name").value("Work Daily"));

        // 2. List
        mockMvc.perform(get("/api/v1/commutes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LIVE"))
                .andExpect(jsonPath("$.data").isArray());

        // 3. Next upcoming
        mockMvc.perform(get("/api/v1/commutes/next"))
                .andExpect(status().isOk());
    }
}

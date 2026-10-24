package com.flowcast.controller;

import com.flowcast.dto.ApiResponse;
import com.flowcast.dto.CommunityReportDto;
import com.flowcast.dto.CommunityReportRequest;
import com.flowcast.dto.CommuteProfileDto;
import com.flowcast.dto.CommuteProfileRequest;
import com.flowcast.dto.DeparturePlanRequest;
import com.flowcast.dto.DeparturePlanResponse;
import com.flowcast.dto.DisruptionAssessmentDto;
import com.flowcast.dto.ForecastReadinessDto;
import com.flowcast.dto.IncidentDto;
import com.flowcast.dto.RouteRequest;
import com.flowcast.dto.TripFeedbackDto;
import com.flowcast.dto.TripFeedbackRequest;
import com.flowcast.service.CommunityReportService;
import com.flowcast.service.CommuteService;
import com.flowcast.service.DepartureRecommendationService;
import com.flowcast.service.ForecastService;
import com.flowcast.service.ProviderService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import java.time.Instant;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1")
@Validated
public class ApiController {
    private final ProviderService service;
    private final CommunityReportService communityReportService;
    private final ForecastService forecastService;
    private final DepartureRecommendationService departureService;
    private final CommuteService commuteService;

    public ApiController(ProviderService service, CommunityReportService communityReportService,
                         ForecastService forecastService, DepartureRecommendationService departureService,
                         CommuteService commuteService) {
        this.service = service;
        this.communityReportService = communityReportService;
        this.forecastService = forecastService;
        this.departureService = departureService;
        this.commuteService = commuteService;
    }

    @GetMapping("/health")
    public ApiResponse<String> health() {
        return ApiResponse.live("FlowCast", "Application is operational and listening", Instant.now());
    }

    @GetMapping("/map/config")
    public ApiResponse<MapConfig> mapConfig() {
        var p = service.properties();
        return ApiResponse.live("FlowCast configuration",
                new MapConfig(p.getLatitude(), p.getLongitude(), p.getLocationName(),
                        p.getTomtomBrowserKey().isBlank() ? null :
                                "https://api.tomtom.com/maps/orbis/display/raster/tile/{z}/{x}/{y}?apiVersion=2&style=street-dark&tileSize=256&key=" + p.getTomtomBrowserKey(),
                        p.getTomtomBrowserKey().isBlank() ? null :
                                "https://api.tomtom.com/maps/orbis/traffic/flow/raster/tile/{z}/{x}/{y}?apiVersion=2&style=dark&tileSize=256&key=" + p.getTomtomBrowserKey()),
                Instant.now());
    }

    // Weather endpoints
    @GetMapping("/weather/current")
    public ApiResponse<?> weather() {
        return service.weather();
    }

    @GetMapping("/weather/forecast")
    public ApiResponse<?> weatherForecast(
            @RequestParam(required = false) @DecimalMin("-90") @DecimalMax("90") Double latitude,
            @RequestParam(required = false) @DecimalMin("-180") @DecimalMax("180") Double longitude) {
        return service.weatherForecast(latitude, longitude);
    }

    // Traffic & incidents
    @GetMapping({"/traffic/incidents", "/incidents"})
    public ApiResponse<?> incidents(
            @RequestParam(defaultValue = "83.0985") @DecimalMin("-180") double west,
            @RequestParam(defaultValue = "17.5668") @DecimalMin("-90") double south,
            @RequestParam(defaultValue = "83.3385") @DecimalMax("180") double east,
            @RequestParam(defaultValue = "17.8068") @DecimalMax("90") double north) {
        if (west >= east || south >= north) {
            return ApiResponse.unavailable("INVALID_REQUEST", "FlowCast", "The bounding box is invalid.");
        }
        return service.incidents(west, south, east, north);
    }

    @GetMapping("/incidents/{id}")
    public ApiResponse<IncidentDto> incidentById(@PathVariable String id) {
        return service.incidentById(id);
    }

    @GetMapping({"/traffic/segments", "/traffic/flow"})
    public ApiResponse<?> segment(
            @RequestParam(defaultValue = "17.6868") @DecimalMin("-90") @DecimalMax("90") double latitude,
            @RequestParam(defaultValue = "83.2185") @DecimalMin("-180") @DecimalMax("180") double longitude) {
        return service.segment(latitude, longitude);
    }

    // Routing & assessment
    @PostMapping("/routes/alternatives")
    public ApiResponse<?> routes(@Valid @RequestBody RouteRequest request) {
        var origin = request.origin();
        var destination = request.destination();
        return service.route(origin.latitude(), origin.longitude(), destination.latitude(), destination.longitude());
    }

    @PostMapping("/routes/assessment")
    public ApiResponse<DisruptionAssessmentDto> routeAssessment(@Valid @RequestBody RouteRequest request) {
        var origin = request.origin();
        var destination = request.destination();
        return service.assessRoute(origin.latitude(), origin.longitude(), destination.latitude(), destination.longitude());
    }

    // Places & Search
    @GetMapping({"/locations/search", "/places/search"})
    public ApiResponse<?> searchPlaces(
            @RequestParam String query,
            @RequestParam(required = false) @DecimalMin("-90") @DecimalMax("90") Double latitude,
            @RequestParam(required = false) @DecimalMin("-180") @DecimalMax("180") Double longitude) {
        if (query.isBlank() || query.length() > 120) {
            return ApiResponse.unavailable("INVALID_REQUEST", "FlowCast", "Search text must contain between 1 and 120 characters.");
        }
        if ((latitude == null) != (longitude == null)) {
            return ApiResponse.unavailable("INVALID_REQUEST", "FlowCast", "Latitude and longitude must be supplied together.");
        }
        return service.search(query, latitude, longitude);
    }

    // Forecasting endpoints
    @GetMapping({"/forecasts/readiness", "/predictions/status"})
    public ApiResponse<ForecastReadinessDto> forecastReadiness() {
        return ApiResponse.live("FlowCast Forecasting Pipeline", forecastService.getReadiness(), Instant.now());
    }

    @GetMapping("/forecasts/traffic")
    public ApiResponse<?> trafficForecast(
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            @RequestParam(defaultValue = "30") Integer horizonMinutes) {
        return forecastService.getTrafficForecast(latitude, longitude, horizonMinutes);
    }

    // Community Hazard Reports
    @PostMapping("/reports")
    public ApiResponse<CommunityReportDto> createReport(
            @Valid @RequestBody CommunityReportRequest request,
            HttpServletRequest httpServletRequest) {
        try {
            CommunityReportDto report = communityReportService.submitReport(request, httpServletRequest.getRemoteAddr());
            return ApiResponse.live("Community Road Hazard Report (Guest Submitted)", report, Instant.now());
        } catch (IllegalArgumentException ex) {
            return ApiResponse.unavailable("RATE_LIMITED", "Community Reports", ex.getMessage());
        }
    }

    @GetMapping("/reports")
    public ApiResponse<List<CommunityReportDto>> listReports() {
        return ApiResponse.live("Community Road Hazard Reports", communityReportService.listReports(), Instant.now());
    }

    @GetMapping("/reports/{id}")
    public ApiResponse<CommunityReportDto> getReport(@PathVariable Long id) {
        return communityReportService.getReport(id)
                .map(r -> ApiResponse.live("Community Road Hazard Report", r, Instant.now()))
                .orElseGet(() -> ApiResponse.unavailable("NOT_FOUND", "Community Reports", "Report not found."));
    }

    @DeleteMapping("/reports/{id}")
    public ApiResponse<String> deleteReport(@PathVariable Long id) {
        boolean removed = communityReportService.deleteReport(id);
        return removed ? ApiResponse.live("Community Reports", "Report removed.", Instant.now())
                : ApiResponse.unavailable("NOT_FOUND", "Community Reports", "Report not found.");
    }

    @DeleteMapping("/reports")
    public ApiResponse<String> clearReports() {
        communityReportService.clearAll();
        return ApiResponse.live("Community Reports", "All community reports cleared.", Instant.now());
    }

    // =========================================================================
    // FLOWCAST 2.0: Personal Commute Assistant & Departure-Time Engine
    // =========================================================================

    @PostMapping("/departures/calculate")
    public ApiResponse<DeparturePlanResponse> calculateDeparture(@Valid @RequestBody DeparturePlanRequest request) {
        DeparturePlanResponse plan = departureService.planDeparture(request);
        return ApiResponse.live("FlowCast Commute Decision Engine", plan, Instant.now());
    }

    @PostMapping("/commutes")
    public ApiResponse<CommuteProfileDto> saveCommute(@Valid @RequestBody CommuteProfileRequest request) {
        CommuteProfileDto profile = commuteService.saveCommute(request);
        return ApiResponse.live("FlowCast Saved Commute", profile, Instant.now());
    }

    @GetMapping("/commutes")
    public ApiResponse<List<CommuteProfileDto>> listCommutes() {
        return ApiResponse.live("FlowCast Saved Commutes", commuteService.listCommutes(), Instant.now());
    }

    @GetMapping("/commutes/next")
    public ApiResponse<CommuteProfileDto> nextCommute() {
        return commuteService.getNextUpcomingCommute()
                .map(c -> ApiResponse.live("Next Scheduled Commute", c, Instant.now()))
                .orElseGet(() -> ApiResponse.unavailable("NONE_SCHEDULED", "Commute Assistant", "No upcoming commutes scheduled for today."));
    }

    @GetMapping("/commutes/{id}")
    public ApiResponse<CommuteProfileDto> getCommute(@PathVariable Long id) {
        return commuteService.getCommute(id)
                .map(c -> ApiResponse.live("Saved Commute", c, Instant.now()))
                .orElseGet(() -> ApiResponse.unavailable("NOT_FOUND", "Saved Commute", "Commute profile not found."));
    }

    @PutMapping("/commutes/{id}")
    public ApiResponse<CommuteProfileDto> updateCommute(@PathVariable Long id, @Valid @RequestBody CommuteProfileRequest request) {
        return commuteService.updateCommute(id, request)
                .map(c -> ApiResponse.live("Updated Commute", c, Instant.now()))
                .orElseGet(() -> ApiResponse.unavailable("NOT_FOUND", "Saved Commute", "Commute profile not found."));
    }

    @DeleteMapping("/commutes/{id}")
    public ApiResponse<String> deleteCommute(@PathVariable Long id) {
        boolean removed = commuteService.deleteCommute(id);
        return removed ? ApiResponse.live("Saved Commute", "Commute removed.", Instant.now())
                : ApiResponse.unavailable("NOT_FOUND", "Saved Commute", "Commute profile not found.");
    }

    @PostMapping("/commutes/{id}/toggle")
    public ApiResponse<CommuteProfileDto> toggleCommute(@PathVariable Long id) {
        return commuteService.toggleCommute(id)
                .map(c -> ApiResponse.live("Toggled Commute", c, Instant.now()))
                .orElseGet(() -> ApiResponse.unavailable("NOT_FOUND", "Saved Commute", "Commute profile not found."));
    }

    @PostMapping("/commutes/{id}/recalculate")
    public ApiResponse<DeparturePlanResponse> recalculateCommute(@PathVariable Long id) {
        return commuteService.recalculateCommute(id)
                .map(plan -> ApiResponse.live("Recalculated Commute Departure", plan, Instant.now()))
                .orElseGet(() -> ApiResponse.unavailable("NOT_FOUND", "Saved Commute", "Commute profile not found."));
    }

    @PostMapping("/commutes/feedback")
    public ApiResponse<TripFeedbackDto> recordFeedback(@RequestBody TripFeedbackRequest request) {
        TripFeedbackDto fb = commuteService.recordFeedback(request);
        return ApiResponse.live("Trip Feedback Recorded", fb, Instant.now());
    }

    @GetMapping("/commutes/feedback")
    public ApiResponse<List<TripFeedbackDto>> listFeedback() {
        return ApiResponse.live("Trip Feedback History", commuteService.listFeedback(), Instant.now());
    }

    @GetMapping(value = "/stream/status", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        SseEmitter emitter = new SseEmitter(5000L);
        try {
            emitter.send(SseEmitter.event().name("status").data("Provider status is available through /api/v1/sources."));
            emitter.complete();
        } catch (Exception ex) {
            emitter.completeWithError(ex);
        }
        return emitter;
    }

    public record MapConfig(double latitude, double longitude, String locationName,
                            String baseTileTemplate, String trafficTileTemplate) {}
}

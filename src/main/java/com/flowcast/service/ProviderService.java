package com.flowcast.service;

import com.flowcast.config.FlowCastProperties;
import com.flowcast.dto.ApiResponse;
import com.flowcast.dto.IncidentDto;
import com.flowcast.dto.SegmentDto;
import com.flowcast.dto.WeatherDto;
import com.flowcast.provider.OpenMeteoClient;
import com.flowcast.provider.TomTomClient;
import java.time.Instant;
import java.util.List;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.HttpStatusCodeException;

@Service
public class ProviderService {
    private final OpenMeteoClient weather;
    private final TomTomClient tomtom;
    private final FlowCastProperties properties;
    private final DisruptionAssessmentService disruptionService;

    public ProviderService(OpenMeteoClient weather, TomTomClient tomtom,
                           FlowCastProperties properties, DisruptionAssessmentService disruptionService) {
        this.weather = weather;
        this.tomtom = tomtom;
        this.properties = properties;
        this.disruptionService = disruptionService;
    }

    public ApiResponse<WeatherDto> weather() {
        try {
            return weather.current().map(value -> ApiResponse.live("Open-Meteo Forecast API", value, Instant.now()))
                    .orElseGet(() -> ApiResponse.unavailable("UNAVAILABLE", "Open-Meteo Forecast API",
                            "The weather response did not contain current conditions."));
        } catch (RestClientException ex) {
            return ApiResponse.unavailable("UNAVAILABLE", "Open-Meteo Forecast API", "The weather provider could not be reached.");
        }
    }

    public ApiResponse<com.flowcast.dto.WeatherForecastDto> weatherForecast(Double lat, Double lon) {
        try {
            return weather.forecast(lat, lon).map(value -> ApiResponse.live("Open-Meteo 24h Forecast API", value, Instant.now()))
                    .orElseGet(() -> ApiResponse.unavailable("UNAVAILABLE", "Open-Meteo Forecast API",
                            "The forecast response could not be parsed."));
        } catch (RestClientException ex) {
            return ApiResponse.unavailable("UNAVAILABLE", "Open-Meteo Forecast API", "The weather provider could not be reached.");
        }
    }

    public ApiResponse<List<IncidentDto>> incidents(double west, double south, double east, double north) {
        if (!tomtom.configured()) return ApiResponse.unavailable("CONFIG_MISSING", "TomTom", "Configure FLOWCAST_TOMTOM_SERVER_KEY.");
        try {
            return ApiResponse.live("TomTom Orbis Traffic Incidents", tomtom.incidents(west, south, east, north), Instant.now());
        } catch (HttpStatusCodeException ex) {
            return ApiResponse.unavailable(providerStatus(ex), "TomTom Orbis Traffic Incidents", providerReason(ex));
        } catch (RestClientException ex) {
            return ApiResponse.unavailable("UNAVAILABLE", "TomTom Orbis Traffic Incidents", "The incident provider could not be reached.");
        }
    }

    public ApiResponse<SegmentDto> segment(double latitude, double longitude) {
        if (!tomtom.configured()) return ApiResponse.unavailable("CONFIG_MISSING", "TomTom", "Configure FLOWCAST_TOMTOM_SERVER_KEY.");
        try {
            SegmentDto value = tomtom.segment(latitude, longitude);
            if (value.currentSpeedKmh() == null) {
                return ApiResponse.unavailable("NO_COVERAGE", "TomTom Traffic Flow Segment Data",
                        "The provider returned no supported segment for this point.");
            }
            return ApiResponse.live("TomTom Traffic Flow Segment Data", value, Instant.now());
        } catch (HttpStatusCodeException ex) {
            return ApiResponse.unavailable(providerStatus(ex), "TomTom Traffic Flow Segment Data", providerReason(ex));
        } catch (RestClientException ex) {
            return ApiResponse.unavailable("UNAVAILABLE", "TomTom Traffic Flow Segment Data", "The traffic provider could not be reached.");
        }
    }

    public FlowCastProperties properties() { return properties; }

    public ApiResponse<JsonNode> route(double originLatitude, double originLongitude,
                                       double destinationLatitude, double destinationLongitude) {
        if (!tomtom.configured()) return ApiResponse.unavailable("CONFIG_MISSING", "TomTom Routing", "Configure FLOWCAST_TOMTOM_SERVER_KEY.");
        try {
            JsonNode data = tomtom.route(originLatitude, originLongitude, destinationLatitude, destinationLongitude);
            return data == null ? ApiResponse.unavailable("INVALID_RESPONSE", "TomTom Routing", "The provider returned no route response.")
                    : ApiResponse.live("TomTom Orbis Routing", data, Instant.now());
        } catch (HttpStatusCodeException ex) {
            return ApiResponse.unavailable(providerStatus(ex), "TomTom Orbis Routing", providerReason(ex));
        } catch (RestClientException ex) {
            return ApiResponse.unavailable("UNAVAILABLE", "TomTom Orbis Routing", "The routing provider could not be reached.");
        }
    }

    public ApiResponse<JsonNode> search(String query, Double latitude, Double longitude) {
        if (!tomtom.configured()) return ApiResponse.unavailable("CONFIG_MISSING", "TomTom Search", "Configure FLOWCAST_TOMTOM_SERVER_KEY.");
        try {
            JsonNode data = tomtom.search(query, latitude, longitude);
            return data == null ? ApiResponse.unavailable("INVALID_RESPONSE", "TomTom Search", "The provider returned no search response.")
                    : ApiResponse.live("TomTom Search", data, Instant.now());
        } catch (HttpStatusCodeException ex) {
            return ApiResponse.unavailable(providerStatus(ex), "TomTom Search", providerReason(ex));
        } catch (RestClientException ex) {
            return ApiResponse.unavailable("UNAVAILABLE", "TomTom Search", "The search provider could not be reached.");
        }
    }

    public ApiResponse<com.flowcast.dto.DisruptionAssessmentDto> assessRoute(
            double originLat, double originLon, double destLat, double destLon) {
        if (!tomtom.configured()) {
            return ApiResponse.unavailable("CONFIG_MISSING", "FlowCast Disruption Engine", "Configure FLOWCAST_TOMTOM_SERVER_KEY.");
        }

        try {
            JsonNode routeData = tomtom.route(originLat, originLon, destLat, destLon);
            if (routeData == null || !routeData.has("routes") || routeData.path("routes").isEmpty()) {
                return ApiResponse.unavailable("ROUTE_NOT_FOUND", "TomTom Routing", "Provider found no routes for these coordinates.");
            }

            JsonNode firstRoute = routeData.path("routes").get(0);

            // Bounding box for incidents covering the route
            double minLon = Math.min(originLon, destLon) - 0.05;
            double maxLon = Math.max(originLon, destLon) + 0.05;
            double minLat = Math.min(originLat, destLat) - 0.05;
            double maxLat = Math.max(originLat, destLat) + 0.05;

            List<IncidentDto> incs = List.of();
            try {
                incs = tomtom.incidents(minLon, minLat, maxLon, maxLat);
            } catch (Exception ignored) {}

            WeatherDto currWeather = weather.current().orElse(null);

            com.flowcast.dto.DisruptionAssessmentDto assessment =
                    disruptionService.assessRoute(firstRoute, incs, currWeather);

            return ApiResponse.live("FlowCast Weather-Aware Disruption Engine", assessment, Instant.now());
        } catch (HttpStatusCodeException ex) {
            return ApiResponse.unavailable(providerStatus(ex), "TomTom Orbis Routing", providerReason(ex));
        } catch (RestClientException ex) {
            return ApiResponse.unavailable("UNAVAILABLE", "FlowCast Disruption Engine", "Providers could not be reached.");
        }
    }

    public ApiResponse<IncidentDto> incidentById(String id) {
        if (!tomtom.configured()) return ApiResponse.unavailable("CONFIG_MISSING", "TomTom", "Configure FLOWCAST_TOMTOM_SERVER_KEY.");
        try {
            double delta = 0.20;
            List<IncidentDto> list = tomtom.incidents(
                    properties.getLongitude() - delta, properties.getLatitude() - delta,
                    properties.getLongitude() + delta, properties.getLatitude() + delta);
            for (IncidentDto inc : list) {
                if (id.equalsIgnoreCase(inc.id())) {
                    return ApiResponse.live("TomTom Orbis Incident Detail", inc, Instant.now());
                }
            }
            return ApiResponse.unavailable("NOT_FOUND", "TomTom Orbis Traffic", "Incident with ID " + id + " not found or expired.");
        } catch (HttpStatusCodeException ex) {
            return ApiResponse.unavailable(providerStatus(ex), "TomTom Orbis Traffic", providerReason(ex));
        } catch (RestClientException ex) {
            return ApiResponse.unavailable("UNAVAILABLE", "TomTom Orbis Traffic", "The incident provider could not be reached.");
        }
    }

    private static String providerStatus(HttpStatusCodeException ex) {
        return ex.getStatusCode().value() == 429 ? "RATE_LIMITED"
                : (ex.getStatusCode().value() == 401 || ex.getStatusCode().value() == 403 ? "CONFIG_MISSING" : "UNAVAILABLE");
    }

    private static String providerReason(HttpStatusCodeException ex) {
        return switch (ex.getStatusCode().value()) {
            case 401, 403 -> "TomTom rejected the configured API key or its product permissions.";
            case 429 -> "TomTom rate-limited this request.";
            default -> "TomTom returned HTTP " + ex.getStatusCode().value() + ".";
        };
    }
}

package com.flowcast.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowcast.dto.DisruptionAssessmentDto;
import com.flowcast.dto.IncidentDto;
import com.flowcast.dto.WeatherDto;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class DisruptionAssessmentService {

    public DisruptionAssessmentDto assessRoute(JsonNode routeNode, List<IncidentDto> incidents, WeatherDto weather) {
        if (routeNode == null || !routeNode.has("summary")) {
            return new DisruptionAssessmentDto(
                    "INSUFFICIENT_DATA",
                    "UNCERTAIN",
                    Instant.now(),
                    0,
                    0,
                    0.0,
                    0.0,
                    weather != null ? describeWeather(weather) : "Weather data unavailable",
                    List.of(),
                    List.of("Route summary from provider is missing or incomplete."),
                    List.of("Provider routing summary unavailable"),
                    "Select valid origin and destination points to calculate a route."
            );
        }

        JsonNode summary = routeNode.path("summary");
        double lengthMeters = summary.path("lengthInMeters").asDouble(0.0);
        int travelTimeSeconds = summary.path("travelTimeInSeconds").asInt(0);
        int trafficDelaySeconds = summary.path("trafficDelayInSeconds").asInt(0);
        double distanceKm = lengthMeters / 1000.0;
        double travelDurationMinutes = travelTimeSeconds / 60.0;

        List<List<Double>> routeCoordinates = extractRouteCoordinates(routeNode);

        List<IncidentDto> relevantIncidents = new ArrayList<>();
        if (incidents != null && !incidents.isEmpty()) {
            for (IncidentDto inc : incidents) {
                if (isIncidentNearRoute(inc, routeCoordinates, 0.015)) { // ~1.5 km corridor
                    relevantIncidents.add(inc);
                }
            }
        }

        List<String> reasons = new ArrayList<>();
        List<String> limitations = new ArrayList<>();

        int score = 0; // 0 to 10 scale for exposure categorization

        // Traffic delay evaluation
        if (trafficDelaySeconds > 0 && travelTimeSeconds > 0) {
            double delayRatio = (double) trafficDelaySeconds / travelTimeSeconds;
            int delayMinutes = Math.round(trafficDelaySeconds / 60.0f);
            if (delayRatio > 0.35 || delayMinutes > 20) {
                score += 4;
                reasons.add("Substantial traffic delay detected: " + delayMinutes + " min delay on a "
                        + Math.round(travelDurationMinutes) + " min journey (" + Math.round(delayRatio * 100) + "% of travel time).");
            } else if (delayRatio > 0.15 || delayMinutes > 8) {
                score += 2;
                reasons.add("Moderate traffic delay reported: " + delayMinutes + " min added to baseline journey time.");
            } else {
                score += 1;
                reasons.add("Minor traffic delay: " + delayMinutes + " min above baseline.");
            }
        } else {
            reasons.add("Traffic flow along the calculated corridor is currently running close to free-flow conditions.");
        }

        // Active incidents evaluation
        if (!relevantIncidents.isEmpty()) {
            int roadClosures = 0;
            int heavyJams = 0;
            for (IncidentDto inc : relevantIncidents) {
                String cat = inc.category() != null ? inc.category().toLowerCase() : "";
                String desc = inc.description() != null ? inc.description().toLowerCase() : "";
                String sev = inc.severity() != null ? inc.severity().toLowerCase() : "";

                if (cat.contains("closed") || desc.contains("closed")) {
                    roadClosures++;
                } else if (sev.contains("major") || cat.contains("jam") || desc.contains("stationary")) {
                    heavyJams++;
                }
            }

            if (roadClosures > 0) {
                score += 5;
                reasons.add(roadClosures + " road closure(s) confirmed on or immediately adjoining this route corridor.");
            }
            if (heavyJams > 0) {
                score += 3;
                reasons.add(heavyJams + " major traffic bottleneck/congestion report(s) along this route.");
            }
            if (roadClosures == 0 && heavyJams == 0) {
                score += 1;
                reasons.add(relevantIncidents.size() + " advisory traffic incident(s) reported in the vicinity of this corridor.");
            }
        } else {
            reasons.add("No active provider traffic incidents or road closures intersect this route corridor.");
            limitations.add("Incident feeds reflect provider coverage and reported events; unmonitored roads may have unreported slowdowns.");
        }

        // Weather assessment
        String weatherSummary = "Weather data unavailable";
        if (weather != null) {
            weatherSummary = describeWeather(weather);
            if (weather.precipitationMillimetres() > 5.0) {
                score += 3;
                reasons.add("High precipitation observed (" + String.format("%.1f", weather.precipitationMillimetres())
                        + " mm): expect wet roads, reduced braking distance, and surface water accumulation.");
            } else if (weather.precipitationMillimetres() > 0.5) {
                score += 1;
                reasons.add("Light to moderate rainfall observed (" + String.format("%.1f", weather.precipitationMillimetres())
                        + " mm): roads may be slick.");
            } else {
                reasons.add("Dry road surface conditions: Open-Meteo reports 0.0 mm precipitation.");
            }

            if (weather.windSpeedKmh() > 40.0) {
                score += 2;
                reasons.add("Strong winds (" + String.format("%.1f", weather.windSpeedKmh())
                        + " km/h): watch for coastal crosswinds and flying debris on exposed overpasses.");
            }
        } else {
            limitations.add("Current weather observation could not be retrieved from Open-Meteo; weather risk factor is unassessed.");
        }

        // Category determination
        String category;
        if (score >= 6) {
            category = "HIGH_EXPOSURE";
        } else if (score >= 4) {
            category = "ELEVATED_EXPOSURE";
        } else if (score >= 2) {
            category = "MODERATE_EXPOSURE";
        } else {
            category = "LOWER_EXPOSURE";
        }

        // Confidence determination
        String confidence = (weather != null && routeCoordinates.size() >= 2) ? "HIGH" : "MEDIUM";

        // Actionable recommendation
        String recommendation;
        if (category.equals("HIGH_EXPOSURE")) {
            recommendation = "Consider reviewing alternative routes or postponing departure until major bottlenecks or closures ease.";
        } else if (category.equals("ELEVATED_EXPOSURE")) {
            recommendation = "Expect noticeable delays along this corridor. Allow 15-20 minutes extra travel time.";
        } else if (category.equals("MODERATE_EXPOSURE")) {
            recommendation = "Route is generally traversable with localized slow traffic. Proceed with normal caution.";
        } else {
            recommendation = "Favorable conditions observed along this route with minimal reported impediments.";
        }

        return new DisruptionAssessmentDto(
                category,
                confidence,
                Instant.now(),
                relevantIncidents.size(),
                trafficDelaySeconds,
                travelDurationMinutes,
                distanceKm,
                weatherSummary,
                relevantIncidents,
                reasons,
                limitations,
                recommendation
        );
    }

    private String describeWeather(WeatherDto w) {
        return String.format("%.1f °C, %.0f%% humidity, %.1f km/h wind, %.1f mm rain",
                w.temperatureCelsius(), w.relativeHumidityPercent(), w.windSpeedKmh(), w.precipitationMillimetres());
    }

    private List<List<Double>> extractRouteCoordinates(JsonNode routeNode) {
        List<List<Double>> coords = new ArrayList<>();
        JsonNode legs = routeNode.path("legs");
        if (legs.isArray()) {
            for (JsonNode leg : legs) {
                JsonNode points = leg.path("points");
                if (points.isArray()) {
                    for (JsonNode pt : points) {
                        double lat = pt.path("latitude").asDouble();
                        double lon = pt.path("longitude").asDouble();
                        coords.add(List.of(lon, lat));
                    }
                }
            }
        }
        return coords;
    }

    private boolean isIncidentNearRoute(IncidentDto inc, List<List<Double>> routeCoords, double thresholdDegrees) {
        if (inc.geometry() == null || inc.geometry().isEmpty() || routeCoords.isEmpty()) return false;
        double threshSq = thresholdDegrees * thresholdDegrees;

        // Sample route points to avoid O(N*M) explosion on long routes
        int step = Math.max(1, routeCoords.size() / 50);
        for (int i = 0; i < routeCoords.size(); i += step) {
            List<Double> rPt = routeCoords.get(i);
            double rLon = rPt.get(0);
            double rLat = rPt.get(1);

            for (List<Double> iPt : inc.geometry()) {
                if (iPt.size() < 2) continue;
                double dLon = iPt.get(0) - rLon;
                double dLat = iPt.get(1) - rLat;
                if ((dLon * dLon + dLat * dLat) <= threshSq) {
                    return true;
                }
            }
        }
        return false;
    }
}

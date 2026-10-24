package com.flowcast.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowcast.config.FlowCastProperties;
import com.flowcast.dto.IncidentDto;
import com.flowcast.dto.SegmentDto;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

@Component
public class TomTomClient {
    private final RestClient client;
    private final FlowCastProperties properties;

    public TomTomClient(RestClient client, FlowCastProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    public boolean configured() { return !properties.getTomtomServerKey().isBlank(); }

    public List<IncidentDto> incidents(double west, double south, double east, double north) {
        JsonNode root = client.get().uri(uri -> uri.scheme("https").host("api.tomtom.com")
                .path("/maps/orbis/traffic/incidents/details")
                .queryParam("apiVersion", "2").queryParam("bbox", "%s,%s,%s,%s".formatted(west, south, east, north))
                .queryParam("timeValidity", "present").queryParam("key", properties.getTomtomServerKey()).build())
                .header("Accept", "application/json")
                .header("Attributes", "incidents")
                .retrieve().body(JsonNode.class);
        List<IncidentDto> result = new ArrayList<>();
        if (root == null) return result;
        for (JsonNode feature : root.path("incidents")) {
            var props = feature.path("properties");
            List<List<Double>> geometry = new ArrayList<>();
            for (JsonNode position : feature.path("geometry").path("coordinates")) {
                if (position.isArray() && position.size() >= 2) geometry.add(List.of(position.get(0).asDouble(), position.get(1).asDouble()));
            }
            String category = props.hasNonNull("iconCategory") ? props.get("iconCategory").asText() : props.path("category").asText(null);
            String description = (props.path("events").isArray() && !props.path("events").isEmpty())
                    ? props.path("events").get(0).path("description").asText(null)
                    : props.path("description").asText(null);
            String roadName = props.hasNonNull("from")
                    ? props.get("from").asText() + (props.hasNonNull("to") && !props.get("to").asText().isBlank() ? " → " + props.get("to").asText() : "")
                    : props.path("roadName").asText(null);
            String severity = props.hasNonNull("magnitudeOfDelay") ? props.get("magnitudeOfDelay").asText() : props.path("severity").asText(null);
            Integer delay = props.hasNonNull("delayInSeconds") ? props.get("delayInSeconds").asInt() : null;
            String startTime = props.path("startTime").asText(null);
            String endTime = props.path("endTime").asText(null);
            result.add(new IncidentDto(props.path("id").asText(null), category, description, roadName, severity, geometry, delay, startTime, endTime, "ACTIVE", "TomTom Orbis"));
        }
        return result;
    }

    public SegmentDto segment(double latitude, double longitude) {
        JsonNode root = client.get().uri(uri -> uri.scheme("https").host("api.tomtom.com")
                .path("/traffic/services/4/flowSegmentData/absolute/10/json")
                .queryParam("point", "%s,%s".formatted(latitude, longitude))
                .queryParam("key", properties.getTomtomServerKey()).build()).retrieve().body(JsonNode.class);
        JsonNode flow = root == null ? null : root.path("flowSegmentData");
        if (flow == null || flow.isMissingNode()) return new SegmentDto(null, latitude, longitude, null, null, null, null, null);
        return new SegmentDto(flow.path("frc").asText(null), latitude, longitude,
                number(flow, "currentSpeed"), number(flow, "freeFlow"), integer(flow, "currentTravelTime"),
                integer(flow, "freeFlowTravelTime"), integer(flow, "confidence"));
    }

    public JsonNode route(double originLatitude, double originLongitude, double destinationLatitude, double destinationLongitude) {
        String locations = "%s,%s:%s,%s".formatted(originLatitude, originLongitude, destinationLatitude, destinationLongitude);
        return client.get().uri(uri -> uri.scheme("https").host("api.tomtom.com")
                .path("/routing/1/calculateRoute/" + locations + "/json")
                .queryParam("key", properties.getTomtomServerKey())
                .queryParam("traffic", "true")
                .build())
                .header("Accept", "application/json")
                .retrieve().body(JsonNode.class);
    }

    public JsonNode search(String query, Double latitude, Double longitude) {
        String searchKey = properties.getTomtomSearchKey().isBlank()
                ? properties.getTomtomServerKey() : properties.getTomtomSearchKey();
        return client.get().uri(uri -> {
                    var builder = uri.scheme("https").host("api.tomtom.com")
                            .pathSegment("search", "2", "search", query + ".json")
                            .queryParam("limit", 5).queryParam("countrySet", "IN")
                            .queryParam("typeahead", true).queryParam("key", searchKey);
                    if (latitude != null && longitude != null) {
                        builder.queryParam("lat", latitude).queryParam("lon", longitude).queryParam("radius", 15000);
                    }
                    return builder.build();
                })
                .retrieve().body(JsonNode.class);
    }

    private static Double number(JsonNode node, String field) { return node.has(field) ? node.get(field).asDouble() : null; }
    private static Integer integer(JsonNode node, String field) { return node.has(field) ? node.get(field).asInt() : null; }
}

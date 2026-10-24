package com.flowcast.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowcast.config.FlowCastProperties;
import com.flowcast.dto.WeatherDto;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class OpenMeteoClient {
    private final RestClient client;
    private final FlowCastProperties properties;

    public OpenMeteoClient(RestClient client, FlowCastProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    public Optional<WeatherDto> current() {
        if (!properties.isOpenMeteoEnabled()) return Optional.empty();
        JsonNode body = client.get().uri(uri -> uri
                .scheme("https").host("api.open-meteo.com").path("/v1/forecast")
                .queryParam("latitude", properties.getLatitude())
                .queryParam("longitude", properties.getLongitude())
                .queryParam("current", "temperature_2m,relative_humidity_2m,precipitation,wind_speed_10m,weather_code")
                .queryParam("timezone", "Asia/Kolkata").build())
                .retrieve().body(JsonNode.class);
        if (body == null || !body.has("current")) return Optional.empty();
        JsonNode current = body.get("current");
        return Optional.of(new WeatherDto(
                current.path("temperature_2m").asDouble(),
                current.path("relative_humidity_2m").asDouble(),
                current.path("precipitation").asDouble(),
                current.path("wind_speed_10m").asDouble(),
                current.path("weather_code").asInt(),
                Instant.now(), body.path("timezone").asText("Asia/Kolkata")));
    }

    public Optional<com.flowcast.dto.WeatherForecastDto> forecast(Double lat, Double lon) {
        if (!properties.isOpenMeteoEnabled()) return Optional.empty();
        double latitude = (lat != null && lat >= -90 && lat <= 90) ? lat : properties.getLatitude();
        double longitude = (lon != null && lon >= -180 && lon <= 180) ? lon : properties.getLongitude();
        JsonNode body = client.get().uri(uri -> uri
                .scheme("https").host("api.open-meteo.com").path("/v1/forecast")
                .queryParam("latitude", latitude)
                .queryParam("longitude", longitude)
                .queryParam("hourly", "temperature_2m,precipitation_probability,precipitation,wind_speed_10m,weather_code")
                .queryParam("forecast_hours", 24)
                .queryParam("timezone", "Asia/Kolkata").build())
                .retrieve().body(JsonNode.class);
        if (body == null || !body.has("hourly")) return Optional.empty();
        JsonNode hourly = body.get("hourly");
        JsonNode times = hourly.path("time");
        JsonNode temps = hourly.path("temperature_2m");
        JsonNode rainProbs = hourly.path("precipitation_probability");
        JsonNode precip = hourly.path("precipitation");
        JsonNode winds = hourly.path("wind_speed_10m");
        JsonNode codes = hourly.path("weather_code");

        java.util.List<com.flowcast.dto.WeatherForecastDto.HourlyForecastItem> items = new java.util.ArrayList<>();
        for (int i = 0; i < times.size(); i++) {
            items.add(new com.flowcast.dto.WeatherForecastDto.HourlyForecastItem(
                    times.get(i).asText(),
                    temps.path(i).asDouble(0.0),
                    rainProbs.path(i).asInt(0),
                    precip.path(i).asDouble(0.0),
                    winds.path(i).asDouble(0.0),
                    codes.path(i).asInt(0)
            ));
        }
        return Optional.of(new com.flowcast.dto.WeatherForecastDto(
                latitude, longitude, body.path("timezone").asText("Asia/Kolkata"), Instant.now(), items));
    }
}

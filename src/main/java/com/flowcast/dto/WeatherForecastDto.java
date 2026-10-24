package com.flowcast.dto;

import java.time.Instant;
import java.util.List;

public record WeatherForecastDto(
        double latitude,
        double longitude,
        String timezone,
        Instant retrievedAt,
        List<HourlyForecastItem> hourly
) {
    public record HourlyForecastItem(
            String time,
            double temperatureCelsius,
            int precipitationProbabilityPercent,
            double precipitationMm,
            double windSpeedKmh,
            int weatherCode
    ) {}
}

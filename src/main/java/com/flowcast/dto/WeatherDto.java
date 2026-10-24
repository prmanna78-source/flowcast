package com.flowcast.dto;

import java.time.Instant;

public record WeatherDto(double temperatureCelsius, double relativeHumidityPercent,
                         double precipitationMillimetres, double windSpeedKmh,
                         int weatherCode, Instant validAt, String timezone) {}

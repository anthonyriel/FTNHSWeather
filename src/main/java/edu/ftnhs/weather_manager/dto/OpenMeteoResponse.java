package edu.ftnhs.weather_manager.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenMeteoResponse(
    @JsonProperty("current") CurrentData current,
    @JsonProperty("hourly") HourlyData hourly
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CurrentData(
        @JsonProperty("precipitation") double precipitation,
        @JsonProperty("rain") double rain,
        @JsonProperty("temperature_2m") double temperature2m,
        @JsonProperty("wind_speed_10m") double windSpeed10m
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record HourlyData(
        @JsonProperty("time") List<String> time,
        @JsonProperty("temperature_2m") List<Double> temperature2m,
        @JsonProperty("precipitation") List<Double> precipitation,
        @JsonProperty("wind_speed_10m") List<Double> windSpeed10m
    ) {}
}
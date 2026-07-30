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
        @JsonProperty("precipitation") Double precipitation,
        @JsonProperty("rain") Double rain,
        @JsonProperty("temperature_2m") Double temperature2m,
        @JsonProperty("wind_speed_10m") Double windSpeed10m,
        @JsonProperty("relative_humidity_2m") Double relativeHumidity2m,
        @JsonProperty("cloud_cover") Integer cloudCover,
        @JsonProperty("pressure_msl") Double pressureMsl,
        @JsonProperty("visibility") Double visibility,
        @JsonProperty("uv_index") Double uvIndex,
        @JsonProperty("precipitation_probability") Integer precipitationProbability,
        @JsonProperty("weather_code") Integer weatherCode
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record HourlyData(
        @JsonProperty("time") List<String> time,
        @JsonProperty("temperature_2m") List<Double> temperature2m,
        @JsonProperty("precipitation") List<Double> precipitation,
        @JsonProperty("wind_speed_10m") List<Double> windSpeed10m,
        @JsonProperty("relative_humidity_2m") List<Double> relativeHumidity2m,
        @JsonProperty("precipitation_probability") List<Integer> precipitationProbability
    ) {}
}
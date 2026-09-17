package edu.ftnhs.weather_manager.service;

import edu.ftnhs.weather_manager.dto.OpenMeteoResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;

@Service
public class WeatherApiClient {
    private static final ZoneId PH_ZONE = ZoneId.of("Asia/Manila");
    private static final String URL = "https://api.open-meteo.com/v1/forecast?latitude=9.876977&longitude=123.90734&current=temperature_2m,relative_humidity_2m,precipitation,rain,weather_code,cloud_cover,pressure_msl,wind_speed_10m,visibility,uv_index,precipitation_probability&hourly=temperature_2m,precipitation,wind_speed_10m,relative_humidity_2m,precipitation_probability&timezone=Asia%2FManila";
    private final RestClient restClient;
    private final Clock clock;
    private volatile OpenMeteoResponse cachedResponse;
    private volatile String lastResult = "No API request made since startup.";
    private Instant lastAttempt;

    public WeatherApiClient() {
        this(RestClient.create(), Clock.systemUTC());
    }

    WeatherApiClient(RestClient restClient, Clock clock) {
        this.restClient = restClient;
        this.clock = clock;
    }

    public boolean isAutomaticFetchWindow() {
        LocalTime time = LocalTime.now(clock.withZone(PH_ZONE));
        return !time.isBefore(LocalTime.of(5, 0)) && time.isBefore(LocalTime.of(18, 0));
    }

    // Only the Diagnostics action may bypass the window and request cooldown.
    // Return null when skipped so cached observations are not saved as new readings.
    public synchronized OpenMeteoResponse fetch(boolean manual) {
        Instant now = clock.instant();
        if (!manual && (!isAutomaticFetchWindow()
                || (lastAttempt != null && Duration.between(lastAttempt, now).compareTo(Duration.ofMinutes(10)) < 0))) {
            return null;
        }
        lastAttempt = now;
        try {
            OpenMeteoResponse response = restClient.get().uri(URL).retrieve().body(OpenMeteoResponse.class);
            if (response != null && response.current() != null && response.hourly() != null) {
                cachedResponse = response;
                lastResult = "Last request succeeded at " + now.atZone(PH_ZONE) + ".";
                return response;
            }
            lastResult = "Last request returned incomplete weather data.";
            return null;
        } catch (RuntimeException e) {
            lastResult = "Last request failed; retaining previously fetched data.";
            throw e;
        }
    }

    public OpenMeteoResponse getCachedResponse() {
        return cachedResponse;
    }

    public String getLastResult() {
        return lastResult;
    }
}

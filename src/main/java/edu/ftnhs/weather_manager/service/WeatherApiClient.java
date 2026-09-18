package edu.ftnhs.weather_manager.service;

import edu.ftnhs.weather_manager.dto.OpenMeteoResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.time.Clock;
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
    private Long lastAttemptSlot;

    public WeatherApiClient() {
        this(RestClient.create(), Clock.systemUTC());
    }

    WeatherApiClient(RestClient restClient, Clock clock) {
        this.restClient = restClient;
        this.clock = clock;
    }

    public boolean isAutomaticFetchWindow() {
        return isAutomaticFetchWindow(clock.instant());
    }

    private boolean isAutomaticFetchWindow(Instant now) {
        LocalTime time = now.atZone(PH_ZONE).toLocalTime();
        return !time.isBefore(LocalTime.of(5, 0)) && time.isBefore(LocalTime.of(18, 0));
    }

    // Only the Diagnostics action may bypass the window and per-slot request guard.
    // Return null when skipped so cached observations are not saved as new readings.
    public synchronized OpenMeteoResponse fetch(boolean manual) {
        Instant now = clock.instant();
        // Manila's UTC+8 offset aligns with these ten-minute boundaries.
        // A rolling 600-second cooldown skips valid cron runs when delivery times vary.
        long slot = Math.floorDiv(now.getEpochSecond(), 600);
        if (!manual && (!isAutomaticFetchWindow(now)
                || (lastAttemptSlot != null && slot <= lastAttemptSlot))) {
            return null;
        }
        // Reserve before sending, including failed attempts. Manual requests satisfy
        // the current slot but must not postpone the next scheduled slot.
        lastAttemptSlot = lastAttemptSlot == null ? slot : Math.max(lastAttemptSlot, slot);
        try {
            // URL is already encoded; the String overload would encode the percent sign again.
            OpenMeteoResponse response = restClient.get().uri(URI.create(URL)).retrieve().body(OpenMeteoResponse.class);
            if (response != null && response.current() != null && response.hourly() != null) {
                cachedResponse = response;
                lastResult = "Last request succeeded at " + now.atZone(PH_ZONE) + ".";
                return response;
            }
            lastResult = "Last request returned incomplete weather data.";
            return null;
        } catch (RestClientResponseException e) {
            String detail = e.getResponseBodyAsString().replaceAll("\\s+", " ").trim();
            if (detail.length() > 400) detail = detail.substring(0, 400) + "...";
            lastResult = "Open-Meteo returned HTTP " + e.getStatusCode().value()
                    + (detail.isEmpty() ? "." : ": " + detail) + cacheStatus();
            throw e;
        } catch (RuntimeException e) {
            lastResult = "Weather request failed (" + e.getClass().getSimpleName()
                    + "). Check the server logs for details." + cacheStatus();
            throw e;
        }
    }

    private String cacheStatus() {
        return cachedResponse == null ? " No forecast cached since startup."
                : " Previously fetched forecast retained.";
    }

    public OpenMeteoResponse getCachedResponse() {
        return cachedResponse;
    }

    public String getLastResult() {
        return lastResult;
    }
}

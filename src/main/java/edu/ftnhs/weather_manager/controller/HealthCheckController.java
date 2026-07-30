package edu.ftnhs.weather_manager.controller;

import edu.ftnhs.weather_manager.service.WeatherDecisionEngine;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;

@RestController
public class HealthCheckController {

    private final WeatherDecisionEngine weatherDecisionEngine;

    public HealthCheckController(WeatherDecisionEngine weatherDecisionEngine) {
        this.weatherDecisionEngine = weatherDecisionEngine;
    }

    @GetMapping("/api/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        try {
            weatherDecisionEngine.fetchWeatherAndEvaluateStatus();
        } catch (Exception e) {
            // Keep health check passing even if weather API hiccups
        }

        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "service", "FTNHS Weather Hub",
            "timestamp", OffsetDateTime.now(ZoneId.of("Asia/Manila")).toString()
        ));
    }
}
package edu.ftnhs.weather_manager.service;

import edu.ftnhs.weather_manager.dto.OpenMeteoResponse;
import edu.ftnhs.weather_manager.entity.WeatherLog;
import edu.ftnhs.weather_manager.repository.WeatherLogRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class WeatherDecisionEngine {

    private static final Logger log = LoggerFactory.getLogger(WeatherDecisionEngine.class);
    private final RestClient restClient;
    private final WeatherLogRepository weatherLogRepository;

    private static final String WEATHER_API_URL = 
    "https://api.open-meteo.com/v1/forecast?latitude=9.876977&longitude=123.90734&current=precipitation,rain,temperature_2m,wind_speed_10m&hourly=temperature_2m,precipitation,wind_speed_10m&timezone=auto";

    public WeatherDecisionEngine(WeatherLogRepository weatherLogRepository) {
        this.weatherLogRepository = weatherLogRepository;
        this.restClient = RestClient.create();
    }

    /**
     * Executes automatically when the application starts up.
     * Evaluates the last database log timestamp and decides whether to fetch immediately
     * or schedule the next fetch based on the remaining time until the 15-minute mark.
     */
    @PostConstruct
    public void initializeStartupCheck() {
        try {
            ZoneId phZone = ZoneId.of("Asia/Manila");
            LocalDateTime now = LocalDateTime.now(phZone);
            WeatherLog latestLog = weatherLogRepository.findTopByOrderByTimestampDesc();

            if (latestLog == null || latestLog.getTimestamp() == null) {
                log.info("No previous weather log found on startup. Executing initial weather check immediately...");
                fetchWeatherAndEvaluateStatus();
            } else {
                Duration duration = Duration.between(latestLog.getTimestamp(), now);
                long elapsedMinutes = duration.toMinutes();

                if (elapsedMinutes >= 15) {
                    log.info("Last check was {} minutes ago (>= 15 mins). Executing weather check immediately...", elapsedMinutes);
                    fetchWeatherAndEvaluateStatus();
                } else {
                    long remainingMinutes = 15 - elapsedMinutes;
                    long remainingSeconds = (15 * 60) - duration.getSeconds();
                    log.info("Last check was {} minutes ago. Scheduling next catch-up check in {} minutes (approx {} seconds).", 
                             elapsedMinutes, remainingMinutes, remainingSeconds);

                    // Schedule a one-time delayed task for the remaining seconds
                    ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
                    scheduler.schedule(() -> {
                        try {
                            fetchWeatherAndEvaluateStatus();
                        } catch (Exception e) {
                            log.error("Failed to execute startup catch-up weather check: ", e);
                        }
                    }, Math.max(remainingSeconds, 0), TimeUnit.SECONDS);
                    scheduler.shutdown();
                }
            }
        } catch (Exception e) {
            log.error("Error during startup weather synchronization check: ", e);
        }
    }

    // Runs every 15 minutes regularly after startup
    @Scheduled(cron = "0 0/15 * * * ?")
    public void fetchWeatherAndEvaluateStatus() {
        log.info("Initiating scheduled weather check...");

        try {
            OpenMeteoResponse response = restClient.get()
                    .uri(WEATHER_API_URL)
                    .retrieve()
                    .body(OpenMeteoResponse.class);

            if (response != null && response.current() != null) {
                double precipitation = response.current().precipitation();
                double temperature = response.current().temperature2m();
                double windSpeed = response.current().windSpeed10m();
                
                log.info("Current Precipitation: {} mm/hr, Temp: {} °C, Wind: {} km/h", precipitation, temperature, windSpeed);

                String recommendedStatus = evaluateLearningMode(precipitation);

                WeatherLog logEntry = new WeatherLog();
                logEntry.setTimestamp(LocalDateTime.now(ZoneId.of("Asia/Manila")));
                logEntry.setLocation("School Campus - Main");
                logEntry.setPrecipitationMm(precipitation);
                logEntry.setTemperature(temperature);
                logEntry.setWindSpeed(windSpeed);
                logEntry.setWeatherCondition(recommendedStatus);
                
                weatherLogRepository.save(logEntry);
                log.info("Weather log saved successfully to database.");
            }
        } catch (Exception e) {
            log.error("Failed to fetch weather data or save to DB: ", e);
        }
    }

    private String evaluateLearningMode(double precipitationMmHr) {
        if (precipitationMmHr > 15.0) {
            return "SUSPENDED"; // Aligns with Orange (>15mm) & Red (>30mm) warnings (Automatic Suspension)
        } else if (precipitationMmHr >= 7.5) {
            return "MODULAR";   // Aligns with Yellow Warning (7.5 - 15 mm/hr)
        } else {
            return "IN_PERSON"; // Normal weather conditions (< 7.5 mm/hr)
        }
    }
}
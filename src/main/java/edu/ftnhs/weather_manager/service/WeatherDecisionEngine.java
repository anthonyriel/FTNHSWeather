package edu.ftnhs.weather_manager.service;

import edu.ftnhs.weather_manager.dto.OpenMeteoResponse;
import edu.ftnhs.weather_manager.entity.LearningStatusLog;
import edu.ftnhs.weather_manager.entity.WeatherLog;
import edu.ftnhs.weather_manager.repository.LearningStatusLogRepository;
import edu.ftnhs.weather_manager.repository.WeatherLogRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.LocalDate;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class WeatherDecisionEngine {

    private static final Logger log = LoggerFactory.getLogger(WeatherDecisionEngine.class);
    private final RestClient restClient;
    private final WeatherLogRepository weatherLogRepository;
    private final LearningStatusLogRepository learningStatusLogRepository;
    private final PushNotificationService pushNotificationService;

    private static final String WEATHER_API_URL = 
    "https://api.open-meteo.com/v1/forecast?latitude=9.876977&longitude=123.90734&current=temperature_2m,relative_humidity_2m,precipitation,rain,weather_code,cloud_cover,pressure_msl,wind_speed_10m,visibility,uv_index,precipitation_probability&hourly=temperature_2m,precipitation,wind_speed_10m,relative_humidity_2m,precipitation_probability&timezone=auto";

    public WeatherDecisionEngine(WeatherLogRepository weatherLogRepository, 
                                 LearningStatusLogRepository learningStatusLogRepository,
                                 PushNotificationService pushNotificationService) {
        this.weatherLogRepository = weatherLogRepository;
        this.learningStatusLogRepository = learningStatusLogRepository;
        this.pushNotificationService = pushNotificationService;
        this.restClient = RestClient.create();
    }

    @PostConstruct
    public void initializeStartupCheck() {
        try {
            ZoneId phZone = ZoneId.of("Asia/Manila");
            OffsetDateTime now = OffsetDateTime.now(phZone);
            WeatherLog latestLog = weatherLogRepository.findTopByOrderByTimestampDesc();

            if (latestLog == null || latestLog.getTimestamp() == null) {
                log.info("No previous weather log found on startup. Executing initial weather check immediately...");
                fetchWeatherAndEvaluateStatus();
            } else {
                Duration duration = Duration.between(latestLog.getTimestamp(), now);
                long elapsedMinutes = duration.toMinutes();

                if (elapsedMinutes >= 10) {
                    log.info("Last check was {} minutes ago (>= 10 mins). Executing weather check immediately...", elapsedMinutes);
                    fetchWeatherAndEvaluateStatus();
                } else {
                    long remainingMinutes = 10 - elapsedMinutes;
                    long remainingSeconds = (10 * 60) - duration.getSeconds();
                    log.info("Last check was {} minutes ago. Scheduling next catch-up check in {} minutes (approx {} seconds).", 
                            elapsedMinutes, remainingMinutes, remainingSeconds);

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

    public void fetchWeatherAndEvaluateStatus() {
        log.info("Initiating weather check via external trigger...");

        try {
            OpenMeteoResponse response = restClient.get()
                    .uri(WEATHER_API_URL)
                    .retrieve()
                    .body(OpenMeteoResponse.class);

            if (response != null && response.current() != null) {
                var cur = response.current();

                double precipitation = cur.precipitation() != null ? cur.precipitation() : 0.0;
                double temperature = cur.temperature2m() != null ? cur.temperature2m() : 0.0;
                double windSpeed = cur.windSpeed10m() != null ? cur.windSpeed10m() : 0.0;
                double humidity = cur.relativeHumidity2m() != null ? cur.relativeHumidity2m() : 0.0;
                int cloudCover = cur.cloudCover() != null ? cur.cloudCover() : 0;
                double pressure = cur.pressureMsl() != null ? cur.pressureMsl() : 0.0;
                double visibility = cur.visibility() != null ? cur.visibility() : 0.0;
                double uvIndex = cur.uvIndex() != null ? cur.uvIndex() : 0.0;
                double precipProb = cur.precipitationProbability() != null ? cur.precipitationProbability().doubleValue() : 0.0;
                String weatherCondition = mapWeatherCode(cur.weatherCode());

                log.info("Current Weather -> Precip: {} mm/hr, Temp: {} °C, Wind: {} km/h, Humidity: {}%, Condition: {}", 
                         precipitation, temperature, windSpeed, humidity, weatherCondition);

                WeatherLog logEntry = new WeatherLog();
                logEntry.setTimestamp(OffsetDateTime.now(ZoneId.of("Asia/Manila")));
                logEntry.setLocation("Fermin Tayabas National High School");
                logEntry.setPrecipitationMm(precipitation);
                logEntry.setTemperature(temperature);
                logEntry.setWindSpeed(windSpeed);
                logEntry.setHumidity(humidity);
                logEntry.setCloudCover(cloudCover);
                logEntry.setPressure(pressure);
                logEntry.setVisibility(visibility);
                logEntry.setUvIndex(uvIndex);
                logEntry.setPrecipitationProbability(precipProb);
                logEntry.setSource("Open-Meteo API");
                logEntry.setWeatherCondition(weatherCondition);
                
                weatherLogRepository.save(logEntry);
                log.info("Weather log saved successfully to Supabase with all fields populated.");

                evaluateAndSaveLearningStatus(precipitation, windSpeed);
            }
        } catch (Exception e) {
            log.error("Failed to fetch weather data or save to DB: ", e);
        }
    }

    private String mapWeatherCode(Integer code) {
        if (code == null) return "Clear";
        return switch (code) {
            case 0 -> "Clear Sky";
            case 1, 2, 3 -> "Partly Cloudy";
            case 45, 48 -> "Foggy";
            case 51, 53, 55, 56, 57 -> "Drizzle";
            case 61, 63, 65, 66, 67 -> "Rain / Showers";
            case 71, 73, 75, 77 -> "Snow";
            case 80, 81, 82 -> "Rain Showers";
            case 95, 96, 99 -> "Thunderstorm";
            default -> "Clear";
        };
    }

    private void evaluateAndSaveLearningStatus(double rainMm, double windSpeed) {
        ZoneId phZone = ZoneId.of("Asia/Manila");
        LocalDate today = LocalDate.now(phZone);

        Optional<LearningStatusLog> existing = learningStatusLogRepository.findTopByTargetDateOrderByCreatedAtDesc(today);
        if (existing.isPresent() && Boolean.FALSE.equals(existing.get().getAutomatedBySystem())) {
            log.info("Manual override active for today. Skipping automated status update.");
            return;
        }

        // Default to HAYO (Continue)
        String mode = "IN_PERSON";
        String risk = "LOW";
        int confidence = 95;
        String reason = "Hayo (Continue): Clear skies or manageable weather. Standard classroom instruction proceeds.";

        // 1. Check for HINTO (Stop)
        if (rainMm > 15.0 || windSpeed > 60.0) {
            mode = "SUSPENDED";
            risk = "HIGH";
            confidence = 98;
            reason = "Hinto (Stop): Severe weather detected. Complete operational halt. Focus entirely on survival and safety.";
        } 
        // 2. Check for HINAY (Ease-in)
        else if (rainMm >= 7.5 || windSpeed >= 40.0) {
            mode = "MODULAR";
            risk = "MODERATE";
            confidence = 95;
            reason = "Hinay (Ease-in): Mild weather disruptions detected. Shift to Alternative Delivery Modes (ADM) with relaxed deadlines.";
        } 
        // 3. Check for HINGA (Check-in) - Triggered if yesterday was Suspended but today is clear
        else {
            LocalDate yesterday = today.minusDays(1);
            Optional<LearningStatusLog> yesterdayLog = learningStatusLogRepository.findTopByTargetDateOrderByCreatedAtDesc(yesterday);
            
            if (yesterdayLog.isPresent() && "SUSPENDED".equals(yesterdayLog.get().getStatus())) {
                mode = "CHECK_IN";
                risk = "LOW";
                confidence = 90;
                reason = "Hinga (Check-in): Aftermath of severe weather. Academics take a back seat. Focus on Psychological First Aid (PFA) and well-being checks.";
            }
        }

        LearningStatusLog statusLog = new LearningStatusLog();
        statusLog.setTargetDate(today);
        statusLog.setStatus(mode);
        statusLog.setAutomatedBySystem(true);
        statusLog.setReason(reason);
        statusLog.setRiskLevel(risk);
        statusLog.setConfidenceScore(BigDecimal.valueOf(confidence));
        statusLog.setRainfallUsed(rainMm);
        statusLog.setWindUsed(windSpeed);
        statusLog.setCreatedAt(OffsetDateTime.now(phZone));

        learningStatusLogRepository.save(statusLog);
        log.info("DepEd 4H status evaluated: Mode = {}, Risk = {}", mode, risk);

        // Automatically trigger push notification broadcast if weather requires advisory/suspension
        if (!"IN_PERSON".equalsIgnoreCase(mode) && !"CHECK_IN".equalsIgnoreCase(mode)) {
            try {
                String title = "FTNHS Weather Alert: " + mode;
                String body = String.format("Risk: %s | Rain: %.1f mm/hr. %s", risk, rainMm, reason);
                String payload = String.format("{\"title\":\"%s\", \"body\":\"%s\", \"url\":\"/\"}", title, body);
                
                pushNotificationService.sendNotificationToAll(payload);
                log.info("Automated push broadcast triggered for mode: {}", mode);
            } catch (Exception e) {
                log.error("Failed to send automated push broadcast: ", e);
            }
        }
    }
}
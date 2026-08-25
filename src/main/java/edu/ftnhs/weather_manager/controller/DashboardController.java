package edu.ftnhs.weather_manager.controller;

import edu.ftnhs.weather_manager.dto.OpenMeteoResponse;
import edu.ftnhs.weather_manager.dto.OverrideRequest;
import edu.ftnhs.weather_manager.entity.LearningStatusLog;
import edu.ftnhs.weather_manager.entity.OverrideLog;
import edu.ftnhs.weather_manager.entity.User;
import edu.ftnhs.weather_manager.entity.WeatherLog;
import edu.ftnhs.weather_manager.repository.LearningStatusLogRepository;
import edu.ftnhs.weather_manager.repository.NotificationLogRepository;
import edu.ftnhs.weather_manager.repository.OverrideLogRepository;
import edu.ftnhs.weather_manager.repository.UserRepository;
import edu.ftnhs.weather_manager.repository.WeatherLogRepository;
import edu.ftnhs.weather_manager.service.PushNotificationService;
import edu.ftnhs.weather_manager.service.WeatherDecisionEngine;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.List;
import edu.ftnhs.weather_manager.entity.NotificationLog;

@Controller
public class DashboardController {

    private final WeatherLogRepository weatherLogRepository;
    private final LearningStatusLogRepository learningStatusLogRepository;
    private final UserRepository userRepository;
    private final NotificationLogRepository notificationLogRepository;
    private final WeatherDecisionEngine weatherDecisionEngine;
    private final PushNotificationService pushNotificationService;
    private final OverrideLogRepository overrideLogRepository;
    private final PasswordEncoder passwordEncoder;
    private final ScheduledExecutorService overrideScheduler = Executors.newSingleThreadScheduledExecutor();

    public DashboardController(WeatherLogRepository weatherLogRepository,
                               LearningStatusLogRepository learningStatusLogRepository,
                               UserRepository userRepository,
                               NotificationLogRepository notificationLogRepository,
                               WeatherDecisionEngine weatherDecisionEngine,
                               PushNotificationService pushNotificationService,
                               OverrideLogRepository overrideLogRepository,
                               PasswordEncoder passwordEncoder) {
        this.weatherLogRepository = weatherLogRepository;
        this.learningStatusLogRepository = learningStatusLogRepository;
        this.userRepository = userRepository;
        this.notificationLogRepository = notificationLogRepository;
        this.weatherDecisionEngine = weatherDecisionEngine;
        this.pushNotificationService = pushNotificationService;
        this.overrideLogRepository = overrideLogRepository;
        this.passwordEncoder = passwordEncoder;
    }

    private boolean checkAdmin(String adminUser) {
        return adminUser != null && !adminUser.trim().isEmpty();
    }

    private String decodeCookieValue(String value) {
        if (value == null || value.trim().isEmpty()) return "Admin";
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8.toString());
        } catch (Exception e) {
            return value;
        }
    }

    // Global Model Attributes available for ALL pages/templates
    @ModelAttribute("isAdminLoggedIn")
    public boolean addAdminLoggedInAttribute(@CookieValue(value = "adminUser", required = false) String adminUser) {
        return checkAdmin(adminUser);
    }

    @ModelAttribute("adminName")
    public String addAdminNameAttribute(@CookieValue(value = "adminName", required = false) String adminName) {
        return decodeCookieValue(adminName);
    }

    @GetMapping("/")
    public String viewDashboard(Model model) {

        ZoneId phZone = ZoneId.of("Asia/Manila");
        LocalDate today = LocalDate.now(phZone);
        OffsetDateTime now = OffsetDateTime.now(phZone);

        WeatherLog latestLog = weatherLogRepository.findTopByOrderByTimestampDesc();
        Optional<LearningStatusLog> todayStatus = learningStatusLogRepository.findTopByTargetDateOrderByCreatedAtDesc(today);
        
        String currentMode = "IN_PERSON";
        String precipitation = "0.0";
        String temperature = "0.0";
        String windSpeed = "0.0";
        String humidity = "0.0";
        String cloudCover = "0";
        String weatherCondition = "Clear";
        String lastUpdated = "Waiting for first cron job run...";
        long secondsSinceLastUpdate = 600; // Default to full 10 mins

        int confidenceScore = 0;
        String riskLevel = "UNKNOWN";
        String decisionReason = "Awaiting system analysis.";
        String rainUsed = "0.0";
        String windUsed = "0.0";

        double highestRainToday = 0.0;
        double peakWindToday = 0.0;
        List<WeatherLog> allLogs = weatherLogRepository.findAllByOrderByTimestampDesc();
        for (WeatherLog logItem : allLogs) {
            if (logItem.getTimestamp() != null) {
                LocalDate logDate = logItem.getTimestamp().atZoneSameInstant(phZone).toLocalDate();
                if (logDate.equals(today)) {
                    if (logItem.getPrecipitationMm() > highestRainToday) {
                        highestRainToday = logItem.getPrecipitationMm();
                    }
                    if (logItem.getWindSpeed() != null && logItem.getWindSpeed() > peakWindToday) {
                        peakWindToday = logItem.getWindSpeed();
                    }
                }
            }
        }

        int wmoCode = 0;
        
        if (latestLog != null) {
            precipitation = String.valueOf(latestLog.getPrecipitationMm());
            temperature = String.valueOf(latestLog.getTemperature() != null ? latestLog.getTemperature() : 0.0);
            windSpeed = String.valueOf(latestLog.getWindSpeed() != null ? latestLog.getWindSpeed() : 0.0);
            humidity = String.valueOf(latestLog.getHumidity() != null ? latestLog.getHumidity() : 0.0);
            cloudCover = String.valueOf(latestLog.getCloudCover() != null ? latestLog.getCloudCover() : 0);
            
            weatherCondition = latestLog.getWeatherCondition() != null ? latestLog.getWeatherCondition() : "Unknown";
            
            String condLower = weatherCondition.toLowerCase();
            if (condLower.contains("clear")) wmoCode = 0;
            else if (condLower.contains("partly")) wmoCode = 2;
            else if (condLower.contains("cloud")) wmoCode = 3;
            else if (condLower.contains("fog")) wmoCode = 45;
            else if (condLower.contains("drizzle")) wmoCode = 51;
            else if (condLower.contains("heavy rain")) wmoCode = 65;
            else if (condLower.contains("rain") || condLower.contains("shower")) wmoCode = 61;
            else if (condLower.contains("snow")) wmoCode = 71;
            else if (condLower.contains("thunder")) wmoCode = 95;

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy - hh:mm a");
            OffsetDateTime phTime = latestLog.getTimestamp().atZoneSameInstant(phZone).toOffsetDateTime();
            lastUpdated = phTime.format(formatter);

            // Calculate exact seconds elapsed for UI timer sync
            Duration diff = Duration.between(latestLog.getTimestamp(), now);
            secondsSinceLastUpdate = diff.getSeconds();
        }

        boolean hasActiveOverride = false;

        if (todayStatus.isPresent()) {
            LearningStatusLog log = todayStatus.get();
            currentMode = log.getStatus();
            
            confidenceScore = log.getConfidenceScore() != null ? log.getConfidenceScore().intValue() : 95;
            riskLevel = log.getRiskLevel() != null ? log.getRiskLevel() : ((currentMode.equals("IN_PERSON") || currentMode.equals("CHECK_IN")) ? "LOW" : "HIGH");
            decisionReason = log.getReason() != null ? log.getReason() : "Learning mode updated based on current conditions.";
            rainUsed = log.getRainfallUsed() != null ? String.valueOf(log.getRainfallUsed()) : precipitation;
            windUsed = log.getWindUsed() != null ? String.valueOf(log.getWindUsed()) : windSpeed;

            if (log.getAutomatedBySystem() != null && !log.getAutomatedBySystem()) {
                hasActiveOverride = true;
                lastUpdated = "MANUAL OVERRIDE APPLIED";
                confidenceScore = 100;
                riskLevel = "ADMIN FORCED";
            }
        }

        String bannerClass = switch (currentMode) {
            case "IN_PERSON" -> "bg-success";
            case "MODULAR" -> "bg-warning text-dark";
            case "CHECK_IN" -> "bg-info text-dark";
            case "SUSPENDED" -> "bg-danger";
            default -> "bg-secondary";
        };

        model.addAttribute("learningMode", currentMode);
        model.addAttribute("wmoCode", wmoCode);
        model.addAttribute("precipitation", precipitation);
        model.addAttribute("temperature", temperature);
        model.addAttribute("windSpeed", windSpeed);
        model.addAttribute("lastUpdated", lastUpdated);
        model.addAttribute("secondsSinceLastUpdate", secondsSinceLastUpdate);
        model.addAttribute("bannerClass", bannerClass);
        model.addAttribute("hasActiveOverride", hasActiveOverride);

        model.addAttribute("humidity", humidity);
        model.addAttribute("cloudCover", cloudCover);
        model.addAttribute("weatherCondition", weatherCondition);
        model.addAttribute("confidenceScore", confidenceScore);
        model.addAttribute("riskLevel", riskLevel);
        model.addAttribute("decisionReason", decisionReason);
        model.addAttribute("rainUsed", rainUsed);
        model.addAttribute("windUsed", windUsed);
        model.addAttribute("highestRainToday", highestRainToday);
        model.addAttribute("peakWindToday", peakWindToday);

        // Add recent notification broadcast history logs (null-safe sorting with explicit types)
        List<NotificationLog> notificationLogs = notificationLogRepository.findAll().stream()
            .sorted((NotificationLog l1, NotificationLog l2) -> {
                if (l1.getSentAt() == null && l2.getSentAt() == null) return 0;
                if (l1.getSentAt() == null) return 1;
                if (l2.getSentAt() == null) return -1;
                return l2.getSentAt().compareTo(l1.getSentAt());
            })
            .limit(10)
            .collect(Collectors.toList());
        model.addAttribute("notificationLogs", notificationLogs);

        // Fetch forecast data for the Mode Projection feature
        RestClient restClient = RestClient.create();
        String url = "https://api.open-meteo.com/v1/forecast?latitude=9.876977&longitude=123.90734&hourly=precipitation,wind_speed_10m&timezone=auto";
        try {
            OpenMeteoResponse forecastResponse = restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(OpenMeteoResponse.class);
            
            if (forecastResponse != null && forecastResponse.hourly() != null) {
                model.addAttribute("forecastTimes", forecastResponse.hourly().time());
                model.addAttribute("forecastPrecip", forecastResponse.hourly().precipitation());
                model.addAttribute("forecastWind", forecastResponse.hourly().windSpeed10m());
            }
        } catch (Exception e) {
            model.addAttribute("forecastError", "Unable to fetch projection data.");
        }

        return "dashboard"; 
    }

    @GetMapping("/login")
    public String viewLogin() {
        return "login";
    }

    @PostMapping("/login")
    public String processLogin(@RequestParam String username, @RequestParam String password, HttpServletResponse response, Model model) {
        Optional<User> userOpt = userRepository.findByUsername(username);

        if (userOpt.isPresent()) {
            User user = userOpt.get();
            boolean isAdmin = user.getRole() != null && user.getRole().trim().equalsIgnoreCase("ADMIN");
            String storedHash = user.getPasswordHash();

            boolean isBcrypt = storedHash != null && storedHash.startsWith("$2");
            boolean passwordMatches;

            if (isBcrypt) {
                passwordMatches = passwordEncoder.matches(password, storedHash);
            } else {
                passwordMatches = password.equals(storedHash);
                if (passwordMatches) {
                    user.setPasswordHash(passwordEncoder.encode(password));
                    userRepository.save(user);
                }
            }

            if (isAdmin && passwordMatches) {
                try {
                    String encodedUser = URLEncoder.encode(user.getUsername(), StandardCharsets.UTF_8.toString());
                    String encodedName = URLEncoder.encode(user.getName() != null ? user.getName() : "Admin", StandardCharsets.UTF_8.toString());

                    Cookie userCookie = new Cookie("adminUser", encodedUser);
                    userCookie.setPath("/");
                    userCookie.setMaxAge(30 * 24 * 60 * 60);
                    userCookie.setSecure(true);
                    userCookie.setHttpOnly(true);
                    response.addCookie(userCookie);

                    Cookie nameCookie = new Cookie("adminName", encodedName);
                    nameCookie.setPath("/");
                    nameCookie.setMaxAge(30 * 24 * 60 * 60);
                    nameCookie.setSecure(true);
                    nameCookie.setHttpOnly(true);
                    response.addCookie(nameCookie);
                } catch (Exception e) {
                    Cookie userCookie = new Cookie("adminUser", user.getUsername());
                    userCookie.setPath("/");
                    userCookie.setMaxAge(30 * 24 * 60 * 60);
                    userCookie.setSecure(true);
                    userCookie.setHttpOnly(true);
                    response.addCookie(userCookie);
                }

                return "redirect:/";
            }
        }

        model.addAttribute("error", "Invalid username or password.");
        return "login";
    }

    @PostMapping("/logout")
    public String logout(HttpServletResponse response) {
        Cookie userCookie = new Cookie("adminUser", "");
        userCookie.setPath("/");
        userCookie.setMaxAge(0);
        userCookie.setSecure(true);
        userCookie.setHttpOnly(true);
        response.addCookie(userCookie);

        Cookie nameCookie = new Cookie("adminName", "");
        nameCookie.setPath("/");
        nameCookie.setMaxAge(0);
        nameCookie.setSecure(true);
        nameCookie.setHttpOnly(true);
        response.addCookie(nameCookie);

        return "redirect:/";
    }

    @PostMapping("/admin/override")
    public String overrideStatus(@RequestParam String overrideMode, @CookieValue(value = "adminUser", required = false) String adminUser) {
        if (!checkAdmin(adminUser)) return "redirect:/login";

        ZoneId phZone = ZoneId.of("Asia/Manila");
        LocalDate today = LocalDate.now(phZone);
        
        learningStatusLogRepository.deleteByTargetDate(today);

        LearningStatusLog manualLog = new LearningStatusLog();
        manualLog.setTargetDate(today);
        manualLog.setStatus(overrideMode);
        manualLog.setAutomatedBySystem(false);
        manualLog.setReason("Admin forced manual override via Dashboard");
        manualLog.setCreatedAt(OffsetDateTime.now(phZone));
        
        learningStatusLogRepository.save(manualLog);
        return "redirect:/"; 
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('PRINCIPAL')")
    @PostMapping("/api/v1/override")
    public ResponseEntity<?> handleApiOverride(@RequestBody OverrideRequest request, 
                                               @CookieValue(value = "adminUser", required = false) String adminUser) {
        if (!checkAdmin(adminUser)) {
            return ResponseEntity.status(401).body("Unauthorized");
        }

        ZoneId phZone = ZoneId.of("Asia/Manila");
        LocalDate today = LocalDate.now(phZone);
        
        Optional<LearningStatusLog> existingStatus = learningStatusLogRepository.findTopByTargetDateOrderByCreatedAtDesc(today);
        String previousMode = existingStatus.map(log -> log != null ? log.getStatus() : "IN_PERSON").orElse("IN_PERSON");

        learningStatusLogRepository.deleteByTargetDate(today);

        String fullReason = "Reason: " + request.getReason();
        if (request.getNotes() != null && !request.getNotes().trim().isEmpty()) {
            fullReason += " | Notes: " + request.getNotes();
        }

        LearningStatusLog manualLog = new LearningStatusLog();
        manualLog.setTargetDate(today);
        manualLog.setStatus(request.getMode());
        manualLog.setAutomatedBySystem(false);
        manualLog.setReason(fullReason);
        manualLog.setCreatedAt(OffsetDateTime.now(phZone));
        learningStatusLogRepository.save(manualLog);

        OverrideLog auditLog = new OverrideLog();
        auditLog.setUserId(decodeCookieValue(adminUser));
        auditLog.setPreviousMode(previousMode);
        auditLog.setNewMode(request.getMode());
        auditLog.setReason(request.getReason());
        auditLog.setNotes(request.getNotes());
        auditLog.setCreatedAt(OffsetDateTime.now(phZone));
        overrideLogRepository.save(auditLog);

        long ttlMinutes = request.getDurationMinutes();
        overrideScheduler.schedule(() -> {
            try {
                LocalDate targetDay = LocalDate.now(phZone);
                learningStatusLogRepository.deleteByTargetDate(targetDay);
                System.out.println("Override TTL expired after " + ttlMinutes + " minutes. Reverted system back to automatic evaluation.");
            } catch (Exception e) {
                System.err.println("Failed to auto-revert override status: " + e.getMessage());
            }
        }, ttlMinutes, TimeUnit.MINUTES);

        return ResponseEntity.ok().body("Override applied successfully with audit logging and TTL: " + ttlMinutes + " minutes.");
    }

    @PostMapping("/admin/revert-automatic")
    public String revertToAutomatic(@CookieValue(value = "adminUser", required = false) String adminUser) {
        if (!checkAdmin(adminUser)) return "redirect:/login";

        ZoneId phZone = ZoneId.of("Asia/Manila");
        LocalDate today = LocalDate.now(phZone);
        learningStatusLogRepository.deleteByTargetDate(today);
        return "redirect:/";
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('TEACHER')")
    @PostMapping("/admin/send-advisory")
    public String sendManualAdvisory(@RequestParam String title, 
                                     @RequestParam String body, 
                                     @CookieValue(value = "adminUser", required = false) String adminUser) {
        if (!checkAdmin(adminUser)) return "redirect:/login";

        if (title == null || title.trim().isEmpty()) title = "FTNHS Weather Advisory";
        if (body == null || body.trim().isEmpty()) body = "Important update from FTNHS Administration.";

        String safeTitle = title.replace("\"", "'");
        String safeBody = body.replace("\"", "'");

        String payload = String.format("{\"title\":\"%s\", \"body\":\"%s\", \"url\":\"/\"}", safeTitle, safeBody);
        
        pushNotificationService.sendNotificationToAll(payload);

        return "redirect:/?success=advisory_sent";
    }

    @GetMapping("/admin/users")
    public String viewUserManagement(Model model, @CookieValue(value = "adminUser", required = false) String adminUser) {
        if (!checkAdmin(adminUser)) return "redirect:/login";

        model.addAttribute("users", userRepository.findByIsActiveTrue());
        return "manage-users";
    }

    @PostMapping("/admin/users/save")
    public String saveUser(@RequestParam(required = false) UUID id,
                           @RequestParam String username,
                           @RequestParam String email,
                           @RequestParam String name,
                           @RequestParam String role,
                           @RequestParam(required = false) String phone,
                           @RequestParam(required = false) String department,
                           @RequestParam(required = false) String position,
                           @RequestParam(required = false) String password,
                           @CookieValue(value = "adminUser", required = false) String adminUser) {
        if (!checkAdmin(adminUser)) return "redirect:/login";

        User user;
        if (id != null) {
            user = userRepository.findById(id).orElse(new User());
            if (password != null && !password.trim().isEmpty()) {
                user.setPasswordHash(passwordEncoder.encode(password));
            }
        } else {
            user = new User();
            user.setPasswordHash(passwordEncoder.encode(password));
            user.setStatus("ACTIVE");
            user.setIsActive(true);
        }
        user.setUsername(username);
        user.setEmail(email);
        user.setName(name);
        user.setRole(role);
        user.setPhone(phone);
        user.setDepartment(department);
        user.setPosition(position);

        userRepository.save(user);
        return "redirect:/admin/users";
    }

    @PostMapping("/admin/users/delete/{id}")
    public String deleteUser(@PathVariable UUID id, @CookieValue(value = "adminUser", required = false) String adminUser) {
        if (!checkAdmin(adminUser)) return "redirect:/login";
        
        userRepository.softDeleteUser(id);
        
        return "redirect:/admin/users";
    }

    @GetMapping("/test-weather")
    public String testWeatherFetch() {
        weatherDecisionEngine.fetchWeatherAndEvaluateStatus();
        return "redirect:/";
    }

    @GetMapping("/forecast")
    public String viewForecast(Model model) {
        RestClient restClient = RestClient.create();
        String url = "https://api.open-meteo.com/v1/forecast?latitude=9.876977&longitude=123.90734&hourly=temperature_2m,precipitation,wind_speed_10m&timezone=auto";
        
        try {
            OpenMeteoResponse response = restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(OpenMeteoResponse.class);
            
            if (response != null && response.hourly() != null) {
                model.addAttribute("hourlyTimes", response.hourly().time());
                model.addAttribute("hourlyTemps", response.hourly().temperature2m());
                model.addAttribute("hourlyPrecip", response.hourly().precipitation());
                model.addAttribute("hourlyWind", response.hourly().windSpeed10m());
            }
        } catch (Exception e) {
            model.addAttribute("error", "Unable to fetch advance forecast data at the moment.");
        }
        
        return "forecast";
    }

    @GetMapping("/history")
    public String viewWeatherHistory(Model model) {
        ZoneId phZone = ZoneId.of("Asia/Manila");
        
        List<WeatherLog> allLogs = weatherLogRepository.findAllByOrderByTimestampDesc();
        
        // Find the latest timestamp to establish the rolling 1-week window
        OffsetDateTime latestTimestamp = null;
        for (WeatherLog log : allLogs) {
            if (log.getTimestamp() != null) {
                latestTimestamp = log.getTimestamp();
                break;
            }
        }
        
        final OffsetDateTime cutoff = (latestTimestamp != null) ? latestTimestamp.minusDays(7) : null;

        // Filter logs to only include the last 1 week from the latest recorded log
        List<WeatherLog> logs = allLogs.stream()
            .filter(log -> {
                if (log.getTimestamp() == null) return false;
                if (cutoff != null) {
                    return !log.getTimestamp().isBefore(cutoff);
                }
                return true;
            })
            .map(log -> {
                OffsetDateTime manilaTime = log.getTimestamp()
                        .atZoneSameInstant(phZone)
                        .toOffsetDateTime();
                log.setTimestamp(manilaTime);
                return log;
            })
            .collect(Collectors.toList());

        model.addAttribute("weatherLogs", logs);
        return "history";
    }

    @GetMapping("/report/daily")
    public String viewDailyReport(Model model) {
        ZoneId phZone = ZoneId.of("Asia/Manila");
        LocalDate today = LocalDate.now(phZone);

        Optional<LearningStatusLog> todayStatus = learningStatusLogRepository.findTopByTargetDateOrderByCreatedAtDesc(today);
        WeatherLog latestLog = weatherLogRepository.findTopByOrderByTimestampDesc();

        String currentMode = "IN_PERSON";
        String reason = "Normal operating conditions maintained across campus.";
        String riskLevel = "LOW";

        if (todayStatus.isPresent()) {
            LearningStatusLog log = todayStatus.get();
            if (log.getStatus() != null) currentMode = log.getStatus();
            if (log.getReason() != null) reason = log.getReason();
            if (log.getRiskLevel() != null) riskLevel = log.getRiskLevel();
        }

        double highestRain = 0.0;
        double peakWind = 0.0;
        List<WeatherLog> allLogs = weatherLogRepository.findAllByOrderByTimestampDesc();
        for (WeatherLog log : allLogs) {
            if (log.getTimestamp() != null && log.getTimestamp().atZoneSameInstant(phZone).toLocalDate().equals(today)) {
                if (log.getPrecipitationMm() > highestRain) {
                    highestRain = log.getPrecipitationMm();
                }
                if (log.getWindSpeed() != null && log.getWindSpeed() > peakWind) {
                    peakWind = log.getWindSpeed();
                }
            }
        }

        model.addAttribute("reportDate", today.format(DateTimeFormatter.ofPattern("MMMM dd, yyyy")));
        model.addAttribute("currentMode", currentMode);
        model.addAttribute("reason", reason);
        model.addAttribute("riskLevel", riskLevel);
        model.addAttribute("highestRain", highestRain);
        model.addAttribute("peakWind", peakWind);
        model.addAttribute("latestTemp", latestLog != null && latestLog.getTemperature() != null ? latestLog.getTemperature() : 0.0);
        model.addAttribute("latestHumidity", latestLog != null && latestLog.getHumidity() != null ? latestLog.getHumidity() : 0.0);
        model.addAttribute("generatedTimestamp", OffsetDateTime.now(phZone).format(DateTimeFormatter.ofPattern("MMM dd, yyyy - hh:mm a")));

        return "daily-report";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/diagnostics")
    public String viewDiagnostics(Model model, @CookieValue(value = "adminUser", required = false) String adminUser) {
        if (!checkAdmin(adminUser)) return "redirect:/login";

        ZoneId phZone = ZoneId.of("Asia/Manila");
        
        long totalWeatherLogs = weatherLogRepository.count();
        long totalUsers = userRepository.count();
        long totalStatusLogs = learningStatusLogRepository.count();

        WeatherLog latestLog = weatherLogRepository.findTopByOrderByTimestampDesc();
        String lastLogTime = "No records found";
        if (latestLog != null && latestLog.getTimestamp() != null) {
            lastLogTime = latestLog.getTimestamp().atZoneSameInstant(phZone)
                    .format(DateTimeFormatter.ofPattern("MMM dd, yyyy - hh:mm:ss a"));
        }

        boolean apiConnected = false;
        String apiResponseMsg = "OK";
        RestClient restClient = RestClient.create();
        String testUrl = "https://api.open-meteo.com/v1/forecast?latitude=9.876977&longitude=123.90734&current=temperature_2m,precipitation,wind_speed_10m&timezone=auto";
        
        try {
            String response = restClient.get().uri(testUrl).retrieve().body(String.class);
            if (response != null && !response.isEmpty()) {
                apiConnected = true;
            }
        } catch (Exception e) {
            apiResponseMsg = "Error: " + e.getMessage();
        }

        model.addAttribute("totalWeatherLogs", totalWeatherLogs);
        model.addAttribute("totalUsers", totalUsers);
        model.addAttribute("totalStatusLogs", totalStatusLogs);
        model.addAttribute("lastLogTime", lastLogTime);
        model.addAttribute("apiConnected", apiConnected);
        model.addAttribute("apiResponseMsg", apiResponseMsg);
        model.addAttribute("serverTime", OffsetDateTime.now(phZone).format(DateTimeFormatter.ofPattern("MMM dd, yyyy - hh:mm:ss a")));

        return "diagnostics";
    }

    @GetMapping("/about")
    public String viewAbout() {
        return "about";
    }

    @GetMapping("/history/export")
    public void exportWeatherLogsCsv(HttpServletResponse response, @CookieValue(value = "adminUser", required = false) String adminUser) throws IOException {
        if (!checkAdmin(adminUser)) {
            response.sendRedirect("/login");
            return;
        }

        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=ftnhs_weather_logs_" + LocalDate.now() + ".csv");

        List<WeatherLog> logs = weatherLogRepository.findAllByOrderByTimestampDesc();
        ZoneId phZone = ZoneId.of("Asia/Manila");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        try (PrintWriter writer = response.getWriter()) {
            writer.println("Timestamp (PST),Location,Condition / Learning Mode,Temperature (°C),Rainfall (mm/hr),Wind Speed (km/h),Humidity (%),Cloud Cover (%)");

            for (WeatherLog log : logs) {
                String timeStr = "";
                if (log.getTimestamp() != null) {
                    timeStr = log.getTimestamp().atZoneSameInstant(phZone).format(formatter);
                }

                writer.printf("\"%s\",\"%s\",\"%s\",%.1f,%.1f,%.1f,%.1f,%d%n",
                    timeStr,
                    log.getLocation() != null ? log.getLocation() : "Campus",
                    log.getWeatherCondition() != null ? log.getWeatherCondition() : "N/A",
                    log.getTemperature() != null ? log.getTemperature() : 0.0,
                    log.getPrecipitationMm(),
                    log.getWindSpeed() != null ? log.getWindSpeed() : 0.0,
                    log.getHumidity() != null ? log.getHumidity() : 0.0,
                    log.getCloudCover() != null ? log.getCloudCover() : 0
                );
            }
            writer.flush();
        }
    }

    @GetMapping("/analytics")
    public String viewAnalytics(Model model) {
        ZoneId phZone = ZoneId.of("Asia/Manila");
        LocalDate today = LocalDate.now(phZone);
        int currentMonth = today.getMonthValue();
        int currentYear = today.getYear();
        
        List<WeatherLog> logs = weatherLogRepository.findAllByOrderByTimestampDesc();
        
        double monthlyRainTotal = 0.0;
        double tempSum = 0.0;
        int tempCount = 0;
        double monthlyPeakWind = 0.0;
        
        for (WeatherLog log : logs) {
            if (log.getTimestamp() != null) {
                LocalDate logDate = log.getTimestamp().atZoneSameInstant(phZone).toLocalDate();
                if (logDate.getMonthValue() == currentMonth && logDate.getYear() == currentYear) {
                    monthlyRainTotal += log.getPrecipitationMm();
                    if (log.getTemperature() != null) {
                        tempSum += log.getTemperature();
                        tempCount++;
                    }
                    if (log.getWindSpeed() != null && log.getWindSpeed() > monthlyPeakWind) {
                        monthlyPeakWind = log.getWindSpeed();
                    }
                }
            }
        }
        
        double monthlyAvgTemp = tempCount > 0 ? (tempSum / tempCount) : 0.0;

        long totalSuspensionsThisMonth = learningStatusLogRepository.findAll().stream()
                .filter(statusLog -> statusLog.getTargetDate() != null 
                        && statusLog.getTargetDate().getMonthValue() == currentMonth 
                        && statusLog.getTargetDate().getYear() == currentYear 
                        && "SUSPENDED".equals(statusLog.getStatus()))
                .count();

        List<WeatherLog> chronologicalLogs = logs.stream()
                .filter(log -> log.getTimestamp() != null)
                .sorted((a, b) -> a.getTimestamp().compareTo(b.getTimestamp()))
                .collect(Collectors.toList());

        List<String> timestamps = chronologicalLogs.stream()
                .map(log -> log.getTimestamp().atZoneSameInstant(phZone).format(DateTimeFormatter.ofPattern("MMM dd HH:mm")))
                .collect(Collectors.toList());

        List<Double> temperatures = chronologicalLogs.stream()
                .map(log -> log.getTemperature() != null ? log.getTemperature() : 0.0)
                .collect(Collectors.toList());

        List<Double> precipitation = chronologicalLogs.stream()
                .map(log -> log.getPrecipitationMm())
                .collect(Collectors.toList());

        List<Double> windSpeeds = chronologicalLogs.stream()
                .map(log -> log.getWindSpeed() != null ? log.getWindSpeed() : 0.0)
                .collect(Collectors.toList());

        model.addAttribute("currentMonthName", today.format(DateTimeFormatter.ofPattern("MMMM yyyy")));
        model.addAttribute("monthlyRainTotal", String.format("%.1f", monthlyRainTotal));
        model.addAttribute("monthlyAvgTemp", String.format("%.1f", monthlyAvgTemp));
        model.addAttribute("monthlyPeakWind", String.format("%.1f", monthlyPeakWind));
        model.addAttribute("totalSuspensionsThisMonth", totalSuspensionsThisMonth);

        model.addAttribute("timestamps", timestamps);
        model.addAttribute("temperatures", temperatures);
        model.addAttribute("precipitation", precipitation);
        model.addAttribute("windSpeeds", windSpeeds);
        
        return "analytics";
    }
}
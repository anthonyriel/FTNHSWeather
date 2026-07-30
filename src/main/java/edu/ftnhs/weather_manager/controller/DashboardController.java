package edu.ftnhs.weather_manager.controller;

import edu.ftnhs.weather_manager.dto.OpenMeteoResponse;
import edu.ftnhs.weather_manager.entity.LearningStatusLog;
import edu.ftnhs.weather_manager.entity.User;
import edu.ftnhs.weather_manager.entity.WeatherLog;
import edu.ftnhs.weather_manager.repository.LearningStatusLogRepository;
import edu.ftnhs.weather_manager.repository.UserRepository;
import edu.ftnhs.weather_manager.repository.WeatherLogRepository;
import edu.ftnhs.weather_manager.service.WeatherDecisionEngine;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.List;

@Controller
public class DashboardController {

    private final WeatherLogRepository weatherLogRepository;
    private final LearningStatusLogRepository learningStatusLogRepository;
    private final UserRepository userRepository;
    private final WeatherDecisionEngine weatherDecisionEngine;

    public DashboardController(WeatherLogRepository weatherLogRepository, 
                               LearningStatusLogRepository learningStatusLogRepository,
                               UserRepository userRepository,
                               WeatherDecisionEngine weatherDecisionEngine) {
        this.weatherLogRepository = weatherLogRepository;
        this.learningStatusLogRepository = learningStatusLogRepository;
        this.userRepository = userRepository;
        this.weatherDecisionEngine = weatherDecisionEngine;
    }

    @GetMapping("/")
    public String viewDashboard(Model model, HttpSession session) {
        ZoneId phZone = ZoneId.of("Asia/Manila");
        LocalDate today = LocalDate.now(phZone);

        WeatherLog latestLog = weatherLogRepository.findTopByOrderByTimestampDesc();
        Optional<LearningStatusLog> todayStatus = learningStatusLogRepository.findTopByTargetDateOrderByCreatedAtDesc(today);
        
        // Default values
        String currentMode = "IN_PERSON";
        String precipitation = "0.0";
        String temperature = "0.0";
        String windSpeed = "0.0";
        String humidity = "0.0";
        String cloudCover = "0";
        String weatherCondition = "Clear";
        String lastUpdated = "Waiting for first cron job run...";

        // Advanced Decision Variables
        int confidenceScore = 0;
        String riskLevel = "UNKNOWN";
        String decisionReason = "Awaiting system analysis.";
        String rainUsed = "0.0";
        String windUsed = "0.0";

        // Calculate Today's Summary Metrics (Highest Rain & Peak Wind)
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

        if (latestLog != null) {
            precipitation = String.valueOf(latestLog.getPrecipitationMm());
            temperature = String.valueOf(latestLog.getTemperature() != null ? latestLog.getTemperature() : 0.0);
            windSpeed = String.valueOf(latestLog.getWindSpeed() != null ? latestLog.getWindSpeed() : 0.0);
            humidity = String.valueOf(latestLog.getHumidity() != null ? latestLog.getHumidity() : 0.0);
            cloudCover = String.valueOf(latestLog.getCloudCover() != null ? latestLog.getCloudCover() : 0);
            weatherCondition = latestLog.getWeatherCondition() != null ? latestLog.getWeatherCondition() : "Unknown";
            
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy - hh:mm a");
            OffsetDateTime phTime = latestLog.getTimestamp().atZoneSameInstant(phZone).toOffsetDateTime();
            lastUpdated = phTime.format(formatter);
        }

        boolean hasActiveOverride = false;

        if (todayStatus.isPresent()) {
            LearningStatusLog log = todayStatus.get();
            currentMode = log.getStatus();
            
            confidenceScore = log.getConfidenceScore() != null ? log.getConfidenceScore().intValue() : 95;
            riskLevel = log.getRiskLevel() != null ? log.getRiskLevel() : (currentMode.equals("IN_PERSON") ? "LOW" : "HIGH");
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
            case "SUSPENDED" -> "bg-danger";
            default -> "bg-secondary";
        };

        boolean isAdminLoggedIn = session.getAttribute("adminUser") != null;

        // Base Data
        model.addAttribute("learningMode", currentMode);
        model.addAttribute("precipitation", precipitation);
        model.addAttribute("temperature", temperature);
        model.addAttribute("windSpeed", windSpeed);
        model.addAttribute("lastUpdated", lastUpdated);
        model.addAttribute("bannerClass", bannerClass);
        model.addAttribute("hasActiveOverride", hasActiveOverride);
        model.addAttribute("isAdminLoggedIn", isAdminLoggedIn);

        // Extended Data
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

        return "dashboard"; 
    }

    @GetMapping("/login")
    public String viewLogin() {
        return "login";
    }

    @PostMapping("/login")
    public String processLogin(@RequestParam String username, @RequestParam String password, HttpSession session, Model model) {
        Optional<User> adminUser = userRepository.findByUsernameAndRole(username, "ADMIN");

        if (adminUser.isPresent() && password.equals(adminUser.get().getPasswordHash())) {
            session.setAttribute("adminUser", adminUser.get().getUsername());
            session.setAttribute("adminName", adminUser.get().getName());
            return "redirect:/";
        } else {
            model.addAttribute("error", "Invalid username or password.");
            return "login";
        }
    }

    @PostMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/";
    }

    @PostMapping("/admin/override")
    public String overrideStatus(@RequestParam String overrideMode, HttpSession session) {
        if (session.getAttribute("adminUser") == null) return "redirect:/login";

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

    @PostMapping("/admin/revert-automatic")
    public String revertToAutomatic(HttpSession session) {
        if (session.getAttribute("adminUser") == null) return "redirect:/login";

        ZoneId phZone = ZoneId.of("Asia/Manila");
        LocalDate today = LocalDate.now(phZone);
        learningStatusLogRepository.deleteByTargetDate(today);
        return "redirect:/";
    }

    // --- USER MANAGEMENT ENDPOINTS ---

    @GetMapping("/admin/users")
    public String viewUserManagement(Model model, HttpSession session) {
        if (session.getAttribute("adminUser") == null) return "redirect:/login";

        model.addAttribute("users", userRepository.findAll());
        model.addAttribute("isAdminLoggedIn", true);
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
                           HttpSession session) {
        if (session.getAttribute("adminUser") == null) return "redirect:/login";

        User user;
        if (id != null) {
            user = userRepository.findById(id).orElse(new User());
            if (password != null && !password.trim().isEmpty()) {
                user.setPasswordHash(password);
            }
        } else {
            user = new User();
            user.setPasswordHash(password);
            user.setStatus("ACTIVE");
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
    public String deleteUser(@PathVariable UUID id, HttpSession session) {
        if (session.getAttribute("adminUser") == null) return "redirect:/login";
        userRepository.deleteById(id);
        return "redirect:/admin/users";
    }

    // Test endpoint: visit http://localhost:8081/test-weather in your browser to fetch & save immediately
    @GetMapping("/test-weather")
    public String testWeatherFetch() {
        weatherDecisionEngine.fetchWeatherAndEvaluateStatus();
        return "redirect:/";
    }

    // Public advance forecast endpoint accessible to all users
    @GetMapping("/forecast")
    public String viewForecast(Model model, HttpSession session) {
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
        
        boolean isAdminLoggedIn = session.getAttribute("adminUser") != null;
        model.addAttribute("isAdminLoggedIn", isAdminLoggedIn);
        
        return "forecast"; // Renders forecast.html
    }

    // Public past weather logs history endpoint
    @GetMapping("/history")
    public String viewWeatherHistory(Model model, HttpSession session) {
        ZoneId phZone = ZoneId.of("Asia/Manila");
        
        List<WeatherLog> logs = weatherLogRepository.findAllByOrderByTimestampDesc()
            .stream()
            .map(log -> {
                if (log.getTimestamp() != null) {
                    OffsetDateTime manilaTime = log.getTimestamp()
                            .atZoneSameInstant(phZone)
                            .toOffsetDateTime();
                    log.setTimestamp(manilaTime);
                }
                return log;
            })
            .collect(Collectors.toList());

        model.addAttribute("weatherLogs", logs);
        
        boolean isAdminLoggedIn = session.getAttribute("adminUser") != null;
        model.addAttribute("isAdminLoggedIn", isAdminLoggedIn);
        
        return "history"; // Renders history.html
    }

    // Official Printable Daily Report for DepEd
    @GetMapping("/report/daily")
    public String viewDailyReport(Model model, HttpSession session) {
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

        // Calculate today's highest rain and peak wind
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

        boolean isAdminLoggedIn = session.getAttribute("adminUser") != null;
        model.addAttribute("isAdminLoggedIn", isAdminLoggedIn);

        return "daily-report";
    }

    // System Diagnostics & Health Status (Admin Only)
    @GetMapping("/admin/diagnostics")
    public String viewDiagnostics(Model model, HttpSession session) {
        if (session.getAttribute("adminUser") == null) return "redirect:/login";

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

        // Test Open-Meteo API Connectivity
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

        boolean isAdminLoggedIn = session.getAttribute("adminUser") != null;
        model.addAttribute("isAdminLoggedIn", isAdminLoggedIn);

        return "diagnostics"; // Renders diagnostics.html
    }

    // Public About endpoint
    @GetMapping("/about")
    public String viewAbout(Model model, HttpSession session) {
        boolean isAdminLoggedIn = session.getAttribute("adminUser") != null;
        model.addAttribute("isAdminLoggedIn", isAdminLoggedIn);
        return "about"; // Renders about.html
    }

    // Export Weather Logs as CSV
    @GetMapping("/history/export")
    public void exportWeatherLogsCsv(HttpServletResponse response, HttpSession session) throws IOException {
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

    // Weather Analytics & Trends Dashboard Endpoint with Monthly Summary Metrics
    @GetMapping("/analytics")
    public String viewAnalytics(Model model, HttpSession session) {
        ZoneId phZone = ZoneId.of("Asia/Manila");
        LocalDate today = LocalDate.now(phZone);
        int currentMonth = today.getMonthValue();
        int currentYear = today.getYear();
        
        List<WeatherLog> logs = weatherLogRepository.findAllByOrderByTimestampDesc();
        
        // Filter and calculate monthly statistics
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

        // Count total suspension days this month
        long totalSuspensionsThisMonth = learningStatusLogRepository.findAll().stream()
                .filter(statusLog -> statusLog.getTargetDate() != null 
                        && statusLog.getTargetDate().getMonthValue() == currentMonth 
                        && statusLog.getTargetDate().getYear() == currentYear 
                        && "SUSPENDED".equals(statusLog.getStatus()))
                .count();

        // Reverse to chronological order for charts (oldest to newest)
        List<WeatherLog> chronologicalLogs = logs.stream()
                .sorted((a, b) -> a.getTimestamp().compareTo(b.getTimestamp()))
                .collect(Collectors.toList());

        List<String> timestamps = chronologicalLogs.stream()
                .map(log -> log.getTimestamp() != null ? log.getTimestamp().atZoneSameInstant(phZone).format(DateTimeFormatter.ofPattern("MMM dd HH:mm")) : "")
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

        // Pass summary attributes to view
        model.addAttribute("currentMonthName", today.format(DateTimeFormatter.ofPattern("MMMM yyyy")));
        model.addAttribute("monthlyRainTotal", String.format("%.1f", monthlyRainTotal));
        model.addAttribute("monthlyAvgTemp", String.format("%.1f", monthlyAvgTemp));
        model.addAttribute("monthlyPeakWind", String.format("%.1f", monthlyPeakWind));
        model.addAttribute("totalSuspensionsThisMonth", totalSuspensionsThisMonth);

        model.addAttribute("timestamps", timestamps);
        model.addAttribute("temperatures", temperatures);
        model.addAttribute("precipitation", precipitation);
        model.addAttribute("windSpeeds", windSpeeds);
        
        boolean isAdminLoggedIn = session.getAttribute("adminUser") != null;
        model.addAttribute("isAdminLoggedIn", isAdminLoggedIn);

        return "analytics"; // Renders analytics.html
    }
}
package edu.ftnhs.weather_manager.controller;

import edu.ftnhs.weather_manager.dto.OpenMeteoResponse;
import edu.ftnhs.weather_manager.entity.LearningStatusLog;
import edu.ftnhs.weather_manager.entity.User;
import edu.ftnhs.weather_manager.entity.WeatherLog;
import edu.ftnhs.weather_manager.repository.LearningStatusLogRepository;
import edu.ftnhs.weather_manager.repository.UserRepository;
import edu.ftnhs.weather_manager.repository.WeatherLogRepository;
import edu.ftnhs.weather_manager.service.WeatherDecisionEngine;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;
import java.util.List;

@Controller
public class DashboardController {

    private final WeatherLogRepository weatherLogRepository;
    private final LearningStatusLogRepository learningStatusLogRepository;
    private final UserRepository userRepository;
    private final WeatherDecisionEngine weatherDecisionEngine; // Added back

    public DashboardController(WeatherLogRepository weatherLogRepository, 
                               LearningStatusLogRepository learningStatusLogRepository,
                               UserRepository userRepository,
                               WeatherDecisionEngine weatherDecisionEngine) { // Added back
        this.weatherLogRepository = weatherLogRepository;
        this.learningStatusLogRepository = learningStatusLogRepository;
        this.userRepository = userRepository;
        this.weatherDecisionEngine = weatherDecisionEngine; // Added back
    }

    @GetMapping("/")
    public String viewDashboard(Model model, HttpSession session) {
        ZoneId phZone = ZoneId.of("Asia/Manila");
        LocalDate today = LocalDate.now(phZone);

        WeatherLog latestLog = weatherLogRepository.findTopByOrderByTimestampDesc();
        Optional<LearningStatusLog> todayOverride = learningStatusLogRepository.findTopByTargetDateOrderByCreatedAtDesc(today);
        
        String currentMode = "IN_PERSON";
        String precipitation = "0.0";
        String temperature = "0.0";
        String windSpeed = "0.0";
        String lastUpdated = "Waiting for first cron job run...";

        if (latestLog != null) {
            precipitation = String.valueOf(latestLog.getPrecipitationMm());
            temperature = String.valueOf(latestLog.getTemperature() != null ? latestLog.getTemperature() : 0.0);
            windSpeed = String.valueOf(latestLog.getWindSpeed() != null ? latestLog.getWindSpeed() : 0.0);
            
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy - hh:mm a");
            lastUpdated = latestLog.getTimestamp().format(formatter);
            currentMode = latestLog.getWeatherCondition();
        }

        boolean hasActiveOverride = todayOverride.isPresent();
        if (hasActiveOverride) {
            currentMode = todayOverride.get().getStatus();
            lastUpdated = "MANUAL OVERRIDE APPLIED"; 
        }

        String bannerClass = switch (currentMode) {
            case "IN_PERSON" -> "bg-success";
            case "MODULAR" -> "bg-warning text-dark";
            case "SUSPENDED" -> "bg-danger";
            default -> "bg-secondary";
        };

        boolean isAdminLoggedIn = session.getAttribute("adminUser") != null;

        model.addAttribute("learningMode", currentMode);
        model.addAttribute("precipitation", precipitation);
        model.addAttribute("temperature", temperature);
        model.addAttribute("windSpeed", windSpeed);
        model.addAttribute("lastUpdated", lastUpdated);
        model.addAttribute("bannerClass", bannerClass);
        model.addAttribute("hasActiveOverride", hasActiveOverride);
        model.addAttribute("isAdminLoggedIn", isAdminLoggedIn);

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
        manualLog.setCreatedAt(LocalDateTime.now(phZone));
        
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
        }
        user.setUsername(username);
        user.setEmail(email);
        user.setName(name);
        user.setRole(role);
        user.setStatus("ACTIVE");

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
        List<WeatherLog> logs = weatherLogRepository.findAllByOrderByTimestampDesc();
        model.addAttribute("weatherLogs", logs);
        
        boolean isAdminLoggedIn = session.getAttribute("adminUser") != null;
        model.addAttribute("isAdminLoggedIn", isAdminLoggedIn);
        
        return "history"; // Renders history.html
    }
}
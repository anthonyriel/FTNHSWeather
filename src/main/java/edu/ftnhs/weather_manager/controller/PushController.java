package edu.ftnhs.weather_manager.controller;

import edu.ftnhs.weather_manager.entity.PushSubscription;
import edu.ftnhs.weather_manager.repository.PushSubscriptionRepository;
import edu.ftnhs.weather_manager.service.PushNotificationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/push")
public class PushController {

    @Value("${vapid.public.key}")
    private String vapidPublicKey;

    private final PushSubscriptionRepository subscriptionRepository;
    private final PushNotificationService pushNotificationService;

    public PushController(PushSubscriptionRepository subscriptionRepository,
                          PushNotificationService pushNotificationService) {
        this.subscriptionRepository = subscriptionRepository;
        this.pushNotificationService = pushNotificationService;
    }

    @GetMapping("/vapid-public-key")
    public ResponseEntity<Map<String, String>> getVapidPublicKey() {
        return ResponseEntity.ok(Map.of("publicKey", vapidPublicKey));
    }

    @PostMapping("/subscribe")
    public ResponseEntity<?> subscribe(@RequestBody Map<String, Object> subscriptionData) {
        try {
            String endpoint = (String) subscriptionData.get("endpoint");
            Object keysObj = subscriptionData.get("keys");
            
            if (endpoint != null && keysObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, String> keys = (Map<String, String>) keysObj;
                
                if (!subscriptionRepository.existsByEndpoint(endpoint)) {
                    PushSubscription sub = new PushSubscription();
                    sub.setEndpoint(endpoint);
                    sub.setP256dh(keys.get("p256dh"));
                    sub.setAuth(keys.get("auth"));
                    subscriptionRepository.save(sub);
                }
            }
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // NEW ENDPOINT: Unsubscribe and delete from database
    @PostMapping("/unsubscribe")
    public ResponseEntity<?> unsubscribe(@RequestBody Map<String, String> payload) {
        try {
            String endpoint = payload.get("endpoint");
            if (endpoint != null) {
                subscriptionRepository.deleteByEndpoint(endpoint);
            }
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/broadcast")
    public ResponseEntity<?> broadcastNotification(@RequestBody Map<String, String> payloadMap) {
        try {
            String title = payloadMap.getOrDefault("title", "FTNHS Weather Hub Alert");
            String body = payloadMap.getOrDefault("body", "System status update triggered.");
            String url = payloadMap.getOrDefault("url", "/");

            String jsonPayload = String.format("{\"title\":\"%s\",\"body\":\"%s\",\"url\":\"%s\"}", title, body, url);
            
            pushNotificationService.sendNotificationToAll(jsonPayload);
            return ResponseEntity.ok(Map.of("success", true, "message", "Broadcast sent successfully!"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
package edu.ftnhs.weather_manager.controller;

import edu.ftnhs.weather_manager.entity.PushSubscription;
import edu.ftnhs.weather_manager.repository.PushSubscriptionRepository;
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

    public PushController(PushSubscriptionRepository subscriptionRepository) {
        this.subscriptionRepository = subscriptionRepository;
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
}
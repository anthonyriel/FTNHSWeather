package edu.ftnhs.weather_manager.service;

import edu.ftnhs.weather_manager.entity.NotificationLog;
import edu.ftnhs.weather_manager.entity.PushSubscription;
import edu.ftnhs.weather_manager.repository.NotificationLogRepository;
import edu.ftnhs.weather_manager.repository.PushSubscriptionRepository;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import jakarta.annotation.PostConstruct;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPrivateKeySpec;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.logging.Logger;

@Service
public class PushNotificationService {

    private static final Logger LOGGER = Logger.getLogger(PushNotificationService.class.getName());

    private final PushSubscriptionRepository subscriptionRepository;
    private final NotificationLogRepository notificationLogRepository;

    @Value("${vapid.public.key}")
    private String vapidPublicKey;

    @Value("${vapid.private.key}")
    private String vapidPrivateKey;

    @Value("${vapid.subject}")
    private String vapidSubject;

    private final RestClient restClient = RestClient.create();

    public PushNotificationService(PushSubscriptionRepository subscriptionRepository,
                                   NotificationLogRepository notificationLogRepository) {
        this.subscriptionRepository = subscriptionRepository;
        this.notificationLogRepository = notificationLogRepository;
    }

    @PostConstruct
    public void init() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        LOGGER.info("PushNotificationService initialized with direct VAPID dispatch and history logging.");
    }

    public void sendNotificationToAll(String payload) {
        // Parse clean title and body from JSON payload if possible
        String displayTitle = "FTNHS Weather Advisory";
        String displayBody = payload;

        try {
            java.util.regex.Matcher titleMatcher = java.util.regex.Pattern.compile("\"title\"\\s*:\\s*\"([^\"]+)\"").matcher(payload);
            if (titleMatcher.find()) {
                displayTitle = titleMatcher.group(1);
            }
            java.util.regex.Matcher bodyMatcher = java.util.regex.Pattern.compile("\"body\"\\s*:\\s*\"([^\"]+)\"").matcher(payload);
            if (bodyMatcher.find()) {
                displayBody = bodyMatcher.group(1);
            }
        } catch (Exception ignored) {}

        // 1. Save cleaned log to Notification History Log
        try {
            NotificationLog logEntry = new NotificationLog();
            logEntry.setTitle(displayTitle);
            logEntry.setBody(displayBody);
            logEntry.setSentAt(OffsetDateTime.now(ZoneId.of("Asia/Manila")));
            notificationLogRepository.save(logEntry);
            LOGGER.info("Notification saved to history log successfully.");
        } catch (Exception e) {
            LOGGER.warning("Failed to save notification to history log: " + e.getMessage());
        }

        // 2. Broadcast push to all active subscriptions
        List<PushSubscription> subscriptions = subscriptionRepository.findAll();
        LOGGER.info("Found " + subscriptions.size() + " push subscription(s) to notify.");

        for (PushSubscription sub : subscriptions) {
            try {
                sendWebPush(sub.getEndpoint(), payload);
                LOGGER.info("Successfully pushed notification to: " + sub.getEndpoint());
            } catch (Exception e) {
                LOGGER.warning("Failed to send push notification: " + e.getMessage());
                e.printStackTrace();
                if (e.getMessage() != null && (e.getMessage().contains("410") || e.getMessage().contains("404") || e.getMessage().contains("Unauthorized"))) {
                    subscriptionRepository.delete(sub);
                }
            }
        }
    }

    private void sendWebPush(String endpoint, String payload) throws Exception {
        String audience = extractOrigin(endpoint);
        byte[] privBytes = Base64.getUrlDecoder().decode(vapidPrivateKey.replace("=", "").trim());
        String jwt = generateVapidJwt(audience, vapidSubject != null && !vapidSubject.trim().isEmpty() ? vapidSubject : "mailto:admin@ftnhs.edu.ph", privBytes);
        
        String authHeaderValue = "vapid t=" + jwt + "; k=" + vapidPublicKey.replace("=", "").trim();

        ResponseEntity<String> response = restClient.post()
                .uri(endpoint)
                .header(HttpHeaders.AUTHORIZATION, authHeaderValue)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .header("TTL", "86400")
                .body(payload)
                .retrieve()
                .toEntity(String.class);

        LOGGER.info("FCM Response Status: " + response.getStatusCode());
    }

    private String extractOrigin(String endpoint) {
        try {
            java.net.URI uri = new java.net.URI(endpoint);
            return uri.getScheme() + "://" + uri.getAuthority();
        } catch (Exception e) {
            return "https://fcm.googleapis.com";
        }
    }

    private String generateVapidJwt(String audience, String subject, byte[] privateKeyBytes) throws Exception {
        String header = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"typ\":\"JWT\",\"alg\":\"ES256\"}".getBytes(StandardCharsets.UTF_8));
        
        long exp = (System.currentTimeMillis() / 1000) + 86400; // 24 hours expiry
        String payloadJson = String.format("{\"aud\":\"%s\",\"sub\":\"%s\",\"exp\":%d}", audience, subject, exp);
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));

        String unsignedJwt = header + "." + payload;

        Signature ecdsa = Signature.getInstance("SHA256withECDSA", BouncyCastleProvider.PROVIDER_NAME);
        ecdsa.initSign(getPrivateKeyFromBytes(privateKeyBytes));
        ecdsa.update(unsignedJwt.getBytes(StandardCharsets.UTF_8));
        byte[] derSignature = ecdsa.sign();

        byte[] rawSignature = derToConcatenated(derSignature, 32);

        return unsignedJwt + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(rawSignature);
    }

    private PrivateKey getPrivateKeyFromBytes(byte[] privBytes) throws Exception {
        AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC", BouncyCastleProvider.PROVIDER_NAME);
        parameters.init(new ECGenParameterSpec("secp256r1"));
        ECParameterSpec ecParameters = parameters.getParameterSpec(ECParameterSpec.class);
        
        ECPrivateKeySpec privSpec = new ECPrivateKeySpec(new BigInteger(1, privBytes), ecParameters);
        KeyFactory keyFactory = KeyFactory.getInstance("EC", BouncyCastleProvider.PROVIDER_NAME);
        return keyFactory.generatePrivate(privSpec);
    }

    private byte[] derToConcatenated(byte[] derSignature, int coordinateSize) throws Exception {
        if (derSignature[0] != 0x30) {
            throw new IllegalArgumentException("Invalid DER signature format");
        }
        int offset = 2;
        if ((derSignature[1] & 0x80) != 0) {
            offset += (derSignature[1] & 0x7f);
        }
        if (derSignature[offset++] != 0x02) {
            throw new IllegalArgumentException("Invalid DER r marker");
        }
        int rLen = derSignature[offset++];
        int rPos = offset;
        offset += rLen;

        if (derSignature[offset++] != 0x02) {
            throw new IllegalArgumentException("Invalid DER s marker");
        }
        int sLen = derSignature[offset++];
        int sPos = offset;

        byte[] rawSignature = new byte[coordinateSize * 2];

        int rSrcLen = rLen;
        int rSrcPos = rPos;
        if (derSignature[rPos] == 0x00) {
            rSrcLen--;
            rSrcPos++;
        }
        System.arraycopy(derSignature, rSrcPos, rawSignature, coordinateSize - rSrcLen, rSrcLen);

        int sSrcLen = sLen;
        int sSrcPos = sPos;
        if (derSignature[sPos] == 0x00) {
            sSrcLen--;
            sSrcPos++;
        }
        System.arraycopy(derSignature, sSrcPos, rawSignature, coordinateSize * 2 - sSrcLen, sSrcLen);

        return rawSignature;
    }
}
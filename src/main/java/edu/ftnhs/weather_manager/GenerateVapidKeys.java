package edu.ftnhs.weather_manager;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.interfaces.ECPrivateKey;
import org.bouncycastle.jce.interfaces.ECPublicKey;
import org.bouncycastle.util.BigIntegers;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;

public class GenerateVapidKeys {
    public static void main(String[] args) {
        try {
            if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                Security.addProvider(new BouncyCastleProvider());
            }

            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EC", BouncyCastleProvider.PROVIDER_NAME);
            keyPairGenerator.initialize(new ECGenParameterSpec("secp256r1"));
            KeyPair keyPair = keyPairGenerator.generateKeyPair();

            ECPublicKey pubKey = (ECPublicKey) keyPair.getPublic();
            ECPrivateKey privKey = (ECPrivateKey) keyPair.getPrivate();

            // Public key uncompressed point (65 bytes) with URL encoding and padding
            byte[] pubBytes = pubKey.getQ().getEncoded(false);
            String publicKey = Base64.getUrlEncoder().encodeToString(pubBytes);

            // Private key raw scalar bytes with URL encoding and padding
            byte[] privBytes = BigIntegers.asUnsignedByteArray(privKey.getD());
            String privateKey = Base64.getUrlEncoder().encodeToString(privBytes);

            System.out.println("==========================================");
            System.out.println("COPY THESE VAPID KEYS INTO application.properties:");
            System.out.println("==========================================");
            System.out.println("vapid.public.key=" + publicKey);
            System.out.println("vapid.private.key=" + privateKey);
            System.out.println("==========================================");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
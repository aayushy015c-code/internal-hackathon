package com.hackathon.distress.service;

import org.springframework.stereotype.Service;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

// We never store the duress PIN itself, only a salted hash of it (PBKDF2).
// Stored format: "salt:hash" (both Base64).
@Service
public class PinService {

    private static final int ITERATIONS = 100_000;
    private final SecureRandom random = new SecureRandom();

    public String hash(String pin) {
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        return encode(salt) + ":" + encode(pbkdf2(pin, salt));
    }

    public boolean matches(String pin, String stored) {
        if (pin == null || stored == null || !stored.contains(":")) return false;
        String[] parts = stored.split(":");
        byte[] salt = Base64.getDecoder().decode(parts[0]);
        byte[] expected = Base64.getDecoder().decode(parts[1]);
        // constant-time compare, so timing doesn't leak how many bytes matched
        return MessageDigest.isEqual(expected, pbkdf2(pin, salt));
    }

    private byte[] pbkdf2(String pin, byte[] salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, 256);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("PBKDF2 not available", e);
        }
    }

    private String encode(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }
}

package com.sebu.backend.auth.token;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

@Component
public class RefreshTokenGenerator {
    private static final int TOKEN_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenMaterial generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return new RefreshTokenMaterial(rawToken, hash(rawToken));
    }

    public String hash(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException("REFRESH_TOKEN_REQUIRED");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA_256_NOT_AVAILABLE", exception);
        }
    }

    public static final class RefreshTokenMaterial {
        private final String rawToken;
        private final String tokenHash;

        private RefreshTokenMaterial(String rawToken, String tokenHash) {
            this.rawToken = rawToken;
            this.tokenHash = tokenHash;
        }

        public String rawToken() {
            return rawToken;
        }

        public String tokenHash() {
            return tokenHash;
        }

        @Override
        public String toString() {
            return "RefreshTokenMaterial[REDACTED]";
        }
    }
}

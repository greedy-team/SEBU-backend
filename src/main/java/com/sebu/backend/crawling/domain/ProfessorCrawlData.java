package com.sebu.backend.crawling.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

public record ProfessorCrawlData(
    String professorName,
    String position,
    String email,
    String laboratoryName,
    String researchIntroduction,
    String homepageUrl
) {
    public ProfessorCrawlData {
        professorName = requireText(professorName, "PROFESSOR_NAME_REQUIRED");
        position = normalizeNullable(position);
        email = normalizeEmail(email);
        laboratoryName = normalizeNullable(laboratoryName);
        researchIntroduction = normalizeNullable(researchIntroduction);
        homepageUrl = normalizeNullable(homepageUrl);
    }

    public String identityKey() {
        if (email != null) {
            return "email:" + email;
        }
        if (homepageUrl != null) {
            return "homepage:" + sha256(homepageUrl);
        }
        return "name:" + sha256(professorName);
    }

    public boolean hasStableIdentity() {
        return email != null || homepageUrl != null;
    }

    private static String requireText(String value, String errorCode) {
        String normalized = normalizeNullable(value);
        if (normalized == null) {
            throw new IllegalArgumentException(errorCode);
        }
        return normalized;
    }

    private static String normalizeEmail(String value) {
        String normalized = normalizeNullable(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private static String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA_256_NOT_AVAILABLE", exception);
        }
    }
}

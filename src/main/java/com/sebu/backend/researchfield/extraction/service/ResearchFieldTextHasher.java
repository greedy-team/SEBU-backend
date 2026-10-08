package com.sebu.backend.researchfield.extraction.service;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.Locale;

@Component
public class ResearchFieldTextHasher {
    public String hashFieldIdentity(String text) {
        return sha256(canonicalizeFieldIdentity(text));
    }

    public String hashSourceDescription(String text) {
        return sha256(canonicalizeSourceDescription(text));
    }

    private String sha256(String canonicalText) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                digest.digest(canonicalText.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA_256_NOT_AVAILABLE", exception);
        }
    }

    private String canonicalizeFieldIdentity(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return Normalizer.normalize(text, Normalizer.Form.NFKC)
            .trim()
            .replaceAll("\\s+", " ")
            .toLowerCase(Locale.ROOT);
    }

    private String canonicalizeSourceDescription(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return Normalizer.normalize(text, Normalizer.Form.NFKC)
            .trim()
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .replaceAll("[\\t\\x0B\\f ]+", " ")
            .replaceAll(" *\\n *", "\n");
    }
}

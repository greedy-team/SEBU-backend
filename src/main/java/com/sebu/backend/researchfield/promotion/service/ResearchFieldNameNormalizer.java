package com.sebu.backend.researchfield.promotion.service;

import com.sebu.backend.researchfield.promotion.exception.ResearchFieldPromotionException;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

@Component
public class ResearchFieldNameNormalizer {
    private static final int MAX_LENGTH = 100;
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    public String normalize(String value) {
        if (value == null || value.isBlank()) {
            throw new ResearchFieldPromotionException(
                "RESEARCH_FIELD_NAME_REQUIRED"
            );
        }
        String normalized = Normalizer.normalize(
            value,
            Normalizer.Form.NFKC
        ).trim();
        normalized = WHITESPACE.matcher(normalized).replaceAll(" ");
        if (normalized.isEmpty()) {
            throw new ResearchFieldPromotionException(
                "RESEARCH_FIELD_NAME_REQUIRED"
            );
        }
        if (normalized.length() > MAX_LENGTH) {
            throw new ResearchFieldPromotionException(
                "RESEARCH_FIELD_NAME_TOO_LONG"
            );
        }
        return normalized;
    }

    public boolean equivalent(String first, String second) {
        return comparisonKey(first).equals(comparisonKey(second));
    }

    private String comparisonKey(String value) {
        return normalize(value).toLowerCase(Locale.ROOT);
    }
}

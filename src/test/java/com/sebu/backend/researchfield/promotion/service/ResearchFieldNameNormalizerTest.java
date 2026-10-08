package com.sebu.backend.researchfield.promotion.service;

import com.sebu.backend.researchfield.promotion.exception.ResearchFieldPromotionException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResearchFieldNameNormalizerTest {
    private final ResearchFieldNameNormalizer normalizer =
        new ResearchFieldNameNormalizer();

    @Test
    void appliesUnicodeCompatibilityNormalizationAndCollapsesWhitespace() {
        String normalized = normalizer.normalize(
            "  ＡＩ\t  Visual\nComputing  "
        );

        assertThat(normalized).isEqualTo("AI Visual Computing");
    }

    @Test
    void comparesNamesWithoutCaseOrWhitespaceDifferences() {
        assertThat(normalizer.equivalent("AI  Vision", " ai vision "))
            .isTrue();
    }

    @Test
    void rejectsMissingNames() {
        assertThatThrownBy(() -> normalizer.normalize(" \t\n "))
            .isInstanceOf(ResearchFieldPromotionException.class)
            .hasMessage("RESEARCH_FIELD_NAME_REQUIRED");
    }

    @Test
    void rejectsNamesLongerThanTheDatabaseColumn() {
        assertThatThrownBy(() -> normalizer.normalize("가".repeat(101)))
            .isInstanceOf(ResearchFieldPromotionException.class)
            .hasMessage("RESEARCH_FIELD_NAME_TOO_LONG");
    }
}

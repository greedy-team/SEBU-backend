package com.sebu.backend.researchfield.extraction.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResearchFieldTextHasherTest {
    private final ResearchFieldTextHasher hasher = new ResearchFieldTextHasher();

    @Test
    void fieldIdentityNormalizesWhitespaceButSourceHashPreservesLineSeparators() {
        assertThat(hasher.hashFieldIdentity("AI\nML"))
            .isEqualTo(hasher.hashFieldIdentity("AI ML"));
        assertThat(hasher.hashSourceDescription("AI\nML"))
            .isNotEqualTo(hasher.hashSourceDescription("AI ML"));
    }

    @Test
    void sourceHashNormalizesLineEndingsAcrossOperatingSystems() {
        assertThat(hasher.hashSourceDescription("AI\r\nML"))
            .isEqualTo(hasher.hashSourceDescription("AI\nML"));
    }
}

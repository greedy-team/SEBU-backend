package com.sebu.backend.researchfield.promotion.dto;

import java.util.List;

public record ResearchFieldPromotionResult(
    int candidateCount,
    int createdFieldCount,
    int createdLinkCount,
    int promotedCount,
    int skippedCount,
    List<Failure> failures
) {
    public ResearchFieldPromotionResult {
        failures = List.copyOf(failures);
    }

    public int failedCount() {
        return failures.size();
    }

    public boolean hasFailures() {
        return !failures.isEmpty();
    }

    public record Failure(
        Long candidateId,
        String reason,
        RuntimeException exception
    ) {
    }
}

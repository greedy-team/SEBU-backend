package com.sebu.backend.promotion.dto;

import java.util.List;

public record PromotionResult(
    int candidateCount,
    int createdCount,
    int updatedCount,
    int skippedCount,
    List<Failure> failures
) {
    public PromotionResult {
        failures = List.copyOf(failures);
    }

    public int failedCount() {
        return failures.size();
    }

    public boolean hasFailures() {
        return !failures.isEmpty();
    }

    public record Failure(Long candidateId, String reason, RuntimeException exception) {
    }
}

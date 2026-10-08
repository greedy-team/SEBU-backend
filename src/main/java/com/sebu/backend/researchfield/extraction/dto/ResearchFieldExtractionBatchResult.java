package com.sebu.backend.researchfield.extraction.dto;

import java.util.List;

public record ResearchFieldExtractionBatchResult(
    int laboratoryCount,
    List<ResearchFieldExtractionResult> successes,
    List<Failure> failures
) {
    public ResearchFieldExtractionBatchResult {
        successes = List.copyOf(successes);
        failures = List.copyOf(failures);
    }

    public int totalCreatedCount() {
        return successes.stream().mapToInt(ResearchFieldExtractionResult::createdCount).sum();
    }

    public int totalRefreshedCount() {
        return successes.stream().mapToInt(ResearchFieldExtractionResult::refreshedCount).sum();
    }

    public int totalStaleCount() {
        return successes.stream().mapToInt(ResearchFieldExtractionResult::staleCount).sum();
    }

    public boolean hasFailures() {
        return !failures.isEmpty();
    }

    public record Failure(long laboratoryId, String reason, RuntimeException exception) {
    }
}

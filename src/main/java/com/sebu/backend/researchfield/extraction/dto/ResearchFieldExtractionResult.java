package com.sebu.backend.researchfield.extraction.dto;

public record ResearchFieldExtractionResult(
    long laboratoryId,
    int extractedCount,
    int createdCount,
    int refreshedCount,
    int staleCount,
    int unchangedCount
) {
}

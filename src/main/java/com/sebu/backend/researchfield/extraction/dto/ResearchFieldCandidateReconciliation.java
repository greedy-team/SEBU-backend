package com.sebu.backend.researchfield.extraction.dto;

import com.sebu.backend.researchfield.candidate.domain.LaboratoryResearchFieldCandidate;

import java.util.List;

public record ResearchFieldCandidateReconciliation(
    List<LaboratoryResearchFieldCandidate> createdCandidates,
    int extractedCount,
    int refreshedCount,
    int staleCount,
    int unchangedCount
) {
    public ResearchFieldCandidateReconciliation {
        createdCandidates = List.copyOf(createdCandidates);
    }

    public int createdCount() {
        return createdCandidates.size();
    }
}

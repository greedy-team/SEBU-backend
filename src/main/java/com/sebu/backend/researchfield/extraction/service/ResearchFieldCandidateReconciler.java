package com.sebu.backend.researchfield.extraction.service;

import com.sebu.backend.laboratory.domain.Laboratory;
import com.sebu.backend.researchfield.candidate.domain.LaboratoryResearchFieldCandidate;
import com.sebu.backend.researchfield.candidate.domain.ResearchFieldCandidateDraft;
import com.sebu.backend.researchfield.extraction.dto.ResearchFieldCandidateReconciliation;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ResearchFieldCandidateReconciler {
    public ResearchFieldCandidateReconciliation reconcile(
        Laboratory laboratory,
        List<ResearchFieldCandidateDraft> extractedFields,
        List<LaboratoryResearchFieldCandidate> existingCandidates,
        String sourceDescriptionHash,
        String extractionRuleVersion,
        LocalDateTime extractedAt
    ) {
        List<LaboratoryResearchFieldCandidate> manualSplitCandidates =
            existingCandidates.stream()
                .filter(LaboratoryResearchFieldCandidate::isManualSplit)
                .toList();
        Map<String, LaboratoryResearchFieldCandidate> unmatchedCandidates =
            existingCandidates.stream()
                .filter(candidate -> !candidate.isManualSplit())
                .collect(Collectors.toMap(
                LaboratoryResearchFieldCandidate::getSourceFieldKey,
                Function.identity(),
                (first, duplicate) -> {
                    throw new IllegalStateException("DUPLICATE_RESEARCH_FIELD_CANDIDATE_KEY");
                },
                LinkedHashMap::new
            ));
        List<LaboratoryResearchFieldCandidate> createdCandidates = new ArrayList<>();
        int refreshedCount = 0;
        int unchangedCount = 0;

        for (ResearchFieldCandidateDraft extractedField : extractedFields) {
            LaboratoryResearchFieldCandidate existing = unmatchedCandidates.remove(
                extractedField.sourceFieldKey()
            );
            if (existing == null) {
                createdCandidates.add(new LaboratoryResearchFieldCandidate(
                    laboratory,
                    extractedField,
                    sourceDescriptionHash,
                    extractionRuleVersion,
                    extractedAt
                ));
                continue;
            }
            if (existing.refreshFromExtraction(
                extractedField,
                sourceDescriptionHash,
                extractionRuleVersion,
                extractedAt
            )) {
                refreshedCount++;
            } else {
                unchangedCount++;
            }
        }

        int staleCount = 0;
        for (LaboratoryResearchFieldCandidate unmatched : unmatchedCandidates.values()) {
            if (unmatched.markStale()) {
                staleCount++;
            }
        }
        for (LaboratoryResearchFieldCandidate manualSplit : manualSplitCandidates) {
            if (manualSplit.shouldBecomeStaleFromSplitSource()
                && manualSplit.markStale()) {
                staleCount++;
            }
        }
        return new ResearchFieldCandidateReconciliation(
            createdCandidates,
            extractedFields.size(),
            refreshedCount,
            staleCount,
            unchangedCount
        );
    }
}

package com.sebu.backend.researchfield.promotion.service;

import com.sebu.backend.laboratory.repository.LaboratoryRepository;
import com.sebu.backend.researchfield.candidate.repository.LaboratoryResearchFieldCandidateRepository;
import com.sebu.backend.researchfield.promotion.dto.ResearchFieldPromotionResult;
import com.sebu.backend.researchfield.promotion.exception.ResearchFieldPromotionException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ResearchFieldCandidatePromotionService {
    private final LaboratoryResearchFieldCandidateRepository candidateRepository;
    private final LaboratoryRepository laboratoryRepository;
    private final ResearchFieldCandidatePromotionTransactionService transactionService;

    public ResearchFieldPromotionResult promote(Long laboratoryId) {
        validateLaboratory(laboratoryId);
        List<Long> candidateIds = findCandidateIds(laboratoryId);
        int createdFieldCount = 0;
        int createdLinkCount = 0;
        int promotedCount = 0;
        int skippedCount = 0;
        List<ResearchFieldPromotionResult.Failure> failures = new ArrayList<>();

        for (Long candidateId : candidateIds) {
            try {
                ResearchFieldPromotionOutcome outcome = executePromotion(
                    candidateId
                );
                createdFieldCount += outcome.fieldCreated() ? 1 : 0;
                createdLinkCount += outcome.linkCreated() ? 1 : 0;
                promotedCount += outcome.promotionRecorded() ? 1 : 0;
                skippedCount += outcome.skipped() ? 1 : 0;
            } catch (RuntimeException exception) {
                failures.add(new ResearchFieldPromotionResult.Failure(
                    candidateId,
                    failureReason(exception),
                    exception
                ));
            }
        }

        return new ResearchFieldPromotionResult(
            candidateIds.size(),
            createdFieldCount,
            createdLinkCount,
            promotedCount,
            skippedCount,
            failures
        );
    }

    private ResearchFieldPromotionOutcome executePromotion(Long candidateId) {
        try {
            return transactionService.promote(candidateId);
        } catch (DataIntegrityViolationException firstConflict) {
            return transactionService.promote(candidateId);
        }
    }

    private List<Long> findCandidateIds(Long laboratoryId) {
        if (laboratoryId == null) {
            return candidateRepository.findCurrentApprovedCandidateIds();
        }
        return candidateRepository.findCurrentApprovedCandidateIdsByLaboratoryId(
            laboratoryId
        );
    }

    private void validateLaboratory(Long laboratoryId) {
        if (laboratoryId != null
            && laboratoryRepository.findByIdAndDeletedAtIsNull(laboratoryId).isEmpty()) {
            throw new ResearchFieldPromotionException(
                "PROMOTION_LABORATORY_NOT_FOUND: " + laboratoryId
            );
        }
    }

    private String failureReason(RuntimeException exception) {
        if (exception instanceof DataIntegrityViolationException) {
            return "RESEARCH_FIELD_PROMOTION_DATA_CONFLICT";
        }
        if (exception.getMessage() == null || exception.getMessage().isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return exception.getMessage();
    }
}

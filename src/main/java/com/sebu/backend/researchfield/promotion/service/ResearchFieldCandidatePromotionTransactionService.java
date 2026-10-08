package com.sebu.backend.researchfield.promotion.service;

import com.sebu.backend.laboratory.domain.Laboratory;
import com.sebu.backend.laboratory.repository.LaboratoryRepository;
import com.sebu.backend.researchfield.candidate.domain.LaboratoryResearchFieldCandidate;
import com.sebu.backend.researchfield.candidate.repository.LaboratoryResearchFieldCandidateRepository;
import com.sebu.backend.researchfield.domain.ResearchField;
import com.sebu.backend.researchfield.promotion.exception.ResearchFieldPromotionException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ResearchFieldCandidatePromotionTransactionService {
    private final LaboratoryResearchFieldCandidateRepository candidateRepository;
    private final LaboratoryRepository laboratoryRepository;
    private final ResearchFieldPromotionTargetResolver targetResolver;
    private final LaboratoryResearchFieldLinkService linkService;
    private final ResearchFieldNameNormalizer nameNormalizer;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ResearchFieldPromotionOutcome promote(Long candidateId) {
        LaboratoryResearchFieldCandidate candidate = candidateRepository
            .findByIdForPromotion(candidateId)
            .orElse(null);
        if (candidate == null || !candidate.isCurrentAndApproved()) {
            return ResearchFieldPromotionOutcome.skippedOutcome();
        }
        if (!candidate.hasConsistentPromotionState()) {
            throw new ResearchFieldPromotionException(
                "INVALID_RESEARCH_FIELD_PROMOTION_STATE"
            );
        }

        Laboratory laboratory = lockActiveLaboratory(candidate);
        ResearchFieldPromotionTarget target = resolveTarget(candidate);
        boolean linkCreated = linkService.ensure(
            laboratory,
            target.researchField()
        );
        boolean promotionRecorded = recordPromotionIfNeeded(
            candidate,
            target.researchField()
        );
        return ResearchFieldPromotionOutcome.completed(
            target.created(),
            linkCreated,
            promotionRecorded
        );
    }

    private Laboratory lockActiveLaboratory(
        LaboratoryResearchFieldCandidate candidate
    ) {
        Long laboratoryId = candidate.getLaboratory().getId();
        Laboratory laboratory = laboratoryRepository.findByIdForUpdate(
            laboratoryId
        ).orElseThrow(() -> new ResearchFieldPromotionException(
            "PROMOTION_LABORATORY_NOT_FOUND"
        ));
        if (laboratory.isDeleted()) {
            throw new ResearchFieldPromotionException(
                "DELETED_LABORATORY_CANNOT_PROMOTE_RESEARCH_FIELD"
            );
        }
        return laboratory;
    }

    private ResearchFieldPromotionTarget resolveTarget(
        LaboratoryResearchFieldCandidate candidate
    ) {
        if (!candidate.hasBeenPromoted()) {
            if (!candidate.needsPromotion()) {
                throw new ResearchFieldPromotionException(
                    "CANDIDATE_NOT_READY_FOR_RESEARCH_FIELD_PROMOTION"
                );
            }
            return targetResolver.resolve(candidate.getCandidateName());
        }
        ResearchField promoted = candidate.getPromotedResearchField();
        if (promoted == null) {
            throw new ResearchFieldPromotionException(
                "INVALID_RESEARCH_FIELD_PROMOTION_STATE"
            );
        }
        if (!nameNormalizer.equivalent(
            candidate.getCandidateName(),
            promoted.getName()
        )) {
            throw new ResearchFieldPromotionException(
                "PROMOTED_ENTITY_CANNOT_BE_REPLACED"
            );
        }
        return new ResearchFieldPromotionTarget(promoted, false);
    }

    private boolean recordPromotionIfNeeded(
        LaboratoryResearchFieldCandidate candidate,
        ResearchField researchField
    ) {
        if (!candidate.needsPromotion()) {
            return false;
        }
        candidate.recordPromotion(researchField, LocalDateTime.now());
        candidateRepository.save(candidate);
        return true;
    }
}

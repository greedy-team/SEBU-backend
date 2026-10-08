package com.sebu.backend.researchfield.promotion.service;

import com.sebu.backend.laboratory.domain.Laboratory;
import com.sebu.backend.laboratory.repository.LaboratoryRepository;
import com.sebu.backend.researchfield.candidate.domain.LaboratoryResearchFieldCandidate;
import com.sebu.backend.researchfield.candidate.repository.LaboratoryResearchFieldCandidateRepository;
import com.sebu.backend.researchfield.domain.ResearchField;
import com.sebu.backend.researchfield.promotion.exception.ResearchFieldPromotionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResearchFieldCandidatePromotionTransactionServiceTest {
    private static final Long CANDIDATE_ID = 43L;
    private static final Long LABORATORY_ID = 17L;
    private static final String FIELD_NAME = "자율주행";

    @Mock
    LaboratoryResearchFieldCandidateRepository candidateRepository;

    @Mock
    LaboratoryRepository laboratoryRepository;

    @Mock
    ResearchFieldPromotionTargetResolver targetResolver;

    @Mock
    LaboratoryResearchFieldLinkService linkService;

    @Mock
    ResearchFieldNameNormalizer nameNormalizer;

    @InjectMocks
    ResearchFieldCandidatePromotionTransactionService service;

    @Test
    void skipsWhenPromotionCandidateDoesNotExist() {
        when(candidateRepository.findByIdForPromotion(CANDIDATE_ID))
            .thenReturn(Optional.empty());

        ResearchFieldPromotionOutcome outcome = service.promote(CANDIDATE_ID);

        assertSkipped(outcome);
        verifyNoInteractions(
            laboratoryRepository,
            targetResolver,
            linkService,
            nameNormalizer
        );
    }

    @Test
    void skipsWhenCandidateIsNotCurrentAndApproved() {
        LaboratoryResearchFieldCandidate candidate = candidate();
        when(candidateRepository.findByIdForPromotion(CANDIDATE_ID))
            .thenReturn(Optional.of(candidate));
        when(candidate.isCurrentAndApproved()).thenReturn(false);

        ResearchFieldPromotionOutcome outcome = service.promote(CANDIDATE_ID);

        assertSkipped(outcome);
        verifyNoInteractions(
            laboratoryRepository,
            targetResolver,
            linkService,
            nameNormalizer
        );
    }

    @Test
    void rejectsCandidateWithInconsistentPromotionState() {
        LaboratoryResearchFieldCandidate candidate = currentCandidate();
        when(candidate.hasConsistentPromotionState()).thenReturn(false);

        assertThatThrownBy(() -> service.promote(CANDIDATE_ID))
            .isInstanceOf(ResearchFieldPromotionException.class)
            .hasMessage("INVALID_RESEARCH_FIELD_PROMOTION_STATE");

        verifyNoInteractions(
            laboratoryRepository,
            targetResolver,
            linkService,
            nameNormalizer
        );
    }

    @Test
    void promotesNewCandidateAfterLockingItsLaboratory() {
        LaboratoryResearchFieldCandidate candidate = currentCandidate();
        Laboratory laboratoryReference = laboratoryReference(candidate);
        Laboratory lockedLaboratory = activeLockedLaboratory();
        ResearchField researchField = new ResearchField(FIELD_NAME);
        ResearchFieldPromotionTarget target = new ResearchFieldPromotionTarget(
            researchField,
            true
        );
        when(candidate.hasBeenPromoted()).thenReturn(false);
        when(candidate.needsPromotion()).thenReturn(true);
        when(candidate.getCandidateName()).thenReturn(FIELD_NAME);
        when(targetResolver.resolve(FIELD_NAME)).thenReturn(target);
        when(linkService.ensure(lockedLaboratory, researchField)).thenReturn(true);

        ResearchFieldPromotionOutcome outcome = service.promote(CANDIDATE_ID);

        assertThat(outcome.fieldCreated()).isTrue();
        assertThat(outcome.linkCreated()).isTrue();
        assertThat(outcome.promotionRecorded()).isTrue();
        assertThat(outcome.skipped()).isFalse();
        InOrder order = inOrder(
            candidateRepository,
            laboratoryRepository,
            targetResolver,
            linkService,
            candidate
        );
        order.verify(candidateRepository).findByIdForPromotion(CANDIDATE_ID);
        order.verify(laboratoryRepository).findByIdForUpdate(LABORATORY_ID);
        order.verify(targetResolver).resolve(FIELD_NAME);
        order.verify(linkService).ensure(lockedLaboratory, researchField);
        order.verify(candidate).recordPromotion(
            eq(researchField),
            any(LocalDateTime.class)
        );
        order.verify(candidateRepository).save(candidate);
        assertThat(laboratoryReference.getId()).isEqualTo(LABORATORY_ID);
    }

    @Test
    void rejectsPromotionForDeletedLaboratory() {
        LaboratoryResearchFieldCandidate candidate = currentCandidate();
        laboratoryReference(candidate);
        Laboratory deletedLaboratory = laboratory();
        when(deletedLaboratory.isDeleted()).thenReturn(true);
        when(laboratoryRepository.findByIdForUpdate(LABORATORY_ID))
            .thenReturn(Optional.of(deletedLaboratory));

        assertThatThrownBy(() -> service.promote(CANDIDATE_ID))
            .isInstanceOf(ResearchFieldPromotionException.class)
            .hasMessage("DELETED_LABORATORY_CANNOT_PROMOTE_RESEARCH_FIELD");

        verifyNoInteractions(targetResolver, linkService, nameNormalizer);
        verify(candidateRepository, never()).save(candidate);
    }

    @Test
    void rejectsReplacingPreviouslyPromotedResearchField() {
        LaboratoryResearchFieldCandidate candidate = currentCandidate();
        laboratoryReference(candidate);
        activeLockedLaboratory();
        ResearchField promotedResearchField = new ResearchField("컴퓨터 비전");
        when(candidate.hasBeenPromoted()).thenReturn(true);
        when(candidate.getPromotedResearchField()).thenReturn(promotedResearchField);
        when(candidate.getCandidateName()).thenReturn(FIELD_NAME);
        when(nameNormalizer.equivalent(FIELD_NAME, "컴퓨터 비전"))
            .thenReturn(false);

        assertThatThrownBy(() -> service.promote(CANDIDATE_ID))
            .isInstanceOf(ResearchFieldPromotionException.class)
            .hasMessage("PROMOTED_ENTITY_CANNOT_BE_REPLACED");

        verifyNoInteractions(targetResolver, linkService);
        verify(candidateRepository, never()).save(candidate);
    }

    @Test
    void doesNotRecordPromotionAgainWhenLatestReviewIsAlreadyPromoted() {
        LaboratoryResearchFieldCandidate candidate = currentCandidate();
        laboratoryReference(candidate);
        Laboratory lockedLaboratory = activeLockedLaboratory();
        ResearchField promotedResearchField = new ResearchField(FIELD_NAME);
        when(candidate.hasBeenPromoted()).thenReturn(true);
        when(candidate.getPromotedResearchField()).thenReturn(promotedResearchField);
        when(candidate.getCandidateName()).thenReturn(FIELD_NAME);
        when(nameNormalizer.equivalent(FIELD_NAME, FIELD_NAME)).thenReturn(true);
        when(linkService.ensure(lockedLaboratory, promotedResearchField))
            .thenReturn(false);
        when(candidate.needsPromotion()).thenReturn(false);

        ResearchFieldPromotionOutcome outcome = service.promote(CANDIDATE_ID);

        assertSkipped(outcome);
        verifyNoInteractions(targetResolver);
        verify(candidate, never()).recordPromotion(
            any(ResearchField.class),
            any(LocalDateTime.class)
        );
        verify(candidateRepository, never()).save(candidate);
    }

    private LaboratoryResearchFieldCandidate currentCandidate() {
        LaboratoryResearchFieldCandidate candidate = candidate();
        when(candidateRepository.findByIdForPromotion(CANDIDATE_ID))
            .thenReturn(Optional.of(candidate));
        when(candidate.isCurrentAndApproved()).thenReturn(true);
        when(candidate.hasConsistentPromotionState()).thenReturn(true);
        return candidate;
    }

    private Laboratory laboratoryReference(
        LaboratoryResearchFieldCandidate candidate
    ) {
        Laboratory reference = laboratory();
        when(candidate.getLaboratory()).thenReturn(reference);
        when(reference.getId()).thenReturn(LABORATORY_ID);
        return reference;
    }

    private Laboratory activeLockedLaboratory() {
        Laboratory lockedLaboratory = laboratory();
        when(laboratoryRepository.findByIdForUpdate(LABORATORY_ID))
            .thenReturn(Optional.of(lockedLaboratory));
        when(lockedLaboratory.isDeleted()).thenReturn(false);
        return lockedLaboratory;
    }

    private LaboratoryResearchFieldCandidate candidate() {
        return org.mockito.Mockito.mock(LaboratoryResearchFieldCandidate.class);
    }

    private Laboratory laboratory() {
        return org.mockito.Mockito.mock(Laboratory.class);
    }

    private void assertSkipped(ResearchFieldPromotionOutcome outcome) {
        assertThat(outcome.fieldCreated()).isFalse();
        assertThat(outcome.linkCreated()).isFalse();
        assertThat(outcome.promotionRecorded()).isFalse();
        assertThat(outcome.skipped()).isTrue();
    }
}

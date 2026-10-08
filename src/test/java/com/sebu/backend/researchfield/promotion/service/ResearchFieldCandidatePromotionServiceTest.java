package com.sebu.backend.researchfield.promotion.service;

import com.sebu.backend.laboratory.domain.Laboratory;
import com.sebu.backend.laboratory.repository.LaboratoryRepository;
import com.sebu.backend.researchfield.candidate.repository.LaboratoryResearchFieldCandidateRepository;
import com.sebu.backend.researchfield.promotion.dto.ResearchFieldPromotionResult;
import com.sebu.backend.researchfield.promotion.exception.ResearchFieldPromotionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResearchFieldCandidatePromotionServiceTest {
    @Mock
    LaboratoryResearchFieldCandidateRepository candidateRepository;

    @Mock
    LaboratoryRepository laboratoryRepository;

    @Mock
    ResearchFieldCandidatePromotionTransactionService transactionService;

    @InjectMocks
    ResearchFieldCandidatePromotionService service;

    @Test
    void aggregatesEachPromotionOutcome() {
        when(candidateRepository.findCurrentApprovedCandidateIds())
            .thenReturn(List.of(11L, 12L, 13L, 14L));
        when(transactionService.promote(11L))
            .thenReturn(ResearchFieldPromotionOutcome.completed(true, true, true));
        when(transactionService.promote(12L))
            .thenReturn(ResearchFieldPromotionOutcome.completed(false, true, true));
        when(transactionService.promote(13L))
            .thenReturn(ResearchFieldPromotionOutcome.completed(false, false, true));
        when(transactionService.promote(14L))
            .thenReturn(ResearchFieldPromotionOutcome.skippedOutcome());

        ResearchFieldPromotionResult result = service.promote(null);

        assertThat(result.candidateCount()).isEqualTo(4);
        assertThat(result.createdFieldCount()).isEqualTo(1);
        assertThat(result.createdLinkCount()).isEqualTo(2);
        assertThat(result.promotedCount()).isEqualTo(3);
        assertThat(result.skippedCount()).isEqualTo(1);
        assertThat(result.failures()).isEmpty();
        verify(candidateRepository).findCurrentApprovedCandidateIds();
        verifyNoInteractions(laboratoryRepository);
    }

    @Test
    void limitsPromotionTargetsToTheRequestedLaboratory() {
        when(laboratoryRepository.findByIdAndDeletedAtIsNull(7L))
            .thenReturn(Optional.of(org.mockito.Mockito.mock(Laboratory.class)));
        when(candidateRepository.findCurrentApprovedCandidateIdsByLaboratoryId(7L))
            .thenReturn(List.of(21L));
        when(transactionService.promote(21L))
            .thenReturn(ResearchFieldPromotionOutcome.skippedOutcome());

        ResearchFieldPromotionResult result = service.promote(7L);

        assertThat(result.candidateCount()).isEqualTo(1);
        assertThat(result.skippedCount()).isEqualTo(1);
        verify(candidateRepository, never()).findCurrentApprovedCandidateIds();
        verify(transactionService).promote(21L);
    }

    @Test
    void isolatesOneCandidateFailureAndContinuesWithTheNextCandidate() {
        IllegalStateException failure = new IllegalStateException(
            "PROMOTED_RESEARCH_FIELD_CANNOT_BE_REPLACED"
        );
        when(candidateRepository.findCurrentApprovedCandidateIds())
            .thenReturn(List.of(31L, 32L));
        when(transactionService.promote(31L)).thenThrow(failure);
        when(transactionService.promote(32L))
            .thenReturn(ResearchFieldPromotionOutcome.completed(false, true, true));

        ResearchFieldPromotionResult result = service.promote(null);

        assertThat(result.candidateCount()).isEqualTo(2);
        assertThat(result.createdLinkCount()).isEqualTo(1);
        assertThat(result.promotedCount()).isEqualTo(1);
        assertThat(result.failedCount()).isEqualTo(1);
        assertThat(result.failures())
            .singleElement()
            .satisfies(candidateFailure -> {
                assertThat(candidateFailure.candidateId()).isEqualTo(31L);
                assertThat(candidateFailure.reason())
                    .isEqualTo("PROMOTED_RESEARCH_FIELD_CANNOT_BE_REPLACED");
                assertThat(candidateFailure.exception()).isSameAs(failure);
            });
        verify(transactionService).promote(31L);
        verify(transactionService).promote(32L);
    }

    @Test
    void retriesOneTimeAfterADataIntegrityConflict() {
        when(candidateRepository.findCurrentApprovedCandidateIds())
            .thenReturn(List.of(41L));
        when(transactionService.promote(41L))
            .thenThrow(new DataIntegrityViolationException("duplicate field"))
            .thenReturn(ResearchFieldPromotionOutcome.completed(false, true, true));

        ResearchFieldPromotionResult result = service.promote(null);

        assertThat(result.createdLinkCount()).isEqualTo(1);
        assertThat(result.promotedCount()).isEqualTo(1);
        assertThat(result.failures()).isEmpty();
        verify(transactionService, times(2)).promote(41L);
    }

    @Test
    void stopsRetryingAfterTheSecondDataIntegrityConflict() {
        when(candidateRepository.findCurrentApprovedCandidateIds())
            .thenReturn(List.of(42L));
        when(transactionService.promote(42L))
            .thenThrow(
                new DataIntegrityViolationException("first conflict"),
                new DataIntegrityViolationException("second conflict")
            );

        ResearchFieldPromotionResult result = service.promote(null);

        assertThat(result.failedCount()).isEqualTo(1);
        assertThat(result.failures().getFirst().reason())
            .isEqualTo("RESEARCH_FIELD_PROMOTION_DATA_CONFLICT");
        verify(transactionService, times(2)).promote(42L);
    }

    @Test
    void rejectsAnUnknownLaboratoryBeforeLookingUpCandidates() {
        when(laboratoryRepository.findByIdAndDeletedAtIsNull(99L))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.promote(99L))
            .isInstanceOf(ResearchFieldPromotionException.class)
            .hasMessage("PROMOTION_LABORATORY_NOT_FOUND: 99");

        verifyNoInteractions(candidateRepository, transactionService);
    }
}

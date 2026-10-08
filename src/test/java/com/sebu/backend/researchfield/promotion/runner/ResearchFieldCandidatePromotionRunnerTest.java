package com.sebu.backend.researchfield.promotion.runner;

import com.sebu.backend.researchfield.promotion.config.ResearchFieldPromotionProfileGuard;
import com.sebu.backend.researchfield.promotion.config.ResearchFieldPromotionProperties;
import com.sebu.backend.researchfield.promotion.dto.ResearchFieldPromotionResult;
import com.sebu.backend.researchfield.promotion.exception.ResearchFieldPromotionException;
import com.sebu.backend.researchfield.promotion.service.ResearchFieldCandidatePromotionService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResearchFieldCandidatePromotionRunnerTest {
    @Test
    void rejectsConflictingOneTimeProfiles() {
        ResearchFieldPromotionProfileGuard guard =
            new ResearchFieldPromotionProfileGuard();

        assertThatThrownBy(() -> guard.run(new DefaultApplicationArguments()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("RESEARCH_FIELD_PROMOTION_PROFILE_CONFLICT");
    }

    @Test
    void delegatesToConfiguredLaboratoryAndCompletesWhenPromotionSucceeds() {
        ResearchFieldCandidatePromotionService service = mock(
            ResearchFieldCandidatePromotionService.class
        );
        ResearchFieldPromotionProperties properties = properties(7L);
        when(service.promote(7L)).thenReturn(new ResearchFieldPromotionResult(
            3,
            2,
            3,
            3,
            0,
            List.of()
        ));
        ResearchFieldCandidatePromotionRunner runner =
            new ResearchFieldCandidatePromotionRunner(service, properties);

        assertThatCode(() -> runner.run(new DefaultApplicationArguments()))
            .doesNotThrowAnyException();
        verify(service).promote(7L);
    }

    @Test
    void reportsProcessFailureAfterCollectingPartialFailures() {
        ResearchFieldCandidatePromotionService service = mock(
            ResearchFieldCandidatePromotionService.class
        );
        ResearchFieldPromotionProperties properties = properties(null);
        RuntimeException cause = new IllegalStateException("FIELD_NAME_CONFLICT");
        when(service.promote(null)).thenReturn(new ResearchFieldPromotionResult(
            2,
            1,
            1,
            1,
            0,
            List.of(new ResearchFieldPromotionResult.Failure(
                11L,
                cause.getMessage(),
                cause
            ))
        ));
        ResearchFieldCandidatePromotionRunner runner =
            new ResearchFieldCandidatePromotionRunner(service, properties);

        assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments()))
            .isInstanceOf(ResearchFieldPromotionException.class)
            .hasMessage("RESEARCH_FIELD_PROMOTION_PARTIALLY_FAILED: 1")
            .hasCause(cause);
        verify(service).promote(null);
    }

    private ResearchFieldPromotionProperties properties(Long laboratoryId) {
        ResearchFieldPromotionProperties properties =
            new ResearchFieldPromotionProperties();
        properties.setEnabled(true);
        properties.setLaboratoryId(laboratoryId);
        return properties;
    }
}

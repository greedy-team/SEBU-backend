package com.sebu.backend.promotion.runner;

import com.sebu.backend.promotion.config.PromotionProperties;
import com.sebu.backend.promotion.config.OneTimeJobProfileGuard;
import com.sebu.backend.promotion.dto.PromotionResult;
import com.sebu.backend.promotion.exception.CandidatePromotionException;
import com.sebu.backend.promotion.service.ProfessorCandidatePromotionService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProfessorCandidatePromotionRunnerTest {
    @Test
    void crawlerAndPromotionProfilesCannotRunInTheSameProcess() {
        OneTimeJobProfileGuard guard = new OneTimeJobProfileGuard();

        assertThatThrownBy(() -> guard.run(new DefaultApplicationArguments()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("CRAWLER_AND_PROMOTION_PROFILES_ARE_MUTUALLY_EXCLUSIVE");
    }

    @Test
    void delegatesToTheConfiguredSourceAndCompletesWhenEveryCandidateSucceeds() {
        ProfessorCandidatePromotionService service = mock(ProfessorCandidatePromotionService.class);
        PromotionProperties properties = properties(3L);
        when(service.promote(3L)).thenReturn(new PromotionResult(2, 2, 0, 0, List.of()));
        ProfessorCandidatePromotionRunner runner = new ProfessorCandidatePromotionRunner(
            service,
            properties
        );

        assertThatCode(() -> runner.run(new DefaultApplicationArguments()))
            .doesNotThrowAnyException();
        verify(service).promote(3L);
    }

    @Test
    void reportsAProcessFailureAfterAllCandidateResultsAreCollected() {
        ProfessorCandidatePromotionService service = mock(ProfessorCandidatePromotionService.class);
        PromotionProperties properties = properties(null);
        RuntimeException cause = new IllegalStateException("PROFESSOR_EMAIL_CONFLICT");
        when(service.promote(null)).thenReturn(new PromotionResult(
            2,
            1,
            0,
            0,
            List.of(new PromotionResult.Failure(7L, cause.getMessage(), cause))
        ));
        ProfessorCandidatePromotionRunner runner = new ProfessorCandidatePromotionRunner(
            service,
            properties
        );

        assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments()))
            .isInstanceOf(CandidatePromotionException.class)
            .hasMessage("CANDIDATE_PROMOTION_PARTIALLY_FAILED: 1")
            .hasCause(cause);
        verify(service).promote(null);
    }

    private PromotionProperties properties(Long sourceId) {
        PromotionProperties properties = new PromotionProperties();
        properties.setEnabled(true);
        properties.setSourceId(sourceId);
        return properties;
    }
}

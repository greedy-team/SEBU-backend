package com.sebu.backend.researchfield.extraction.runner;

import com.sebu.backend.researchfield.extraction.config.ResearchFieldExtractionProfileGuard;
import com.sebu.backend.researchfield.extraction.config.ResearchFieldExtractionProperties;
import com.sebu.backend.researchfield.extraction.dto.ResearchFieldExtractionBatchResult;
import com.sebu.backend.researchfield.extraction.dto.ResearchFieldExtractionResult;
import com.sebu.backend.researchfield.extraction.exception.ResearchFieldExtractionException;
import com.sebu.backend.researchfield.extraction.service.ResearchFieldCandidateBatchService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResearchFieldCandidateExtractionRunnerTest {
    @Test
    void conflictingOneTimeProfilesAreRejected() {
        ResearchFieldExtractionProfileGuard guard = new ResearchFieldExtractionProfileGuard();

        assertThatThrownBy(() -> guard.run(new DefaultApplicationArguments()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("RESEARCH_FIELD_EXTRACTION_PROFILE_CONFLICT");
    }

    @Test
    void delegatesToConfiguredLaboratoryAndCompletesWhenExtractionSucceeds() {
        ResearchFieldCandidateBatchService service = mock(
            ResearchFieldCandidateBatchService.class
        );
        ResearchFieldExtractionProperties properties = properties(7L);
        when(service.extract(7L)).thenReturn(new ResearchFieldExtractionBatchResult(
            1,
            List.of(new ResearchFieldExtractionResult(7L, 3, 3, 0, 0, 0)),
            List.of()
        ));
        ResearchFieldCandidateExtractionRunner runner =
            new ResearchFieldCandidateExtractionRunner(service, properties);

        assertThatCode(() -> runner.run(new DefaultApplicationArguments()))
            .doesNotThrowAnyException();
        verify(service).extract(7L);
    }

    @Test
    void reportsProcessFailureAfterCollectingPartialFailures() {
        ResearchFieldCandidateBatchService service = mock(
            ResearchFieldCandidateBatchService.class
        );
        ResearchFieldExtractionProperties properties = properties(null);
        RuntimeException cause = new IllegalStateException("DATABASE_CONFLICT");
        when(service.extract(null)).thenReturn(new ResearchFieldExtractionBatchResult(
            2,
            List.of(new ResearchFieldExtractionResult(1L, 2, 2, 0, 0, 0)),
            List.of(new ResearchFieldExtractionBatchResult.Failure(
                2L,
                cause.getMessage(),
                cause
            ))
        ));
        ResearchFieldCandidateExtractionRunner runner =
            new ResearchFieldCandidateExtractionRunner(service, properties);

        assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments()))
            .isInstanceOf(ResearchFieldExtractionException.class)
            .hasMessage("RESEARCH_FIELD_EXTRACTION_PARTIALLY_FAILED: 1")
            .hasCause(cause);
    }

    private ResearchFieldExtractionProperties properties(Long laboratoryId) {
        ResearchFieldExtractionProperties properties = new ResearchFieldExtractionProperties();
        properties.setEnabled(true);
        properties.setLaboratoryId(laboratoryId);
        return properties;
    }
}

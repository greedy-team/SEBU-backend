package com.sebu.backend.researchfield.extraction.runner;

import com.sebu.backend.global.logging.BatchLog;
import com.sebu.backend.researchfield.extraction.config.ResearchFieldExtractionProperties;
import com.sebu.backend.researchfield.extraction.dto.ResearchFieldExtractionBatchResult;
import com.sebu.backend.researchfield.extraction.exception.ResearchFieldExtractionException;
import com.sebu.backend.researchfield.extraction.service.ResearchFieldCandidateBatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("research-field-extraction & !crawler & !promotion")
@ConditionalOnProperty(
    prefix = "app.research-field-extraction",
    name = "enabled",
    havingValue = "true"
)
@RequiredArgsConstructor
public class ResearchFieldCandidateExtractionRunner implements ApplicationRunner {
    private final ResearchFieldCandidateBatchService batchService;
    private final ResearchFieldExtractionProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        BatchLog batch = BatchLog.start("RESEARCH_FIELD_EXTRACTION");
        try {
            ResearchFieldExtractionBatchResult batchResult = batchService.extract(properties.getLaboratoryId());
            batch.processed(batchResult.successes().size());
            if (batchResult.hasFailures()) {
                batch.failed(batchResult.failures().size(), batchResult.failures().getFirst().exception());
                throw new ResearchFieldExtractionException("RESEARCH_FIELD_EXTRACTION_PARTIALLY_FAILED: " + batchResult.failures().size(),
                    batchResult.failures().getFirst().exception());
            }
            batch.complete(false);
        } catch (RuntimeException exception) {
            batch.failed(exception);
            throw exception;
        }
    }
}

package com.sebu.backend.researchfield.promotion.runner;

import com.sebu.backend.global.logging.BatchLog;
import com.sebu.backend.researchfield.promotion.config.ResearchFieldPromotionProperties;
import com.sebu.backend.researchfield.promotion.dto.ResearchFieldPromotionResult;
import com.sebu.backend.researchfield.promotion.exception.ResearchFieldPromotionException;
import com.sebu.backend.researchfield.promotion.service.ResearchFieldCandidatePromotionService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile(
    "research-field-promotion"
        + " & !crawler"
        + " & !promotion"
        + " & !research-field-extraction"
        + " & !research-field-manual-split"
)
@ConditionalOnProperty(
    prefix = "app.research-field-promotion",
    name = "enabled",
    havingValue = "true"
)
@RequiredArgsConstructor
public class ResearchFieldCandidatePromotionRunner implements ApplicationRunner {
    private final ResearchFieldCandidatePromotionService promotionService;
    private final ResearchFieldPromotionProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        BatchLog batch = BatchLog.start("RESEARCH_FIELD_PROMOTION");
        try {
            ResearchFieldPromotionResult result = promotionService.promote(properties.getLaboratoryId());
            batch.processed(result.candidateCount() - result.failedCount());
            if (result.hasFailures()) {
                batch.failed(result.failedCount(), result.failures().getFirst().exception());
                throw new ResearchFieldPromotionException("RESEARCH_FIELD_PROMOTION_PARTIALLY_FAILED: " + result.failedCount(),
                    result.failures().getFirst().exception());
            }
            batch.complete(false);
        } catch (RuntimeException exception) {
            batch.failed(exception);
            throw exception;
        }
    }
}

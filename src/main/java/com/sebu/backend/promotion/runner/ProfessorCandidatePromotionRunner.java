package com.sebu.backend.promotion.runner;

import com.sebu.backend.global.logging.BatchLog;
import com.sebu.backend.promotion.config.PromotionProperties;
import com.sebu.backend.promotion.dto.PromotionResult;
import com.sebu.backend.promotion.exception.CandidatePromotionException;
import com.sebu.backend.promotion.service.ProfessorCandidatePromotionService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("promotion & !crawler")
@ConditionalOnProperty(
    prefix = "app.candidate-promotion",
    name = "enabled",
    havingValue = "true"
)
@RequiredArgsConstructor
public class ProfessorCandidatePromotionRunner implements ApplicationRunner {
    private final ProfessorCandidatePromotionService promotionService;
    private final PromotionProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        BatchLog batch = BatchLog.start("PROFESSOR_PROMOTION");
        try {
            PromotionResult result = promotionService.promote(properties.getSourceId());
            batch.processed(result.candidateCount() - result.failedCount());
            if (result.hasFailures()) {
                batch.failed(result.failedCount(), result.failures().getFirst().exception());
                throw new CandidatePromotionException("CANDIDATE_PROMOTION_PARTIALLY_FAILED: " + result.failedCount(),
                    result.failures().getFirst().exception());
            }
            batch.complete(false);
        } catch (RuntimeException exception) {
            batch.failed(exception);
            throw exception;
        }
    }
}

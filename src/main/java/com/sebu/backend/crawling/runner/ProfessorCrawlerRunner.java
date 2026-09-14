package com.sebu.backend.crawling.runner;

import com.sebu.backend.global.logging.BatchLog;
import com.sebu.backend.crawling.config.ProfessorCrawlerProperties;
import com.sebu.backend.crawling.dto.ProfessorCrawlBatchResult;
import com.sebu.backend.crawling.exception.ProfessorCrawlException;
import com.sebu.backend.crawling.service.ProfessorCrawlBatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("crawler & !promotion")
@ConditionalOnProperty(
    prefix = "app.professor-crawler",
    name = "enabled",
    havingValue = "true"
)
@RequiredArgsConstructor
public class ProfessorCrawlerRunner implements ApplicationRunner {
    private final ProfessorCrawlBatchService batchService;
    private final ProfessorCrawlerProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        BatchLog batch = BatchLog.start("PROFESSOR_CRAWL");
        try {
            ProfessorCrawlBatchResult batchResult = batchService.crawl(properties.getSourceId(), properties.getRequestDelay());
            batch.processed(batchResult.successes().size());
            if (batchResult.hasFailures()) {
                batch.failed(batchResult.failures().size(), batchResult.failures().getFirst().exception());
                throw new ProfessorCrawlException("PROFESSOR_CRAWL_PARTIALLY_FAILED: " + batchResult.failures().size(),
                    batchResult.failures().getFirst().exception());
            }
            batch.complete(false);
        } catch (RuntimeException exception) {
            batch.failed(exception);
            throw exception;
        }
    }
}

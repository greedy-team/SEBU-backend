package com.sebu.backend.crawling.service;

import com.sebu.backend.crawling.dto.CrawlSourceRun;
import com.sebu.backend.crawling.dto.FetchedProfessorPage;
import com.sebu.backend.crawling.dto.ProfessorCrawlResult;
import com.sebu.backend.crawling.exception.ProfessorCrawlException;
import com.sebu.backend.crawling.port.ProfessorPageFetcher;
import com.sebu.backend.crawling.port.ProfessorPageParser;
import com.sebu.backend.crawling.domain.CrawlSource;
import com.sebu.backend.crawling.repository.CrawlSourceRepository;
import com.sebu.backend.crawling.domain.ProfessorCrawlData;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProfessorCrawlCoordinator {
    private final CrawlSourceRepository crawlSourceRepository;
    private final ProfessorPageFetcher pageFetcher;
    private final ProfessorPageParserRegistry parserRegistry;
    private final ProfessorCrawlPersistenceService persistenceService;
    private final Clock professorCrawlerClock;

    public ProfessorCrawlResult crawl(Long sourceId) {
        CrawlSource source = crawlSourceRepository.findById(sourceId)
            .orElseThrow(() -> new ProfessorCrawlException("CRAWL_SOURCE_NOT_FOUND: " + sourceId));
        if (!source.isActive()) {
            throw new ProfessorCrawlException("CRAWL_SOURCE_INACTIVE: " + sourceId);
        }
        CrawlSourceRun sourceRun = CrawlSourceRun.from(source);

        try {
            ProfessorPageParser parser = parserRegistry.get(sourceRun.provenance().parserType());
            FetchedProfessorPage page = pageFetcher.fetch(sourceRun.provenance().sourceUrl());
            List<ProfessorCrawlData> data = parser.parse(page);
            return persistenceService.saveSuccessful(
                sourceRun,
                data,
                LocalDateTime.now(professorCrawlerClock)
            );
        } catch (RuntimeException exception) {
            markFailedWithoutMaskingOriginal(sourceRun, exception);
            throw new ProfessorCrawlException(
                "PROFESSOR_CRAWL_FAILED: sourceId=" + sourceId,
                exception
            );
        }
    }

    private void markFailedWithoutMaskingOriginal(CrawlSourceRun sourceRun, RuntimeException original) {
        try {
            persistenceService.markFailed(
                sourceRun,
                LocalDateTime.now(professorCrawlerClock),
                describe(original)
            );
        } catch (RuntimeException failureRecordingException) {
            original.addSuppressed(failureRecordingException);
        }
    }

    private String describe(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return exception.getClass().getSimpleName() + ": " + message;
    }
}

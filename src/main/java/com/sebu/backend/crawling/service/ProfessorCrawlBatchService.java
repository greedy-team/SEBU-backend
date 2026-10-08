package com.sebu.backend.crawling.service;

import com.sebu.backend.crawling.dto.ProfessorCrawlBatchResult;
import com.sebu.backend.crawling.dto.ProfessorCrawlResult;
import com.sebu.backend.crawling.exception.ProfessorCrawlException;
import com.sebu.backend.crawling.domain.CrawlSource;
import com.sebu.backend.crawling.repository.CrawlSourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ProfessorCrawlBatchService {
    private final CrawlSourceRepository crawlSourceRepository;
    private final ProfessorCrawlCoordinator crawlCoordinator;

    public ProfessorCrawlBatchResult crawl(Long targetSourceId, Duration requestDelay) {
        Duration normalizedDelay = requireValidDelay(requestDelay);
        List<CrawlSource> sources = findTargetSources(targetSourceId);
        List<ProfessorCrawlResult> successes = new ArrayList<>();
        List<ProfessorCrawlBatchResult.Failure> failures = new ArrayList<>();

        for (int index = 0; index < sources.size(); index++) {
            CrawlSource source = sources.get(index);
            try {
                successes.add(crawlCoordinator.crawl(source.getId()));
            } catch (ProfessorCrawlException exception) {
                failures.add(new ProfessorCrawlBatchResult.Failure(
                    source.getId(),
                    source.getSourceName(),
                    exception
                ));
            }
            delayBeforeNextSource(index, sources.size(), normalizedDelay);
        }
        return new ProfessorCrawlBatchResult(successes, failures);
    }

    private List<CrawlSource> findTargetSources(Long targetSourceId) {
        if (targetSourceId == null) {
            List<CrawlSource> activeSources = crawlSourceRepository.findAllByActiveTrueOrderByIdAsc();
            if (activeSources.isEmpty()) {
                throw new ProfessorCrawlException("ACTIVE_CRAWL_SOURCE_NOT_FOUND");
            }
            return activeSources;
        }

        CrawlSource source = crawlSourceRepository.findById(targetSourceId)
            .orElseThrow(() -> new ProfessorCrawlException(
                "CRAWL_SOURCE_NOT_FOUND: " + targetSourceId
            ));
        if (!source.isActive()) {
            throw new ProfessorCrawlException("CRAWL_SOURCE_INACTIVE: " + targetSourceId);
        }
        return List.of(source);
    }

    private Duration requireValidDelay(Duration requestDelay) {
        Duration delay = Objects.requireNonNull(requestDelay, "REQUEST_DELAY_REQUIRED");
        if (delay.isNegative()) {
            throw new IllegalArgumentException("REQUEST_DELAY_MUST_NOT_BE_NEGATIVE");
        }
        return delay;
    }

    private void delayBeforeNextSource(int currentIndex, int sourceCount, Duration delay) {
        if (currentIndex >= sourceCount - 1 || delay.isZero()) {
            return;
        }
        try {
            Thread.sleep(delay);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ProfessorCrawlException("PROFESSOR_CRAWL_INTERRUPTED", exception);
        }
    }
}

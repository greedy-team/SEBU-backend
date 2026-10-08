package com.sebu.backend.crawling.service;

import com.sebu.backend.crawling.dto.CrawlSourceRun;
import com.sebu.backend.crawling.dto.ProfessorCrawlReconciliation;
import com.sebu.backend.crawling.dto.ProfessorCrawlResult;
import com.sebu.backend.crawling.exception.ProfessorCrawlException;
import com.sebu.backend.crawling.domain.CrawlSource;
import com.sebu.backend.crawling.domain.CrawlSourceProvenance;
import com.sebu.backend.crawling.repository.CrawlSourceRepository;
import com.sebu.backend.crawling.domain.ProfessorCrawlCandidate;
import com.sebu.backend.crawling.repository.ProfessorCrawlCandidateRepository;
import com.sebu.backend.crawling.domain.ProfessorCrawlData;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProfessorCrawlPersistenceService {
    private static final int MAX_ERROR_MESSAGE_LENGTH = 1000;

    private final CrawlSourceRepository crawlSourceRepository;
    private final ProfessorCrawlCandidateRepository candidateRepository;
    private final ProfessorCrawlCandidateReconciler candidateReconciler;

    @Transactional
    public ProfessorCrawlResult saveSuccessful(
        CrawlSourceRun sourceRun,
        List<ProfessorCrawlData> crawledData,
        LocalDateTime crawledAt
    ) {
        CrawlSource source = getMatchingActiveSource(sourceRun);
        Long sourceId = sourceRun.sourceId();
        CrawlSourceProvenance provenance = sourceRun.provenance();
        ProfessorCrawlReconciliation reconciliation = candidateReconciler.reconcile(
            candidateRepository.findAllBySourceId(sourceId),
            crawledData
        );

        List<ProfessorCrawlCandidate> newCandidates = new ArrayList<>();
        int refreshedCount = 0;
        for (ProfessorCrawlReconciliation.Item item : reconciliation.items()) {
            if (item.isNewCandidate()) {
                newCandidates.add(new ProfessorCrawlCandidate(
                    source,
                    item.data(),
                    provenance,
                    crawledAt
                ));
                continue;
            }
            ProfessorCrawlCandidate candidate = item.candidate();
            candidate.reidentify(item.resolvedIdentityKey());
            candidate.refreshFromCrawl(item.data(), provenance, crawledAt);
            refreshedCount++;
        }

        int staleCount = 0;
        for (ProfessorCrawlCandidate candidate : reconciliation.missingCandidates()) {
            if (!candidate.isStale()) {
                candidate.markStale();
                staleCount++;
            }
        }

        candidateRepository.saveAll(newCandidates);
        source.markSucceeded(crawledAt);
        return new ProfessorCrawlResult(
            source.getId(),
            sourceRun.sourceName(),
            reconciliation.items().size(),
            newCandidates.size(),
            refreshedCount,
            staleCount
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(
        CrawlSourceRun sourceRun,
        LocalDateTime failedAt,
        String errorMessage
    ) {
        CrawlSource source = getMatchingActiveSource(sourceRun);
        source.markFailed(failedAt, truncate(errorMessage));
    }

    private CrawlSource getMatchingActiveSource(CrawlSourceRun sourceRun) {
        CrawlSource source = crawlSourceRepository.findByIdForUpdate(sourceRun.sourceId())
            .orElseThrow(() -> new ProfessorCrawlException(
                "CRAWL_SOURCE_NOT_FOUND: " + sourceRun.sourceId()
            ));
        CrawlSourceProvenance current = CrawlSourceProvenance.from(source);
        if (!source.isActive() || !current.equals(sourceRun.provenance())) {
            throw new ProfessorCrawlException(
                "CRAWL_SOURCE_CONFIGURATION_CHANGED: " + sourceRun.sourceId()
            );
        }
        return source;
    }

    private String truncate(String value) {
        if (value == null || value.length() <= MAX_ERROR_MESSAGE_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_ERROR_MESSAGE_LENGTH);
    }
}

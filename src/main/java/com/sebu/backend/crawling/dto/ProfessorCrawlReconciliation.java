package com.sebu.backend.crawling.dto;

import com.sebu.backend.crawling.domain.ProfessorCrawlCandidate;
import com.sebu.backend.crawling.domain.ProfessorCrawlData;

import java.util.List;
import java.util.Objects;

public record ProfessorCrawlReconciliation(
    List<Item> items,
    List<ProfessorCrawlCandidate> missingCandidates
) {
    public ProfessorCrawlReconciliation {
        items = List.copyOf(Objects.requireNonNull(items, "RECONCILIATION_ITEMS_REQUIRED"));
        missingCandidates = List.copyOf(Objects.requireNonNull(
            missingCandidates,
            "MISSING_CANDIDATES_REQUIRED"
        ));
    }

    public record Item(
        ProfessorCrawlData data,
        ProfessorCrawlCandidate candidate,
        String resolvedIdentityKey
    ) {
        public Item {
            data = Objects.requireNonNull(data, "CRAWL_DATA_REQUIRED");
            if (resolvedIdentityKey == null || resolvedIdentityKey.isBlank()) {
                throw new IllegalArgumentException("CRAWL_IDENTITY_REQUIRED");
            }
            resolvedIdentityKey = resolvedIdentityKey.trim();
        }

        public boolean isNewCandidate() {
            return candidate == null;
        }
    }
}

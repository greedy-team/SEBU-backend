package com.sebu.backend.crawling.dto;

import com.sebu.backend.crawling.exception.ProfessorCrawlException;
import java.util.List;

public record ProfessorCrawlBatchResult(
    List<ProfessorCrawlResult> successes,
    List<Failure> failures
) {
    public ProfessorCrawlBatchResult {
        successes = List.copyOf(successes);
        failures = List.copyOf(failures);
    }

    public int sourceCount() {
        return successes.size() + failures.size();
    }

    public int totalCandidateCount() {
        return successes.stream()
            .mapToInt(ProfessorCrawlResult::crawledCount)
            .sum();
    }

    public boolean hasFailures() {
        return !failures.isEmpty();
    }

    public record Failure(
        Long sourceId,
        String sourceName,
        ProfessorCrawlException exception
    ) {
        public String reason() {
            Throwable rootCause = exception;
            while (rootCause.getCause() != null) {
                rootCause = rootCause.getCause();
            }
            String message = rootCause.getMessage();
            if (message == null || message.isBlank()) {
                return rootCause.getClass().getSimpleName();
            }
            return rootCause.getClass().getSimpleName() + ": " + message;
        }
    }
}

package com.sebu.backend.crawling.dto;

public record ProfessorCrawlResult(
    Long sourceId,
    String sourceName,
    int crawledCount,
    int createdCount,
    int refreshedCount,
    int staleCount
) {
}

package com.sebu.backend.crawling.domain;

import java.util.Objects;

public record CrawlSourceProvenance(
    String sourceUrl,
    CrawlParserType parserType
) {
    public CrawlSourceProvenance {
        if (sourceUrl == null || sourceUrl.isBlank()) {
            throw new IllegalArgumentException("SOURCE_URL_REQUIRED");
        }
        sourceUrl = sourceUrl.trim();
        parserType = Objects.requireNonNull(parserType, "PARSER_TYPE_REQUIRED");
    }

    public static CrawlSourceProvenance from(CrawlSource source) {
        Objects.requireNonNull(source, "SOURCE_REQUIRED");
        return new CrawlSourceProvenance(source.getSourceUrl(), source.getParserType());
    }
}

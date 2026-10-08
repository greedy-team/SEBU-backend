package com.sebu.backend.crawling.domain;

import com.sebu.backend.global.domain.BaseTimeEntity;
import com.sebu.backend.department.domain.Department;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Objects;

@Getter
@Entity
@Table(
    name = "crawl_source",
    uniqueConstraints = @UniqueConstraint(name = "uk_crawl_source_url", columnNames = "source_url"),
    indexes = {
        @Index(name = "idx_crawl_source_department", columnList = "department_id"),
        @Index(name = "idx_crawl_source_active", columnList = "is_active")
    }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CrawlSource extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(nullable = false)
    private long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @Column(name = "source_name", nullable = false, length = 150)
    private String sourceName;

    @Column(name = "source_url", nullable = false, length = 512)
    private String sourceUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "parser_type", nullable = false, length = 50)
    private CrawlParserType parserType;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "last_crawled_at")
    private LocalDateTime lastCrawledAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_crawl_status", nullable = false, length = 30)
    private CrawlSourceStatus lastCrawlStatus = CrawlSourceStatus.NOT_STARTED;

    @Column(name = "last_error_message", length = 1000)
    private String lastErrorMessage;

    public CrawlSource(
        Department department,
        String sourceName,
        String sourceUrl,
        CrawlParserType parserType
    ) {
        this.department = Objects.requireNonNull(department, "DEPARTMENT_REQUIRED");
        this.sourceName = requireText(sourceName, "SOURCE_NAME_REQUIRED");
        this.sourceUrl = requireText(sourceUrl, "SOURCE_URL_REQUIRED");
        this.parserType = Objects.requireNonNull(parserType, "PARSER_TYPE_REQUIRED");
    }

    public void markSucceeded(LocalDateTime crawledAt) {
        lastCrawledAt = Objects.requireNonNull(crawledAt, "CRAWLED_AT_REQUIRED");
        lastCrawlStatus = CrawlSourceStatus.SUCCESS;
        lastErrorMessage = null;
    }

    public void markFailed(LocalDateTime crawledAt, String errorMessage) {
        lastCrawledAt = Objects.requireNonNull(crawledAt, "CRAWLED_AT_REQUIRED");
        lastCrawlStatus = CrawlSourceStatus.FAILED;
        lastErrorMessage = normalizeNullable(errorMessage);
    }

    public void rename(String sourceName) {
        this.sourceName = requireText(sourceName, "SOURCE_NAME_REQUIRED");
    }

    public void changeEndpoint(
        String sourceUrl,
        CrawlParserType parserType
    ) {
        String normalizedUrl = requireText(sourceUrl, "SOURCE_URL_REQUIRED");
        CrawlParserType normalizedParserType = Objects.requireNonNull(parserType, "PARSER_TYPE_REQUIRED");
        boolean changed = !Objects.equals(this.sourceUrl, normalizedUrl)
            || this.parserType != normalizedParserType;

        this.sourceUrl = normalizedUrl;
        this.parserType = normalizedParserType;
        if (changed) {
            resetCrawlResult();
        }
    }

    public void activate() {
        active = true;
    }

    public void deactivate() {
        active = false;
    }

    private void resetCrawlResult() {
        lastCrawledAt = null;
        lastCrawlStatus = CrawlSourceStatus.NOT_STARTED;
        lastErrorMessage = null;
    }

    private String requireText(String value, String errorCode) {
        String normalized = normalizeNullable(value);
        if (normalized == null) {
            throw new IllegalArgumentException(errorCode);
        }
        return normalized;
    }

    private String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}

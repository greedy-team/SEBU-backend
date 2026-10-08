package com.sebu.backend.crawling.service;

import com.sebu.backend.crawling.dto.CrawlSourceRun;
import com.sebu.backend.crawling.exception.ProfessorCrawlException;
import com.sebu.backend.crawling.port.ProfessorPageFetcher;
import com.sebu.backend.crawling.port.ProfessorPageParser;
import com.sebu.backend.crawling.domain.CrawlParserType;
import com.sebu.backend.crawling.domain.CrawlSource;
import com.sebu.backend.crawling.repository.CrawlSourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfessorCrawlCoordinatorTest {
    private static final Long SOURCE_ID = 1L;
    private static final String SOURCE_URL = "https://example.com/professors";
    private static final Clock CLOCK = Clock.fixed(
        Instant.parse("2026-08-17T01:00:00Z"),
        ZoneOffset.UTC
    );

    @Mock
    CrawlSourceRepository crawlSourceRepository;

    @Mock
    ProfessorPageFetcher pageFetcher;

    @Mock
    ProfessorPageParserRegistry parserRegistry;

    @Mock
    ProfessorPageParser parser;

    @Mock
    ProfessorCrawlPersistenceService persistenceService;

    @Mock
    CrawlSource source;

    ProfessorCrawlCoordinator coordinator;

    @BeforeEach
    void setUp() {
        coordinator = new ProfessorCrawlCoordinator(
            crawlSourceRepository,
            pageFetcher,
            parserRegistry,
            persistenceService,
            CLOCK
        );
        when(crawlSourceRepository.findById(SOURCE_ID)).thenReturn(Optional.of(source));
        when(source.getId()).thenReturn(SOURCE_ID);
        when(source.getSourceName()).thenReturn("테스트 학과 교수진");
        when(source.isActive()).thenReturn(true);
        when(source.getParserType()).thenReturn(CrawlParserType.SEJONG_STANDARD);
        when(source.getSourceUrl()).thenReturn(SOURCE_URL);
        when(parserRegistry.get(CrawlParserType.SEJONG_STANDARD)).thenReturn(parser);
    }

    @Test
    void recordsFailureWhenPageFetchFails() {
        ProfessorCrawlException fetchFailure = new ProfessorCrawlException("PAGE_FETCH_FAILED");
        when(pageFetcher.fetch(SOURCE_URL)).thenThrow(fetchFailure);

        assertThatThrownBy(() -> coordinator.crawl(SOURCE_ID))
            .isInstanceOf(ProfessorCrawlException.class)
            .hasMessage("PROFESSOR_CRAWL_FAILED: sourceId=1")
            .hasCause(fetchFailure);

        verify(persistenceService).markFailed(
            any(CrawlSourceRun.class),
            eq(LocalDateTime.of(2026, 8, 17, 1, 0)),
            startsWith("ProfessorCrawlException: PAGE_FETCH_FAILED")
        );
    }

    @Test
    void preservesTheOriginalFailureWhenFailureRecordingAlsoFails() {
        ProfessorCrawlException fetchFailure = new ProfessorCrawlException("PAGE_FETCH_FAILED");
        IllegalStateException recordFailure = new IllegalStateException("DATABASE_UNAVAILABLE");
        when(pageFetcher.fetch(SOURCE_URL)).thenThrow(fetchFailure);
        doThrow(recordFailure).when(persistenceService).markFailed(
            any(CrawlSourceRun.class),
            any(LocalDateTime.class),
            any(String.class)
        );

        assertThatThrownBy(() -> coordinator.crawl(SOURCE_ID))
            .isInstanceOf(ProfessorCrawlException.class)
            .satisfies(thrown -> assertThat(thrown.getCause().getSuppressed())
                .containsExactly(recordFailure));
    }
}

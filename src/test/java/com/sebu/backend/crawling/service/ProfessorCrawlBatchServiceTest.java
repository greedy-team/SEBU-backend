package com.sebu.backend.crawling.service;

import com.sebu.backend.crawling.dto.ProfessorCrawlBatchResult;
import com.sebu.backend.crawling.dto.ProfessorCrawlResult;
import com.sebu.backend.crawling.exception.ProfessorCrawlException;
import com.sebu.backend.crawling.domain.CrawlSource;
import com.sebu.backend.crawling.repository.CrawlSourceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfessorCrawlBatchServiceTest {
    @Mock
    CrawlSourceRepository crawlSourceRepository;

    @Mock
    ProfessorCrawlCoordinator crawlCoordinator;

    @InjectMocks
    ProfessorCrawlBatchService batchService;

    @Test
    void continuesWithTheNextSourceAfterOneSourceFails() {
        CrawlSource failedSource = source(1L, "첫 번째 학과");
        CrawlSource successfulSource = source(2L, "두 번째 학과");
        when(crawlSourceRepository.findAllByActiveTrueOrderByIdAsc())
            .thenReturn(List.of(failedSource, successfulSource));
        when(crawlCoordinator.crawl(1L))
            .thenThrow(new ProfessorCrawlException(
                "PROFESSOR_CRAWL_FAILED",
                new IllegalStateException("PAGE_FETCH_FAILED")
            ));
        when(crawlCoordinator.crawl(2L))
            .thenReturn(new ProfessorCrawlResult(2L, "두 번째 학과", 3, 3, 0, 0));

        ProfessorCrawlBatchResult result = batchService.crawl(null, Duration.ZERO);

        assertThat(result.sourceCount()).isEqualTo(2);
        assertThat(result.successes()).hasSize(1);
        assertThat(result.failures())
            .singleElement()
            .satisfies(failure -> {
                assertThat(failure.sourceId()).isEqualTo(1L);
                assertThat(failure.reason())
                    .isEqualTo("IllegalStateException: PAGE_FETCH_FAILED");
            });
        assertThat(result.totalCandidateCount()).isEqualTo(3);
        verify(crawlCoordinator).crawl(1L);
        verify(crawlCoordinator).crawl(2L);
    }

    private CrawlSource source(Long id, String sourceName) {
        CrawlSource source = org.mockito.Mockito.mock(CrawlSource.class);
        when(source.getId()).thenReturn(id);
        lenient().when(source.getSourceName()).thenReturn(sourceName);
        return source;
    }
}

package com.sebu.backend.crawling.service;

import com.sebu.backend.crawling.dto.FetchedProfessorPage;
import com.sebu.backend.crawling.exception.ProfessorCrawlException;
import com.sebu.backend.crawling.port.ProfessorPageFetcher;
import com.sebu.backend.crawling.domain.CrawlSource;
import com.sebu.backend.crawling.repository.CrawlSourceRepository;
import com.sebu.backend.crawling.domain.CrawlSourceStatus;
import com.sebu.backend.crawling.repository.ProfessorCrawlCandidateRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ProfessorCrawlTransactionIntegrationTest {
    private static final String COMPUTER_SCIENCE_URL =
        "https://dept.sejong.ac.kr/cedpt/intro/professor.do";

    @MockitoBean
    ProfessorPageFetcher pageFetcher;

    @Autowired
    ProfessorCrawlCoordinator crawlCoordinator;

    @Autowired
    CrawlSourceRepository crawlSourceRepository;

    @Autowired
    ProfessorCrawlCandidateRepository candidateRepository;

    @Test
    void candidateTransactionRollsBackWhileFailureStateCommitsSeparately() {
        CrawlSource source = crawlSourceRepository.findBySourceUrl(COMPUTER_SCIENCE_URL)
            .orElseThrow();
        String oversizedResearchIntroduction = "가".repeat(2001);
        String html = """
            <html><body><ul id="proShow"><li>
              <div class="b-professor-name"><p>트랜잭션테스트</p></div>
              <div class="b-professor-field"><p>%s</p></div>
            </li></ul></body></html>
            """.formatted(oversizedResearchIntroduction);
        when(pageFetcher.fetch(COMPUTER_SCIENCE_URL))
            .thenReturn(new FetchedProfessorPage(html, COMPUTER_SCIENCE_URL));

        assertThatThrownBy(() -> crawlCoordinator.crawl(source.getId()))
            .isInstanceOf(ProfessorCrawlException.class)
            .hasMessage("PROFESSOR_CRAWL_FAILED: sourceId=" + source.getId());

        assertThat(candidateRepository.findAllBySourceId(source.getId())).isEmpty();
        CrawlSource failedSource = crawlSourceRepository.findById(source.getId()).orElseThrow();
        assertThat(failedSource.getLastCrawlStatus()).isEqualTo(CrawlSourceStatus.FAILED);
        assertThat(failedSource.getLastErrorMessage()).isNotBlank();
    }
}

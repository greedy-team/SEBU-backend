package com.sebu.backend.crawling.service;

import com.sebu.backend.crawling.domain.CandidateReviewStatus;
import com.sebu.backend.crawling.domain.CrawlParserType;
import com.sebu.backend.crawling.domain.CrawlSource;
import com.sebu.backend.crawling.domain.CrawlSourceStatus;
import com.sebu.backend.crawling.domain.ProfessorCrawlCandidate;
import com.sebu.backend.crawling.dto.FetchedProfessorPage;
import com.sebu.backend.crawling.exception.ProfessorCrawlException;
import com.sebu.backend.crawling.port.ProfessorPageFetcher;
import com.sebu.backend.crawling.repository.CrawlSourceRepository;
import com.sebu.backend.crawling.repository.ProfessorCrawlCandidateRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AerospaceProfessorCrawlIntegrationTest {
    private Long createdSourceId;

    @MockitoBean
    ProfessorPageFetcher pageFetcher;
    @Autowired
    ProfessorCrawlCoordinator coordinator;
    @Autowired
    CrawlSourceRepository sourceRepository;
    @Autowired
    ProfessorCrawlCandidateRepository candidateRepository;
    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void removeOnlyThisTestsSourceAndCandidates() {
        if (createdSourceId != null) {
            candidateRepository.deleteAll(candidateRepository.findAllBySourceId(createdSourceId));
            sourceRepository.deleteById(createdSourceId);
        }
    }

    @Test
    void savesPendingCandidatesAndPreservesApprovalOnUnchangedRerunsWithoutPromoting() throws IOException {
        CrawlSource source = createSource("82031");
        when(pageFetcher.fetch(source.getSourceUrl())).thenReturn(page(source, fixture()));
        int professorCount = jdbc.queryForObject("SELECT COUNT(*) FROM professor", Integer.class);
        int laboratoryCount = jdbc.queryForObject("SELECT COUNT(*) FROM laboratory", Integer.class);

        assertThat(coordinator.crawl(source.getId()).createdCount()).isEqualTo(3);
        List<ProfessorCrawlCandidate> candidates = candidateRepository.findAllBySourceId(source.getId());
        assertThat(candidates).hasSize(3).allSatisfy(candidate -> {
            assertThat(candidate.getReviewStatus()).isEqualTo(CandidateReviewStatus.PENDING);
            assertThat(candidate.getParserTypeAtCrawl()).isEqualTo(CrawlParserType.SEJONG_AEROSPACE);
            assertThat(candidate.getSourceUrlAtCrawl()).isEqualTo(source.getSourceUrl());
            assertThat(candidate.getLaboratoryName()).isNull();
            assertThat(candidate.getHomepageUrl()).isNull();
            assertThat(candidate.getPromotedAt()).isNull();
        });
        ProfessorCrawlCandidate reviewed = candidates.getFirst();
        reviewed.approve("test-reviewer", "검수 완료", LocalDateTime.now());
        candidateRepository.saveAndFlush(reviewed);

        assertThat(coordinator.crawl(source.getId()).createdCount()).isZero();
        assertThat(candidateRepository.findAllBySourceId(source.getId())).hasSize(3);
        assertThat(candidateRepository.findById(reviewed.getId()).orElseThrow().getReviewStatus())
            .isEqualTo(CandidateReviewStatus.APPROVED);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM professor", Integer.class)).isEqualTo(professorCount);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM laboratory", Integer.class)).isEqualTo(laboratoryCount);
    }

    @Test
    void partialPageFailureLeavesExistingCandidatesCurrentAndUnchanged() throws IOException {
        CrawlSource source = createSource("82041");
        when(pageFetcher.fetch(source.getSourceUrl())).thenReturn(page(source, fixture()));
        coordinator.crawl(source.getId());
        List<Long> ids = candidateRepository.findAllBySourceId(source.getId()).stream()
            .map(ProfessorCrawlCandidate::getId).toList();
        when(pageFetcher.fetch(source.getSourceUrl()))
            .thenReturn(page(source, fixture().replace("총 3건, 1/1 페이지", "총 6건, 1/2 페이지")));

        assertThatThrownBy(() -> coordinator.crawl(source.getId()))
            .isInstanceOf(ProfessorCrawlException.class)
            .hasRootCauseMessage("SEJONG_AEROSPACE_MULTIPLE_PAGES_NOT_SUPPORTED");
        assertThat(candidateRepository.findAllBySourceId(source.getId()))
            .extracting(ProfessorCrawlCandidate::getId).containsExactlyInAnyOrderElementsOf(ids);
        assertThat(candidateRepository.findAllBySourceId(source.getId()))
            .allSatisfy(candidate -> assertThat(candidate.isStale()).isFalse());
        assertThat(sourceRepository.findById(source.getId()).orElseThrow().getLastCrawlStatus())
            .isEqualTo(CrawlSourceStatus.FAILED);
    }

    private CrawlSource createSource(String category) {
        CrawlSource existing = sourceRepository.findAll().getFirst();
        CrawlSource source = sourceRepository.saveAndFlush(new CrawlSource(
            existing.getDepartment(), "우주항공 파서 테스트 " + category,
            "https://ae.sejong.ac.kr/shop_contents/myboard_list.htm?myboard_code=professor&category_idx=" + category,
            CrawlParserType.SEJONG_AEROSPACE
        ));
        createdSourceId = source.getId();
        return source;
    }

    private FetchedProfessorPage page(CrawlSource source, String html) {
        return new FetchedProfessorPage(html, source.getSourceUrl());
    }

    private String fixture() throws IOException {
        return new ClassPathResource("fixtures/crawling/sejong-aerospace-professors.html")
            .getContentAsString(StandardCharsets.UTF_8);
    }
}

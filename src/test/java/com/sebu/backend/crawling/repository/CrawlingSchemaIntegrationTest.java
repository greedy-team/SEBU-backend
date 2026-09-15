package com.sebu.backend.crawling.repository;

import com.sebu.backend.crawling.domain.CandidateReviewStatus;
import com.sebu.backend.crawling.domain.CrawlParserType;
import com.sebu.backend.crawling.domain.CrawlSource;
import com.sebu.backend.crawling.domain.CrawlSourceProvenance;
import com.sebu.backend.crawling.domain.ProfessorCrawlCandidate;
import com.sebu.backend.crawling.domain.ProfessorCrawlData;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class CrawlingSchemaIntegrationTest {
    private static final String COMPUTER_SCIENCE_URL =
        "https://dept.sejong.ac.kr/cedpt/intro/professor.do";
    private static final String INTELLIGENT_INFORMATION_URL =
        "https://dept.sejong.ac.kr/aiitdpt/intro/professor.do";

    @Autowired
    CrawlSourceRepository crawlSourceRepository;

    @Autowired
    ProfessorCrawlCandidateRepository candidateRepository;

    @Autowired
    EntityManager entityManager;

    @Autowired
    ApplicationContext applicationContext;

    @Test
    void normalServerProfileDoesNotCreateTheOneTimeCrawlerRunner() {
        assertThat(applicationContext.getBeansOfType(
            com.sebu.backend.crawling.runner.ProfessorCrawlerRunner.class
        )).isEmpty();
        assertThat(applicationContext.getBeansOfType(
            com.sebu.backend.promotion.runner.ProfessorCandidatePromotionRunner.class
        )).isEmpty();
    }

    @Test
    void csvSourcesAreSeededWithoutWhitespaceOrDuplicates() {
        List<CrawlSource> sources = crawlSourceRepository.findAll();

        assertThat(sources).hasSize(42);
        assertThat(sources)
            .extracting(CrawlSource::getSourceUrl)
            .doesNotHaveDuplicates()
            .allMatch(url -> url.equals(url.trim()));
        assertThat(crawlSourceRepository.findBySourceUrl(INTELLIGENT_INFORMATION_URL))
            .isPresent();
        assertThat(sources)
            .filteredOn(source -> source.getParserType() == CrawlParserType.SEJONG_QUANTUM)
            .singleElement()
            .extracting(CrawlSource::getSourceName)
            .isEqualTo("양자지능정보학과 교수진");
    }

    @Test
    void candidateValuesAreNormalizedAndMapped() {
        CrawlSource source = crawlSourceRepository.findBySourceUrl(COMPUTER_SCIENCE_URL)
            .orElseThrow();
        ProfessorCrawlCandidate saved = candidateRepository.saveAndFlush(
            new ProfessorCrawlCandidate(
                source,
                new ProfessorCrawlData(
                    " 홍길동 ",
                    " 조교수 ",
                    " PROFESSOR@SEJONG.AC.KR ",
                    " ",
                    " 인공지능 연구 ",
                    " https://example.com/lab "
                ),
                CrawlSourceProvenance.from(source),
                LocalDateTime.now()
            )
        );

        entityManager.clear();

        ProfessorCrawlCandidate found = candidateRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getProfessorName()).isEqualTo("홍길동");
        assertThat(found.getPosition()).isEqualTo("조교수");
        assertThat(found.getEmail()).isEqualTo("professor@sejong.ac.kr");
        assertThat(found.getSourceIdentityKey()).isEqualTo("email:professor@sejong.ac.kr");
        assertThat(found.getLaboratoryName()).isNull();
        assertThat(found.getResearchIntroduction()).isEqualTo("인공지능 연구");
        assertThat(found.getHomepageUrl()).isEqualTo("https://example.com/lab");
        assertThat(found.getSourceUrlAtCrawl()).isEqualTo(COMPUTER_SCIENCE_URL);
        assertThat(found.getParserTypeAtCrawl()).isEqualTo(CrawlParserType.SEJONG_STANDARD);
        assertThat(found.isStale()).isFalse();
        assertThat(found.getReviewStatus()).isEqualTo(CandidateReviewStatus.PENDING);
        assertThat(found.getVersion()).isZero();
    }
}

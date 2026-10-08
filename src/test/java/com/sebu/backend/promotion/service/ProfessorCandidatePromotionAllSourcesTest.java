package com.sebu.backend.promotion.service;

import com.sebu.backend.college.domain.College;
import com.sebu.backend.college.repository.CollegeRepository;
import com.sebu.backend.crawling.domain.CrawlParserType;
import com.sebu.backend.crawling.domain.CrawlSource;
import com.sebu.backend.crawling.domain.CrawlSourceProvenance;
import com.sebu.backend.crawling.domain.ProfessorCrawlCandidate;
import com.sebu.backend.crawling.domain.ProfessorCrawlData;
import com.sebu.backend.crawling.repository.CrawlSourceRepository;
import com.sebu.backend.crawling.repository.ProfessorCrawlCandidateRepository;
import com.sebu.backend.department.domain.Department;
import com.sebu.backend.department.repository.DepartmentRepository;
import com.sebu.backend.promotion.dto.PromotionResult;
import com.sebu.backend.promotion.exception.CandidatePromotionException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:promotion-all-sources-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ProfessorCandidatePromotionAllSourcesTest {
    @Autowired
    ProfessorCandidatePromotionService promotionService;

    @Autowired
    CollegeRepository collegeRepository;

    @Autowired
    DepartmentRepository departmentRepository;

    @Autowired
    CrawlSourceRepository sourceRepository;

    @Autowired
    ProfessorCrawlCandidateRepository candidateRepository;

    @Test
    void nullSourceIdPromotesApprovedCandidatesAcrossEverySource() {
        approveCandidate(newSource(), "전체승격A", "all-a@example.com");
        approveCandidate(newSource(), "전체승격B", "all-b@example.com");

        PromotionResult result = promotionService.promote(null);

        assertThat(result.candidateCount()).isEqualTo(2);
        assertThat(result.createdCount()).isEqualTo(2);
        assertThat(result.failures()).isEmpty();
    }

    @Test
    void unknownSourceIdFailsBeforeAnyCandidateIsChanged() {
        assertThatThrownBy(() -> promotionService.promote(Long.MAX_VALUE))
            .isInstanceOf(CandidatePromotionException.class)
            .hasMessage("CRAWL_SOURCE_NOT_FOUND: " + Long.MAX_VALUE);
    }

    private CrawlSource newSource() {
        String suffix = UUID.randomUUID().toString();
        College college = collegeRepository.save(new College("전체 승격 대학 " + suffix));
        Department department = departmentRepository.save(
            new Department(college, "전체 승격 학과 " + suffix)
        );
        return sourceRepository.saveAndFlush(new CrawlSource(
            department,
            "전체 승격 교수진 " + suffix,
            "https://example.com/all-sources/" + suffix,
            CrawlParserType.SEJONG_STANDARD
        ));
    }

    private void approveCandidate(CrawlSource source, String name, String email) {
        LocalDateTime now = LocalDateTime.now();
        ProfessorCrawlCandidate candidate = new ProfessorCrawlCandidate(
            source,
            new ProfessorCrawlData(
                name,
                "교수",
                email,
                null,
                "전체 출처 승격 연구 소개",
                "https://example.com/professors/" + name
            ),
            CrawlSourceProvenance.from(source),
            now
        );
        candidate.approve("reviewer", "검수 완료", now.plusSeconds(1));
        candidateRepository.saveAndFlush(candidate);
    }
}

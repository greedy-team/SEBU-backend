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
import com.sebu.backend.professor.domain.Professor;
import com.sebu.backend.professor.repository.ProfessorRepository;
import com.sebu.backend.promotion.dto.PromotionResult;
import com.sebu.backend.laboratory.domain.LaboratoryNameSource;
import com.sebu.backend.laboratory.domain.WebsiteUrlSource;
import com.sebu.backend.laboratory.repository.LaboratoryRepository;
import com.sebu.backend.laboratory.service.LaboratoryQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:promotion-service-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ProfessorCandidatePromotionServiceTest {
    @Autowired
    ProfessorCandidatePromotionService promotionService;

    @Autowired
    CrawlSourceRepository sourceRepository;

    @Autowired
    CollegeRepository collegeRepository;

    @Autowired
    DepartmentRepository departmentRepository;

    @Autowired
    ProfessorCrawlCandidateRepository candidateRepository;

    @Autowired
    ProfessorRepository professorRepository;

    @Autowired
    LaboratoryRepository laboratoryRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    LaboratoryQueryService laboratoryQueryService;

    @Test
    void promotesOnlyApprovedCurrentCandidatesAndIsIdempotent() {
        CrawlSource source = newSource();
        String suffix = uniqueSuffix();
        ProfessorCrawlCandidate official = approvedCandidate(
            source,
            data("공식" + suffix, "official-" + suffix + "@sejong.ac.kr", "AI 시스템 연구실")
        );
        ProfessorCrawlCandidate generated = approvedCandidate(
            source,
            data("생성" + suffix, null, null)
        );
        ProfessorCrawlCandidate pending = candidateRepository.saveAndFlush(new ProfessorCrawlCandidate(
            source,
            data("대기" + suffix, "pending-" + suffix + "@sejong.ac.kr", null),
            CrawlSourceProvenance.from(source),
            LocalDateTime.now()
        ));
        ProfessorCrawlCandidate stale = approvedCandidate(
            source,
            data("제외" + suffix, "stale-" + suffix + "@sejong.ac.kr", "제외 연구실")
        );
        stale.markStale();
        candidateRepository.saveAndFlush(stale);

        PromotionResult first = promotionService.promote(source.getId());

        assertThat(first.candidateCount()).isEqualTo(2);
        assertThat(first.createdCount()).isEqualTo(2);
        assertThat(first.updatedCount()).isZero();
        assertThat(first.skippedCount()).isZero();
        assertThat(first.failures()).isEmpty();
        assertLaboratory(official.getId(), "AI 시스템 연구실", "OFFICIAL", "UNKNOWN");
        assertLaboratory(
            generated.getId(),
            generated.getProfessorName() + " 교수님 연구실",
            "GENERATED",
            "UNKNOWN"
        );
        assertThat(promotionColumns(pending.getId()).get("promoted_at")).isNull();
        assertThat(promotionColumns(stale.getId()).get("promoted_at")).isNull();
        Long generatedLaboratoryId = laboratoryId(generated.getId());
        assertThat(laboratoryQueryService.getAll().laboratories())
            .filteredOn(laboratory -> laboratory.id().equals(generatedLaboratoryId))
            .singleElement()
            .satisfies(laboratory -> {
                assertThat(laboratory.name())
                    .isEqualTo(generated.getProfessorName() + " 교수님 연구실");
                assertThat(laboratory.nameSource()).isEqualTo(LaboratoryNameSource.GENERATED);
            });

        PromotionResult second = promotionService.promote(source.getId());

        assertThat(second.candidateCount()).isZero();
        assertThat(second.createdCount()).isZero();
        assertThat(second.failures()).isEmpty();
        assertThat(countProfessorByEmail(official.getEmail())).isOne();
        assertThat(countProfessorByCandidateId(generated.getId())).isOne();
    }

    @Test
    void sameProfessorAcrossDepartmentsSharesCanonicalTargetsAndAffiliationsIdempotently() {
        CrawlSource firstSource = newSource();
        CrawlSource secondSource = newSource();
        String suffix = uniqueSuffix();
        String professorName = "겸임" + suffix;
        String email = "affiliated-" + suffix + "@sejong.ac.kr";
        String laboratoryName = "겸임 통합 연구실 " + suffix;
        ProfessorCrawlCandidate first = approvedCandidate(
            firstSource,
            data(professorName, email, laboratoryName)
        );
        ProfessorCrawlCandidate second = approvedCandidate(
            secondSource,
            data(professorName, email, laboratoryName)
        );

        PromotionResult firstSourceRun = promotionService.promote(firstSource.getId());
        PromotionResult secondSourceRun = promotionService.promote(secondSource.getId());

        assertThat(firstSourceRun.candidateCount()).isOne();
        assertThat(firstSourceRun.createdCount()).isOne();
        assertThat(firstSourceRun.failures()).isEmpty();
        assertThat(secondSourceRun.candidateCount()).isOne();
        assertThat(secondSourceRun.failures()).isEmpty();
        assertThat(secondSourceRun.updatedCount()).isOne();
        assertThat(countProfessorByEmail(email)).isOne();
        Map<String, Object> firstPromotion = promotionColumns(first.getId());
        Map<String, Object> secondPromotion = promotionColumns(second.getId());
        assertThat(secondPromotion.get("promoted_professor_id"))
            .isEqualTo(firstPromotion.get("promoted_professor_id"));
        assertThat(secondPromotion.get("promoted_laboratory_id"))
            .isEqualTo(firstPromotion.get("promoted_laboratory_id"));

        Long professorId = ((Number) firstPromotion.get("promoted_professor_id")).longValue();
        Long laboratoryId = ((Number) firstPromotion.get("promoted_laboratory_id")).longValue();
        assertThat(countProfessorAffiliations(professorId)).isEqualTo(2L);
        assertThat(countLaboratoryAffiliations(laboratoryId)).isEqualTo(2L);

        PromotionResult firstIdempotentRun = promotionService.promote(firstSource.getId());
        PromotionResult secondIdempotentRun = promotionService.promote(secondSource.getId());

        assertThat(firstIdempotentRun.candidateCount()).isZero();
        assertThat(firstIdempotentRun.failures()).isEmpty();
        assertThat(secondIdempotentRun.candidateCount()).isZero();
        assertThat(secondIdempotentRun.failures()).isEmpty();
        assertThat(countProfessorByEmail(email)).isOne();
        assertThat(countProfessorAffiliations(professorId)).isEqualTo(2L);
        assertThat(countLaboratoryAffiliations(laboratoryId)).isEqualTo(2L);
    }

    @Test
    void departmentSpecificPositionsAreStoredWithoutOverwritingCanonicalPosition() {
        CrawlSource primarySource = newSource();
        CrawlSource affiliatedSource = newSource();
        String suffix = uniqueSuffix();
        String professorName = "직위겸임" + suffix;
        String email = "position-affiliation-" + suffix + "@sejong.ac.kr";
        String laboratoryName = "직위 겸임 연구실 " + suffix;
        approvedCandidate(
            primarySource,
            data(professorName, "교수", email, laboratoryName)
        );
        approvedCandidate(
            affiliatedSource,
            data(professorName, "겸임교수", email, laboratoryName)
        );

        PromotionResult primaryResult = promotionService.promote(primarySource.getId());
        PromotionResult affiliatedResult = promotionService.promote(affiliatedSource.getId());

        assertThat(primaryResult.createdCount()).isOne();
        assertThat(primaryResult.failures()).isEmpty();
        assertThat(affiliatedResult.updatedCount()).isOne();
        assertThat(affiliatedResult.failures()).isEmpty();
        Long professorId = jdbcTemplate.queryForObject(
            "SELECT id FROM professor WHERE email = ?",
            Long.class,
            email
        );
        assertThat(jdbcTemplate.queryForObject(
            "SELECT position FROM professor WHERE id = ?",
            String.class,
            professorId
        )).isEqualTo("교수");
        assertThat(professorAffiliationPosition(
            professorId,
            primarySource.getDepartment().getId()
        )).isEqualTo("교수");
        assertThat(professorAffiliationPosition(
            professorId,
            affiliatedSource.getDepartment().getId()
        )).isEqualTo("겸임교수");
    }

    @Test
    void officialLaboratoryProfileReplacesGeneratedProfileWhenCanonicalProfessorIsShared() {
        CrawlSource generatedSource = newSource();
        CrawlSource officialSource = newSource();
        String suffix = uniqueSuffix();
        String professorName = "공식우선" + suffix;
        String email = "official-priority-" + suffix + "@sejong.ac.kr";
        ProfessorCrawlCandidate generated = approvedCandidate(
            generatedSource,
            data(professorName, email, null)
        );
        ProfessorCrawlCandidate official = approvedCandidate(
            officialSource,
            data(professorName, email, "공식 통합 연구실 " + suffix)
        );

        PromotionResult generatedResult = promotionService.promote(generatedSource.getId());
        PromotionResult officialResult = promotionService.promote(officialSource.getId());

        assertThat(generatedResult.createdCount()).isOne();
        assertThat(officialResult.updatedCount()).isOne();
        assertThat(generatedResult.failures()).isEmpty();
        assertThat(officialResult.failures()).isEmpty();
        assertLaboratory(
            official.getId(),
            "공식 통합 연구실 " + suffix,
            "OFFICIAL",
            "UNKNOWN"
        );
        assertThat(laboratoryId(official.getId())).isEqualTo(laboratoryId(generated.getId()));
        assertThat(countLaboratoryAffiliations(laboratoryId(official.getId()))).isEqualTo(2L);
    }

    @Test
    void generatedProfileDoesNotOverwriteExistingOfficialLaboratoryProfile() {
        CrawlSource officialSource = newSource();
        CrawlSource generatedSource = newSource();
        String suffix = uniqueSuffix();
        String professorName = "공식보존" + suffix;
        String email = "official-preserved-" + suffix + "@sejong.ac.kr";
        String officialLaboratoryName = "보존할 공식 연구실 " + suffix;
        ProfessorCrawlCandidate official = approvedCandidate(
            officialSource,
            data(professorName, email, officialLaboratoryName)
        );
        ProfessorCrawlCandidate generated = approvedCandidate(
            generatedSource,
            data(professorName, email, null)
        );

        PromotionResult officialResult = promotionService.promote(officialSource.getId());
        PromotionResult generatedResult = promotionService.promote(generatedSource.getId());

        assertThat(officialResult.createdCount()).isOne();
        assertThat(generatedResult.updatedCount()).isOne();
        assertThat(officialResult.failures()).isEmpty();
        assertThat(generatedResult.failures()).isEmpty();
        assertLaboratory(
            generated.getId(),
            officialLaboratoryName,
            "OFFICIAL",
            "UNKNOWN"
        );
        assertThat(laboratoryId(generated.getId())).isEqualTo(laboratoryId(official.getId()));
        assertThat(countLaboratoryAffiliations(laboratoryId(generated.getId()))).isEqualTo(2L);
    }

    @Test
    void reapprovedPrimaryCandidateDoesNotDowngradeSharedOfficialLaboratoryName() {
        CrawlSource primarySource = newSource();
        CrawlSource officialSource = newSource();
        String suffix = uniqueSuffix();
        String professorName = "재검수보존" + suffix;
        String email = "refresh-preserved-" + suffix + "@sejong.ac.kr";
        String officialLaboratoryName = "재검수 보존 연구실 " + suffix;
        ProfessorCrawlCandidate primary = approvedCandidate(
            primarySource,
            data(professorName, email, null)
        );
        ProfessorCrawlCandidate official = approvedCandidate(
            officialSource,
            data(professorName, email, officialLaboratoryName)
        );
        assertThat(promotionService.promote(primarySource.getId()).createdCount()).isOne();
        assertThat(promotionService.promote(officialSource.getId()).updatedCount()).isOne();

        ProfessorCrawlCandidate reviewedAgain = candidateRepository.findById(primary.getId())
            .orElseThrow();
        LocalDateTime recrawledAt = LocalDateTime.now().plusMinutes(1);
        reviewedAgain.refreshFromCrawl(
            new ProfessorCrawlData(
                professorName,
                "교수",
                email,
                null,
                "대표 학과에서 갱신한 연구 소개",
                "https://example.com/refresh-preserved-" + suffix
            ),
            CrawlSourceProvenance.from(primarySource),
            recrawledAt
        );
        reviewedAgain.approve("reviewer", "대표 학과 재검수", recrawledAt.plusMinutes(1));
        candidateRepository.saveAndFlush(reviewedAgain);

        PromotionResult refreshed = promotionService.promote(primarySource.getId());

        assertThat(refreshed.updatedCount()).isOne();
        assertThat(refreshed.failures()).isEmpty();
        assertLaboratory(
            official.getId(),
            officialLaboratoryName,
            "OFFICIAL",
            "UNKNOWN"
        );
    }

    @Test
    void sameEmailWithDifferentProfessorIdentityIsRejectedWithoutChangingCanonicalData() {
        CrawlSource canonicalSource = newSource();
        CrawlSource conflictingSource = newSource();
        String suffix = uniqueSuffix();
        String email = "identity-conflict-" + suffix + "@sejong.ac.kr";
        ProfessorCrawlCandidate canonical = approvedCandidate(
            canonicalSource,
            data("기존교수" + suffix, email, "기존 연구실 " + suffix)
        );
        ProfessorCrawlCandidate conflict = approvedCandidate(
            conflictingSource,
            data("다른교수" + suffix, email, "다른 연구실 " + suffix)
        );
        assertThat(promotionService.promote(canonicalSource.getId()).createdCount()).isOne();

        PromotionResult conflictResult = promotionService.promote(conflictingSource.getId());

        assertThat(conflictResult.createdCount()).isZero();
        assertThat(conflictResult.updatedCount()).isZero();
        assertThat(conflictResult.failedCount()).isOne();
        assertThat(conflictResult.failures().getFirst().candidateId()).isEqualTo(conflict.getId());
        assertThat(conflictResult.failures().getFirst().reason())
            .isEqualTo("PROFESSOR_IDENTITY_CONFLICT");
        assertThat(promotionColumns(conflict.getId()).get("promoted_at")).isNull();
        assertThat(countProfessorByEmail(email)).isOne();
        assertThat(laboratoryId(canonical.getId())).isNotNull();
        assertThat(countProfessorAffiliations(
            ((Number) promotionColumns(canonical.getId()).get("promoted_professor_id")).longValue()
        )).isOne();
        assertThat(countLaboratoryAffiliations(laboratoryId(canonical.getId()))).isOne();
    }

    @Test
    void reappliedReviewUpdatesOwnedFieldsButPreservesManualRecruitmentStatus() {
        CrawlSource source = newSource();
        String suffix = uniqueSuffix();
        ProfessorCrawlCandidate candidate = approvedCandidate(
            source,
            data("갱신" + suffix, "update-" + suffix + "@sejong.ac.kr", null)
        );
        assertThat(promotionService.promote(source.getId()).createdCount()).isOne();
        Long laboratoryId = laboratoryId(candidate.getId());
        jdbcTemplate.update(
            "UPDATE laboratory SET recruitment_status = 'RECRUITING' WHERE id = ?",
            laboratoryId
        );

        ProfessorCrawlCandidate reviewedAgain = candidateRepository.findById(candidate.getId())
            .orElseThrow();
        LocalDateTime sameReviewedAt = reviewedAgain.getReviewedAt();
        LocalDateTime recrawledAt = LocalDateTime.now().plusMinutes(1);
        reviewedAgain.refreshFromCrawl(
            new ProfessorCrawlData(
                "갱신완료" + suffix,
                "부교수",
                "updated-" + suffix + "@sejong.ac.kr",
                "공식 갱신 연구실",
                "갱신된 연구 소개",
                "https://example.com/updated-" + suffix
            ),
            CrawlSourceProvenance.from(source),
            recrawledAt
        );
        reviewedAgain.approve("reviewer", "재검수", sameReviewedAt);
        candidateRepository.saveAndFlush(reviewedAgain);

        PromotionResult result = promotionService.promote(source.getId());

        assertThat(result.updatedCount()).isOne();
        assertThat(result.createdCount()).isZero();
        assertThat(result.failures()).isEmpty();
        Map<String, Object> promoted = jdbcTemplate.queryForMap("""
            SELECT p.name AS professor_name, p.position, p.email,
                   l.name AS laboratory_name, l.name_source, l.description,
                   l.website_url, l.recruitment_status
            FROM professor_crawl_candidate c
            JOIN professor p ON p.id = c.promoted_professor_id
            JOIN laboratory l ON l.id = c.promoted_laboratory_id
            WHERE c.id = ?
            """, candidate.getId());
        assertThat(promoted)
            .containsEntry("professor_name", "갱신완료" + suffix)
            .containsEntry("position", "부교수")
            .containsEntry("email", "updated-" + suffix + "@sejong.ac.kr")
            .containsEntry("laboratory_name", "공식 갱신 연구실")
            .containsEntry("name_source", "OFFICIAL")
            .containsEntry("description", "갱신된 연구 소개")
            .containsEntry("website_url", "https://example.com/updated-" + suffix)
            .containsEntry("recruitment_status", "RECRUITING");
    }

    @Test
    void reappliedReviewPreservesManuallyVerifiedWebsiteWhenCrawlLosesTheUrl() {
        CrawlSource source = newSource();
        String suffix = uniqueSuffix();
        String professorName = "수동URL보존" + suffix;
        String email = "manual-url-preserved-" + suffix + "@sejong.ac.kr";
        ProfessorCrawlCandidate candidate = approvedCandidate(
            source,
            data(professorName, email, null)
        );
        assertThat(promotionService.promote(source.getId()).createdCount()).isOne();
        Long laboratoryId = laboratoryId(candidate.getId());
        var laboratory = laboratoryRepository.findById(laboratoryId).orElseThrow();
        assertThat(laboratory.getWebsiteUrlSource()).isEqualTo(WebsiteUrlSource.CRAWLED);
        laboratory.updateWebsiteManually("https://sdl.sejong.ac.kr/");
        laboratoryRepository.saveAndFlush(laboratory);

        ProfessorCrawlCandidate reviewedAgain = candidateRepository.findById(candidate.getId())
            .orElseThrow();
        LocalDateTime recrawledAt = LocalDateTime.now().plusMinutes(1);
        reviewedAgain.refreshFromCrawl(
            new ProfessorCrawlData(
                professorName,
                "교수",
                email,
                null,
                "재크롤링으로 갱신된 연구 소개",
                null
            ),
            CrawlSourceProvenance.from(source),
            recrawledAt
        );
        reviewedAgain.approve("검수자", "URL 누락 재검수", recrawledAt.plusMinutes(1));
        candidateRepository.saveAndFlush(reviewedAgain);

        PromotionResult result = promotionService.promote(source.getId());

        assertThat(result.updatedCount()).isOne();
        assertThat(result.failures()).isEmpty();
        assertThat(jdbcTemplate.queryForMap("""
            SELECT website_url, website_url_source, description
            FROM laboratory
            WHERE id = ?
            """, laboratoryId))
            .containsEntry("website_url", "https://sdl.sejong.ac.kr/")
            .containsEntry("website_url_source", "MANUAL")
            .containsEntry("description", "재크롤링으로 갱신된 연구 소개");
    }

    @Test
    void conflictRollsBackThatCandidateWithoutStoppingOtherCandidates() {
        CrawlSource source = newSource();
        String suffix = uniqueSuffix();
        String duplicateEmail = "duplicate-" + suffix + "@sejong.ac.kr";
        professorRepository.saveAndFlush(new Professor(
            source.getDepartment(),
            "기존" + suffix,
            "교수",
            duplicateEmail
        ));
        ProfessorCrawlCandidate conflict = approvedCandidate(
            source,
            data("충돌" + suffix, duplicateEmail, null)
        );
        ProfessorCrawlCandidate valid = approvedCandidate(
            source,
            data("정상" + suffix, "valid-" + suffix + "@sejong.ac.kr", null)
        );

        PromotionResult result = promotionService.promote(source.getId());

        assertThat(result.candidateCount()).isEqualTo(2);
        assertThat(result.createdCount()).isOne();
        assertThat(result.failedCount()).isOne();
        assertThat(result.failures().getFirst().candidateId()).isEqualTo(conflict.getId());
        assertThat(result.failures().getFirst().reason()).isEqualTo("PROFESSOR_IDENTITY_CONFLICT");
        assertThat(promotionColumns(conflict.getId()).get("promoted_at")).isNull();
        assertThat(promotionColumns(valid.getId()).get("promoted_at")).isNotNull();
        assertThat(countProfessorByEmail(duplicateEmail)).isOne();
    }

    @Test
    void homonymousProfessorsWithDistinctIdentitiesAndOfficialLaboratoriesAreAllowed() {
        CrawlSource source = newSource();
        String suffix = uniqueSuffix();
        approvedCandidate(
            source,
            data("동명이인" + suffix, "same-a-" + suffix + "@sejong.ac.kr", "공식 연구실 A")
        );
        approvedCandidate(
            source,
            data("동명이인" + suffix, "same-b-" + suffix + "@sejong.ac.kr", "공식 연구실 B")
        );

        PromotionResult result = promotionService.promote(source.getId());

        assertThat(result.createdCount()).isEqualTo(2);
        assertThat(result.failures()).isEmpty();
        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM professor
            WHERE department_id = ?
              AND name = ?
            """, Long.class, source.getDepartment().getId(), "동명이인" + suffix))
            .isEqualTo(2L);
    }

    @Test
    void concurrentRunsDoNotCreateDuplicateMainData() throws Exception {
        CrawlSource source = newSource();
        String suffix = uniqueSuffix();
        ProfessorCrawlCandidate candidate = approvedCandidate(
            source,
            data("동시" + suffix, "concurrent-" + suffix + "@sejong.ac.kr", null)
        );
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<PromotionResult> first = executor.submit(
                () -> promoteAfterSignal(source.getId(), ready, start)
            );
            Future<PromotionResult> second = executor.submit(
                () -> promoteAfterSignal(source.getId(), ready, start)
            );
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            PromotionResult firstResult = first.get(10, TimeUnit.SECONDS);
            PromotionResult secondResult = second.get(10, TimeUnit.SECONDS);
            assertThat(firstResult.createdCount() + secondResult.createdCount()).isOne();
            assertThat(firstResult.failedCount() + secondResult.failedCount()).isZero();
        }

        assertThat(countProfessorByEmail(candidate.getEmail())).isOne();
        assertThat(promotionColumns(candidate.getId()).get("promoted_at")).isNotNull();
    }

    @Test
    void aPurgedLaboratoryIsNotSilentlyRecreatedByAReapprovedCandidate() {
        CrawlSource source = newSource();
        String suffix = uniqueSuffix();
        ProfessorCrawlCandidate candidate = approvedCandidate(
            source,
            data("삭제이력" + suffix, "purged-" + suffix + "@sejong.ac.kr", null)
        );
        assertThat(promotionService.promote(source.getId()).createdCount()).isOne();
        Long laboratoryId = laboratoryId(candidate.getId());
        jdbcTemplate.update("DELETE FROM laboratory WHERE id = ?", laboratoryId);

        ProfessorCrawlCandidate reviewedAgain = candidateRepository.findById(candidate.getId())
            .orElseThrow();
        LocalDateTime recrawledAt = LocalDateTime.now().plusMinutes(1);
        reviewedAgain.refreshFromCrawl(
            data("삭제이력" + suffix, "purged-" + suffix + "@sejong.ac.kr", "재검수 연구실"),
            CrawlSourceProvenance.from(source),
            recrawledAt
        );
        reviewedAgain.approve("reviewer", "삭제 후 재검수", recrawledAt.plusMinutes(1));
        candidateRepository.saveAndFlush(reviewedAgain);

        PromotionResult result = promotionService.promote(source.getId());

        assertThat(result.createdCount()).isZero();
        assertThat(result.updatedCount()).isZero();
        assertThat(result.failedCount()).isOne();
        assertThat(result.failures().getFirst().reason()).isEqualTo("PROMOTED_ENTITY_WAS_REMOVED");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM laboratory WHERE professor_id = ?",
            Long.class,
            promotionColumns(candidate.getId()).get("promoted_professor_id")
        )).isZero();
    }

    @Test
    void partialManualPromotionStateFailsWithoutCreatingDuplicateMainData() {
        CrawlSource source = newSource();
        String suffix = uniqueSuffix();
        ProfessorCrawlCandidate candidate = approvedCandidate(
            source,
            data("부분상태" + suffix, "partial-" + suffix + "@sejong.ac.kr", null)
        );
        Professor manuallyLinked = professorRepository.saveAndFlush(new Professor(
            source.getDepartment(),
            "수동연결" + suffix,
            "교수",
            "manual-" + suffix + "@sejong.ac.kr"
        ));
        jdbcTemplate.update("""
            UPDATE professor_crawl_candidate
            SET promoted_professor_id = ?
            WHERE id = ?
            """, manuallyLinked.getId(), candidate.getId());

        PromotionResult result = promotionService.promote(source.getId());

        assertThat(result.createdCount()).isZero();
        assertThat(result.failedCount()).isOne();
        assertThat(result.failures().getFirst().reason())
            .isEqualTo("INVALID_CANDIDATE_PROMOTION_STATE");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM professor WHERE department_id = ?",
            Long.class,
            source.getDepartment().getId()
        )).isOne();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM laboratory WHERE department_id = ?",
            Long.class,
            source.getDepartment().getId()
        )).isZero();
    }

    private ProfessorCrawlCandidate approvedCandidate(
        CrawlSource source,
        ProfessorCrawlData data
    ) {
        ProfessorCrawlCandidate candidate = new ProfessorCrawlCandidate(
            source,
            data,
            CrawlSourceProvenance.from(source),
            LocalDateTime.now()
        );
        candidate.approve("reviewer", "검수 완료", LocalDateTime.now());
        return candidateRepository.saveAndFlush(candidate);
    }

    private ProfessorCrawlData data(String name, String email, String laboratoryName) {
        return data(name, "교수", email, laboratoryName);
    }

    private ProfessorCrawlData data(
        String name,
        String position,
        String email,
        String laboratoryName
    ) {
        return new ProfessorCrawlData(
            name,
            position,
            email,
            laboratoryName,
            "연구 소개",
            "https://example.com/" + name
        );
    }

    private CrawlSource newSource() {
        String suffix = uniqueSuffix();
        College college = collegeRepository.save(new College("승격 테스트 대학 " + suffix));
        Department department = departmentRepository.save(
            new Department(college, "승격 테스트 학과 " + suffix)
        );
        return sourceRepository.saveAndFlush(new CrawlSource(
            department,
            "승격 테스트 교수진 " + suffix,
            "https://example.com/sources/" + suffix,
            CrawlParserType.SEJONG_STANDARD
        ));
    }

    private PromotionResult promoteAfterSignal(
        Long sourceId,
        CountDownLatch ready,
        CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("CONCURRENT_PROMOTION_START_TIMEOUT");
        }
        return promotionService.promote(sourceId);
    }

    private void assertLaboratory(
        Long candidateId,
        String name,
        String nameSource,
        String recruitmentStatus
    ) {
        Map<String, Object> laboratory = jdbcTemplate.queryForMap("""
            SELECT l.name, l.name_source, l.recruitment_status
            FROM professor_crawl_candidate c
            JOIN laboratory l ON l.id = c.promoted_laboratory_id
            WHERE c.id = ?
            """, candidateId);
        assertThat(laboratory)
            .containsEntry("name", name)
            .containsEntry("name_source", nameSource)
            .containsEntry("recruitment_status", recruitmentStatus);
    }

    private Map<String, Object> promotionColumns(Long candidateId) {
        return jdbcTemplate.queryForMap("""
            SELECT promoted_professor_id, promoted_laboratory_id, promoted_at, promoted_reviewed_at
            FROM professor_crawl_candidate
            WHERE id = ?
            """, candidateId);
    }

    private Long laboratoryId(Long candidateId) {
        return jdbcTemplate.queryForObject(
            "SELECT promoted_laboratory_id FROM professor_crawl_candidate WHERE id = ?",
            Long.class,
            candidateId
        );
    }

    private long countProfessorByEmail(String email) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM professor WHERE email = ?",
            Long.class,
            email
        );
    }

    private long countProfessorByCandidateId(Long candidateId) {
        return jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM professor_crawl_candidate c
            JOIN professor p ON p.id = c.promoted_professor_id
            WHERE c.id = ?
              AND p.email IS NULL
            """, Long.class, candidateId);
    }

    private long countProfessorAffiliations(Long professorId) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM professor_department WHERE professor_id = ?",
            Long.class,
            professorId
        );
    }

    private long countLaboratoryAffiliations(Long laboratoryId) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM laboratory_department WHERE laboratory_id = ?",
            Long.class,
            laboratoryId
        );
    }

    private String professorAffiliationPosition(Long professorId, Long departmentId) {
        return jdbcTemplate.queryForObject(
            """
                SELECT position
                FROM professor_department
                WHERE professor_id = ?
                  AND department_id = ?
                """,
            String.class,
            professorId,
            departmentId
        );
    }

    private String uniqueSuffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }
}

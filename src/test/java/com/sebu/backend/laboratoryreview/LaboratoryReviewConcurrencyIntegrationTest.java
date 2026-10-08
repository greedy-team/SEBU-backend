package com.sebu.backend.laboratoryreview;

import com.sebu.backend.college.domain.College;
import com.sebu.backend.college.repository.CollegeRepository;
import com.sebu.backend.department.domain.Department;
import com.sebu.backend.department.repository.DepartmentRepository;
import com.sebu.backend.laboratory.domain.Laboratory;
import com.sebu.backend.laboratory.domain.RecruitmentStatus;
import com.sebu.backend.laboratory.repository.LaboratoryRepository;
import com.sebu.backend.laboratoryreview.domain.Atmosphere;
import com.sebu.backend.laboratoryreview.domain.Compensation;
import com.sebu.backend.laboratoryreview.domain.LaboratoryReviewCategory;
import com.sebu.backend.laboratoryreview.domain.ParticipationTerm;
import com.sebu.backend.laboratoryreview.domain.ResearchIntensity;
import com.sebu.backend.laboratoryreview.dto.LaboratoryReviewCreateRequest;
import com.sebu.backend.laboratoryreview.dto.LaboratoryReviewCreateResponse;
import com.sebu.backend.laboratoryreview.exception.LaboratoryReviewAlreadyExistsException;
import com.sebu.backend.laboratoryreview.service.LaboratoryReviewService;
import com.sebu.backend.professor.domain.Professor;
import com.sebu.backend.professor.repository.ProfessorRepository;
import com.sebu.backend.user.domain.AppUser;
import com.sebu.backend.user.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class LaboratoryReviewConcurrencyIntegrationTest {

    @Autowired
    LaboratoryReviewService laboratoryReviewService;

    @Autowired
    CollegeRepository collegeRepository;

    @Autowired
    DepartmentRepository departmentRepository;

    @Autowired
    ProfessorRepository professorRepository;

    @Autowired
    LaboratoryRepository laboratoryRepository;

    @Autowired
    AppUserRepository appUserRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void 동시_중복_후기_작성은_하나만_저장하고_하나는_중복_오류를_반환한다()
            throws Exception {
        TestFixture fixture = createFixture();
        LaboratoryReviewCreateRequest request = createRequest();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Callable<Object> createReview = () -> {
            ready.countDown();
            start.await(5, TimeUnit.SECONDS);

            try {
                return laboratoryReviewService.createReview(
                        fixture.laboratoryId(),
                        fixture.userId(),
                        request
                );
            } catch (LaboratoryReviewAlreadyExistsException exception) {
                return exception;
            }
        };

        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            List<Future<Object>> futures = List.of(
                    executor.submit(createReview),
                    executor.submit(createReview)
            );

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Object> results = List.of(
                    futures.get(0).get(10, TimeUnit.SECONDS),
                    futures.get(1).get(10, TimeUnit.SECONDS)
            );

            assertThat(results)
                    .filteredOn(LaboratoryReviewCreateResponse.class::isInstance)
                    .hasSize(1);
            assertThat(results)
                    .filteredOn(LaboratoryReviewAlreadyExistsException.class::isInstance)
                    .hasSize(1);
        } finally {
            start.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }

        Integer activeReviewCount = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM laboratory_review
            WHERE laboratory_id = ?
              AND author_id = ?
              AND deleted_at IS NULL
            """, Integer.class, fixture.laboratoryId(), fixture.userId());

        assertThat(activeReviewCount).isOne();
    }

    private TestFixture createFixture() {
        String suffix = UUID.randomUUID().toString();
        College college = collegeRepository.save(
                new College("동시성 후기 대학-" + suffix)
        );
        Department department = departmentRepository.save(
                new Department(college, "동시성 후기 학과-" + suffix)
        );
        Professor professor = professorRepository.save(
                new Professor(
                        department,
                        "동시성 후기 교수-" + suffix,
                        "review-" + suffix + "@test.com"
                )
        );
        Laboratory laboratory = laboratoryRepository.saveAndFlush(
                new Laboratory(
                        professor,
                        department,
                        "동시성 후기 연구실-" + suffix,
                        "https://example.com/" + suffix,
                        RecruitmentStatus.RECRUITING
                )
        );
        AppUser user = appUserRepository.saveAndFlush(
                new AppUser("reviewer-" + suffix + "@test.com")
        );

        return new TestFixture(laboratory.getId(), user.getId());
    }

    private LaboratoryReviewCreateRequest createRequest() {
        return new LaboratoryReviewCreateRequest(
                LaboratoryReviewCategory.RESEARCH_ENVIRONMENT,
                ResearchIntensity.LOW,
                Compensation.SUFFICIENT,
                Atmosphere.COOPERATIVE,
                Set.of(),
                "동시 요청에서도 활성 후기가 하나만 저장되는지 확인하는 후기입니다.",
                2026,
                ParticipationTerm.FIRST_SEMESTER
        );
    }

    private record TestFixture(Long laboratoryId, Long userId) {
    }
}

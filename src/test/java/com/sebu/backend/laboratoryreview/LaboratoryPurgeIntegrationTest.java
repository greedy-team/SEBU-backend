package com.sebu.backend.laboratoryreview;

import com.sebu.backend.college.domain.College;
import com.sebu.backend.department.domain.Department;
import com.sebu.backend.laboratory.domain.Laboratory;
import com.sebu.backend.laboratory.domain.RecruitmentStatus;
import com.sebu.backend.laboratory.repository.LaboratoryRepository;
import com.sebu.backend.laboratory.service.LaboratoryPurgeService;
import com.sebu.backend.laboratoryreview.domain.Atmosphere;
import com.sebu.backend.laboratoryreview.domain.Compensation;
import com.sebu.backend.laboratoryreview.domain.LaboratoryReview;
import com.sebu.backend.laboratoryreview.domain.LaboratoryReviewCategory;
import com.sebu.backend.laboratoryreview.domain.LaboratoryReviewTag;
import com.sebu.backend.laboratoryreview.domain.ParticipationTerm;
import com.sebu.backend.laboratoryreview.domain.ResearchIntensity;
import com.sebu.backend.professor.domain.Professor;
import com.sebu.backend.user.domain.AppUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class LaboratoryPurgeIntegrationTest {

    @Autowired
    LaboratoryPurgeService laboratoryPurgeService;

    @Autowired
    LaboratoryRepository laboratoryRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @PersistenceContext
    EntityManager entityManager;

    @Test
    void purgesLaboratoryWithReviewAndTagsAfterRetentionPeriod() {
        // given
        College college = new College("테스트 단과대학");
        entityManager.persist(college);

        Department department = new Department(
                college,
                "테스트학과"
        );
        entityManager.persist(department);

        Professor professor = new Professor(
                department,
                "김교수",
                "purge-professor@test.com"
        );
        entityManager.persist(professor);

        Laboratory laboratory = new Laboratory(
                professor,
                department,
                "삭제 대상 연구실",
                "https://example.com",
                RecruitmentStatus.RECRUITING
        );
        entityManager.persist(laboratory);

        AppUser user = new AppUser(
                "purge-reviewer@test.com"
        );
        entityManager.persist(user);

        LaboratoryReview review = new LaboratoryReview(
                laboratory,
                user,
                LaboratoryReviewCategory.RESEARCH_ENVIRONMENT,
                ResearchIntensity.LOW,
                Compensation.SUFFICIENT,
                Atmosphere.COOPERATIVE,
                Set.of(
                        LaboratoryReviewTag.PROJECT_OPPORTUNITY,
                        LaboratoryReviewTag.ACTIVE_FEEDBACK
                ),
                "프로젝트 참여 기회가 많고 피드백을 자주 받을 수 있었습니다.",
                2026,
                ParticipationTerm.FIRST_SEMESTER
        );
        entityManager.persist(review);

        entityManager.flush();

        Long laboratoryId = laboratory.getId();
        Long reviewId = review.getId();

        // 연구실 soft delete
        laboratory.softDelete();
        entityManager.flush();

        // 보존 기간보다 충분히 지난 시점에서 purge 실행
        LocalDateTime purgeTime =
                LocalDateTime.now().plusYears(1);

        // when
        int deletedCount =
                laboratoryPurgeService.purgeExpiredLaboratories(
                        purgeTime
                );

        entityManager.flush();
        entityManager.clear();

        // then
        assertThat(deletedCount).isEqualTo(1);

        assertThat(
                laboratoryRepository.findById(laboratoryId)
        ).isEmpty();

        Integer reviewCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM laboratory_review
                WHERE id = ?
                """,
                Integer.class,
                reviewId
        );

        assertThat(reviewCount).isZero();

        Integer tagCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM laboratory_review_tag
                WHERE review_id = ?
                """,
                Integer.class,
                reviewId
        );

        assertThat(tagCount).isZero();
    }
}

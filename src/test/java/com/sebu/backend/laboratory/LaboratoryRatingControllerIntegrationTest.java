package com.sebu.backend.laboratory;

import com.sebu.backend.college.domain.College;
import com.sebu.backend.department.domain.Department;
import com.sebu.backend.global.auth.CurrentUserProvider;
import com.sebu.backend.laboratory.domain.Laboratory;
import com.sebu.backend.laboratory.domain.RecruitmentStatus;
import com.sebu.backend.laboratoryreview.domain.Atmosphere;
import com.sebu.backend.laboratoryreview.domain.Compensation;
import com.sebu.backend.laboratoryreview.domain.LaboratoryReview;
import com.sebu.backend.laboratoryreview.domain.LaboratoryReviewCategory;
import com.sebu.backend.laboratoryreview.domain.ParticipationTerm;
import com.sebu.backend.laboratoryreview.domain.ResearchIntensity;
import com.sebu.backend.professor.domain.Professor;
import com.sebu.backend.user.domain.AppUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LaboratoryRatingControllerIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    CurrentUserProvider currentUserProvider;

    @PersistenceContext
    EntityManager entityManager;

    @Test
    void returnsReviewCountSortedLaboratoriesWithPagination() throws Exception {
        when(currentUserProvider.currentUserId())
                .thenReturn(Optional.empty());

        TestFixture fixture = createReviewCountFixture();

        mockMvc.perform(
                        get("/api/v1/laboratories")
                                .param("sort", "REVIEW_COUNT_DESC")
                                .param("page", "0")
                                .param("size", "2")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(
                        jsonPath("$.data.laboratories[0].id")
                                .value(fixture.twoReviewsLaboratoryId())
                )
                .andExpect(
                        jsonPath("$.data.laboratories[0].reviewCount")
                                .value(2)
                )
                .andExpect(
                        jsonPath("$.data.laboratories[1].id")
                                .value(fixture.newerOneReviewLaboratoryId())
                )
                .andExpect(
                        jsonPath("$.data.laboratories[1].reviewCount")
                                .value(1)
                )
                .andExpect(
                        jsonPath("$.data.laboratories[0].affiliations[0].college.id")
                                .value(fixture.collegeId())
                )
                .andExpect(
                        jsonPath("$.data.laboratories[0].researchFieldCategories")
                                .isArray()
                )
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(2))
                .andExpect(jsonPath("$.data.totalElements").value(4))
                .andExpect(jsonPath("$.data.hasNext").value(true))
                .andExpect(jsonPath("$.error").doesNotExist());

        mockMvc.perform(
                        get("/api/v1/laboratories")
                                .param("sort", "REVIEW_COUNT_DESC")
                                .param("page", "1")
                                .param("size", "2")
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.data.laboratories[0].id")
                                .value(fixture.olderOneReviewLaboratoryId())
                )
                .andExpect(
                        jsonPath("$.data.laboratories[0].reviewCount")
                                .value(1)
                )
                .andExpect(
                        jsonPath("$.data.laboratories[1].id")
                                .value(fixture.zeroReviewLaboratoryId())
                )
                .andExpect(
                        jsonPath("$.data.laboratories[1].reviewCount")
                                .value(0)
                )
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(2))
                .andExpect(jsonPath("$.data.totalElements").value(4))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    void keepsExistingLaboratoryListApiWorkingWithoutSort() throws Exception {
        when(currentUserProvider.currentUserId())
                .thenReturn(Optional.empty());

        mockMvc.perform(
                        get("/api/v1/laboratories")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.laboratories").isArray())
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    private TestFixture createReviewCountFixture() {
        entityManager.createNativeQuery("""
                UPDATE laboratory
                SET deleted_at = CURRENT_TIMESTAMP
                WHERE deleted_at IS NULL
                """).executeUpdate();
        entityManager.clear();

        String suffix = UUID.randomUUID().toString();
        College college = new College("후기 정렬 대학-" + suffix);
        entityManager.persist(college);

        Department department = new Department(
                college,
                "후기 정렬 학과-" + suffix
        );
        entityManager.persist(department);

        Laboratory twoReviewsLaboratory = createLaboratory(
                department,
                "후기 2개 연구실-" + suffix,
                1
        );
        Laboratory olderOneReviewLaboratory = createLaboratory(
                department,
                "후기 1개 연구실 A-" + suffix,
                2
        );
        Laboratory newerOneReviewLaboratory = createLaboratory(
                department,
                "후기 1개 연구실 B-" + suffix,
                3
        );
        Laboratory zeroReviewLaboratory = createLaboratory(
                department,
                "후기 0개 연구실-" + suffix,
                4
        );

        createReview(twoReviewsLaboratory, suffix, 1, false);
        createReview(twoReviewsLaboratory, suffix, 2, false);
        createReview(olderOneReviewLaboratory, suffix, 3, false);
        createReview(olderOneReviewLaboratory, suffix, 4, true);
        createReview(newerOneReviewLaboratory, suffix, 5, false);

        entityManager.flush();
        entityManager.clear();

        return new TestFixture(
                college.getId(),
                twoReviewsLaboratory.getId(),
                olderOneReviewLaboratory.getId(),
                newerOneReviewLaboratory.getId(),
                zeroReviewLaboratory.getId()
        );
    }

    private Laboratory createLaboratory(
            Department department,
            String name,
            int sequence
    ) {
        Professor professor = new Professor(
                department,
                "후기 정렬 교수 " + sequence,
                "sort-professor-" + sequence + "-" + UUID.randomUUID()
                        + "@test.com"
        );
        entityManager.persist(professor);

        Laboratory laboratory = new Laboratory(
                professor,
                department,
                name,
                "https://example.com/" + UUID.randomUUID(),
                RecruitmentStatus.RECRUITING
        );
        entityManager.persist(laboratory);
        return laboratory;
    }

    private void createReview(
            Laboratory laboratory,
            String suffix,
            int sequence,
            boolean deleted
    ) {
        AppUser author = new AppUser(
                "sort-reviewer-" + sequence + "-" + suffix + "@test.com"
        );
        entityManager.persist(author);

        LaboratoryReview review = new LaboratoryReview(
                laboratory,
                author,
                LaboratoryReviewCategory.RESEARCH_ENVIRONMENT,
                ResearchIntensity.MEDIUM,
                Compensation.SMALL_AMOUNT,
                Atmosphere.COOPERATIVE,
                Set.of(),
                "후기 개수 정렬과 삭제 제외를 검증하기 위한 충분히 긴 내용입니다.",
                2026,
                ParticipationTerm.FIRST_SEMESTER
        );

        if (deleted) {
            review.softDelete();
        }

        entityManager.persist(review);
    }

    private record TestFixture(
            Long collegeId,
            Long twoReviewsLaboratoryId,
            Long olderOneReviewLaboratoryId,
            Long newerOneReviewLaboratoryId,
            Long zeroReviewLaboratoryId
    ) {
    }
}

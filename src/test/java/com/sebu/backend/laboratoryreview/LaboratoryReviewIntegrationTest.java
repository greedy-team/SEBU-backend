package com.sebu.backend.laboratoryreview;

import com.sebu.backend.college.domain.College;
import com.sebu.backend.department.domain.Department;
import com.sebu.backend.laboratory.domain.Laboratory;
import com.sebu.backend.laboratory.domain.RecruitmentStatus;
import com.sebu.backend.laboratoryreview.domain.Atmosphere;
import com.sebu.backend.laboratoryreview.domain.Compensation;
import com.sebu.backend.laboratoryreview.domain.LaboratoryReview;
import com.sebu.backend.laboratoryreview.domain.LaboratoryReviewCategory;
import com.sebu.backend.laboratoryreview.domain.LaboratoryReviewTag;
import com.sebu.backend.laboratoryreview.domain.ParticipationTerm;
import com.sebu.backend.laboratoryreview.domain.ResearchIntensity;
import com.sebu.backend.laboratoryreview.dto.LaboratoryReviewCreateRequest;
import com.sebu.backend.laboratoryreview.exception.InvalidReviewPageException;
import com.sebu.backend.laboratoryreview.exception.InvalidReviewSizeException;
import com.sebu.backend.laboratoryreview.exception.LaboratoryReviewAlreadyExistsException;
import com.sebu.backend.laboratoryreview.repository.LaboratoryReviewRepository;
import com.sebu.backend.laboratoryreview.service.LaboratoryReviewService;
import com.sebu.backend.professor.domain.Professor;
import com.sebu.backend.user.domain.AppUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class LaboratoryReviewIntegrationTest {

    @Autowired
    LaboratoryReviewService laboratoryReviewService;

    @Autowired
    LaboratoryReviewRepository laboratoryReviewRepository;

    @PersistenceContext
    EntityManager entityManager;

    @Autowired
    EntityManagerFactory entityManagerFactory;

    @Test
    void createsLaboratoryReview() {
        // given
        TestFixture fixture = createFixture();

        LaboratoryReviewCreateRequest request = createRequest();

        // when
        var response = laboratoryReviewService.createReview(
                fixture.laboratoryId(),
                fixture.userId(),
                request
        );

        // then
        assertThat(response.reviewId()).isNotNull();

        LaboratoryReview saved = laboratoryReviewRepository
                .findById(response.reviewId())
                .orElseThrow();

        assertThat(saved.getCategory())
                .isEqualTo(
                        LaboratoryReviewCategory.RESEARCH_ENVIRONMENT
                );

        assertThat(saved.getResearchIntensity())
                .isEqualTo(ResearchIntensity.LOW);

        assertThat(saved.getCompensation())
                .isEqualTo(Compensation.SUFFICIENT);

        assertThat(saved.getAtmosphere())
                .isEqualTo(Atmosphere.COOPERATIVE);

        assertThat(saved.getTags())
                .containsExactlyInAnyOrder(
                        LaboratoryReviewTag.PROJECT_OPPORTUNITY,
                        LaboratoryReviewTag.ACTIVE_FEEDBACK
                );

        assertThat(saved.getContent())
                .isEqualTo(
                        "프로젝트 참여 기회가 많고 피드백을 자주 받을 수 있었습니다."
                );

        assertThat(saved.getParticipationYear())
                .isEqualTo(2026);

        assertThat(saved.getParticipationTerm())
                .isEqualTo(
                        ParticipationTerm.FIRST_SEMESTER
                );

        assertThat(saved.isDeleted()).isFalse();
    }

    @Test
    void rejectsDuplicateReview() {
        // given
        TestFixture fixture = createFixture();

        LaboratoryReviewCreateRequest request =
                createRequest();

        laboratoryReviewService.createReview(
                fixture.laboratoryId(),
                fixture.userId(),
                request
        );

        // when & then
        assertThatThrownBy(() ->
                laboratoryReviewService.createReview(
                        fixture.laboratoryId(),
                        fixture.userId(),
                        request
                )
        )
                .isInstanceOf(
                        LaboratoryReviewAlreadyExistsException.class
                )
                .hasMessage(
                        "LABORATORY_REVIEW_ALREADY_EXISTS"
                );
    }

    @Test
    void returnsLaboratoryReviews() {
        // given
        TestFixture fixture = createFixture();

        laboratoryReviewService.createReview(
                fixture.laboratoryId(),
                fixture.userId(),
                createRequest()
        );

        // when
        var response =
                laboratoryReviewService.getReviews(
                        fixture.laboratoryId(),
                        fixture.userId(),
                        0,
                        20
                );

        // then
        assertThat(response.totalElements())
                .isEqualTo(1);

        assertThat(response.reviews())
                .hasSize(1);

        assertThat(response.reviewedByMe())
                .isTrue();

        assertThat(response.laboratory().id())
                .isEqualTo(fixture.laboratoryId());

        assertThat(response.laboratory().name())
                .isEqualTo("테스트 연구실");

        var review = response.reviews().get(0);

        assertThat(review.category())
                .isEqualTo("RESEARCH_ENVIRONMENT");

        assertThat(review.researchIntensity())
                .isEqualTo("LOW");

        assertThat(review.compensation())
                .isEqualTo("SUFFICIENT");

        assertThat(review.atmosphere())
                .isEqualTo("COOPERATIVE");

        assertThat(review.participationYear())
                .isEqualTo(2026);

        assertThat(review.participationTerm())
                .isEqualTo("FIRST_SEMESTER");

        assertThat(review.tags())
                .containsExactlyInAnyOrder(
                        "PROJECT_OPPORTUNITY",
                        "ACTIVE_FEEDBACK"
                );
    }

    @Test
    void returnsReviewedByMeFalseForAnotherUser() {
        // given
        TestFixture fixture = createFixture();

        laboratoryReviewService.createReview(
                fixture.laboratoryId(),
                fixture.userId(),
                createRequest()
        );

        AppUser anotherUser =
                new AppUser(
                        "another-reviewer@test.com"
                );

        entityManager.persist(anotherUser);
        entityManager.flush();

        // when
        var response =
                laboratoryReviewService.getReviews(
                        fixture.laboratoryId(),
                        anotherUser.getId(),
                        0,
                        20
                );

        // then
        assertThat(response.reviews())
                .hasSize(1);

        assertThat(response.reviewedByMe())
                .isFalse();
    }

    @Test
    void returnsReviewedByMeFalseForAnonymousUser() {
        // given
        TestFixture fixture = createFixture();

        laboratoryReviewService.createReview(
                fixture.laboratoryId(),
                fixture.userId(),
                createRequest()
        );

        // when
        var response =
                laboratoryReviewService.getReviews(
                        fixture.laboratoryId(),
                        null,
                        0,
                        20
                );

        // then
        assertThat(response.reviews())
                .hasSize(1);

        assertThat(response.reviewedByMe())
                .isFalse();
    }

    @Test
    void rejectsNegativePage() {
        // given
        TestFixture fixture = createFixture();

        // when & then
        assertThatThrownBy(() ->
                laboratoryReviewService.getReviews(
                        fixture.laboratoryId(),
                        fixture.userId(),
                        -1,
                        20
                )
        )
                .isInstanceOf(
                        InvalidReviewPageException.class
                );
    }

    @Test
    void rejectsZeroSize() {
        // given
        TestFixture fixture = createFixture();

        // when & then
        assertThatThrownBy(() ->
                laboratoryReviewService.getReviews(
                        fixture.laboratoryId(),
                        fixture.userId(),
                        0,
                        0
                )
        )
                .isInstanceOf(
                        InvalidReviewSizeException.class
                );
    }

    @Test
    void rejectsSizeGreaterThanFifty() {
        // given
        TestFixture fixture = createFixture();

        // when & then
        assertThatThrownBy(() ->
                laboratoryReviewService.getReviews(
                        fixture.laboratoryId(),
                        fixture.userId(),
                        0,
                        51
                )
        )
                .isInstanceOf(
                        InvalidReviewSizeException.class
                );
    }

    private LaboratoryReviewCreateRequest createRequest() {
        return new LaboratoryReviewCreateRequest(
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
    }

    private TestFixture createFixture() {
        College college =
                new College(
                        "테스트 단과대학"
                );

        entityManager.persist(college);

        Department department =
                new Department(
                        college,
                        "테스트학과"
                );

        entityManager.persist(department);

        Professor professor =
                new Professor(
                        department,
                        "김교수",
                        "professor@test.com"
                );

        entityManager.persist(professor);

        Laboratory laboratory =
                new Laboratory(
                        professor,
                        department,
                        "테스트 연구실",
                        "https://example.com",
                        RecruitmentStatus.RECRUITING
                );

        entityManager.persist(laboratory);

        AppUser user =
                new AppUser(
                        "reviewer@test.com"
                );

        entityManager.persist(user);

        entityManager.flush();

        return new TestFixture(
                laboratory.getId(),
                user.getId()
        );
    }

    private record TestFixture(
            Long laboratoryId,
            Long userId
    ) {
    }

    @Test
    void allowsReviewCreationAgainAfterSoftDelete() {
        // given
        TestFixture fixture = createFixture();
        LaboratoryReviewCreateRequest request = createRequest();

        var firstResponse = laboratoryReviewService.createReview(
                fixture.laboratoryId(),
                fixture.userId(),
                request
        );

        LaboratoryReview firstReview = laboratoryReviewRepository
                .findById(firstResponse.reviewId())
                .orElseThrow();

        firstReview.softDelete();

        entityManager.flush();
        entityManager.clear();

        // when
        var secondResponse = laboratoryReviewService.createReview(
                fixture.laboratoryId(),
                fixture.userId(),
                request
        );

        entityManager.flush();

        // then
        assertThat(secondResponse.reviewId())
                .isNotNull()
                .isNotEqualTo(firstResponse.reviewId());

        LaboratoryReview recreatedReview =
                laboratoryReviewRepository
                        .findById(secondResponse.reviewId())
                        .orElseThrow();

        assertThat(recreatedReview.isDeleted()).isFalse();

        assertThat(
                laboratoryReviewRepository
                        .existsByLaboratoryIdAndAuthorIdAndDeletedAtIsNull(
                                fixture.laboratoryId(),
                                fixture.userId()
                        )
        ).isTrue();
    }

    @Test
    void reviewListQueryCountDoesNotIncreaseWithNumberOfReviews() {
        // given
        TestFixture fixture = createFixture();

        for (int i = 0; i < 10; i++) {
            AppUser user = new AppUser(
                    "query-test-" + i + "@test.com"
            );
            entityManager.persist(user);

            Laboratory laboratory = entityManager.find(
                    Laboratory.class,
                    fixture.laboratoryId()
            );

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
                    "쿼리 수가 후기 개수에 따라 증가하지 않는지 확인하기 위한 테스트 후기입니다.",
                    2026,
                    ParticipationTerm.FIRST_SEMESTER
            );

            entityManager.persist(review);
        }

        entityManager.flush();
        entityManager.clear();

        Statistics statistics =
                entityManagerFactory
                        .unwrap(SessionFactory.class)
                        .getStatistics();

        statistics.clear();

        // when
        var response = laboratoryReviewService.getReviews(
                fixture.laboratoryId(),
                null,
                0,
                20
        );

        // then
        assertThat(response.reviews()).hasSize(10);

        long queryCount =
                statistics.getPrepareStatementCount();

        assertThat(queryCount).isLessThanOrEqualTo(4);
    }
}

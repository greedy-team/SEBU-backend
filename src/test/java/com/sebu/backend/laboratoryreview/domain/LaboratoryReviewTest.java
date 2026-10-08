package com.sebu.backend.laboratoryreview.domain;

import com.sebu.backend.college.domain.College;
import com.sebu.backend.department.domain.Department;
import com.sebu.backend.laboratory.domain.Laboratory;
import com.sebu.backend.laboratory.domain.RecruitmentStatus;
import com.sebu.backend.laboratoryreview.exception.InvalidLaboratoryReviewInputException;
import com.sebu.backend.professor.domain.Professor;
import com.sebu.backend.user.domain.AppUser;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LaboratoryReviewTest {

    @Test
    void doesNotExposeMutableTags() {
        // given
        LaboratoryReview review = createReview(
                Set.of(LaboratoryReviewTag.PROJECT_OPPORTUNITY)
        );

        // when & then
        assertThatThrownBy(() -> review.getTags().clear())
                .isInstanceOf(UnsupportedOperationException.class);

        assertThat(review.getTags())
                .containsExactly(LaboratoryReviewTag.PROJECT_OPPORTUNITY);
    }

    @Test
    void doesNotAllowAddingTagThroughGetter() {
        // given
        LaboratoryReview review = createReview(
                Set.of(LaboratoryReviewTag.PROJECT_OPPORTUNITY)
        );

        // when & then
        assertThatThrownBy(() ->
                review.getTags().add(
                        LaboratoryReviewTag.ACTIVE_FEEDBACK
                )
        ).isInstanceOf(UnsupportedOperationException.class);

        assertThat(review.getTags())
                .containsExactly(LaboratoryReviewTag.PROJECT_OPPORTUNITY);
    }

    @Test
    void defensivelyCopiesTagsPassedToConstructor() {
        // given
        Set<LaboratoryReviewTag> tags = new LinkedHashSet<>();
        tags.add(LaboratoryReviewTag.PROJECT_OPPORTUNITY);

        LaboratoryReview review = createReview(tags);

        // when
        tags.clear();
        tags.add(LaboratoryReviewTag.ACTIVE_FEEDBACK);

        // then
        assertThat(review.getTags())
                .containsExactly(
                        LaboratoryReviewTag.PROJECT_OPPORTUNITY
                );
    }

    @Test
    void replacesTagsThroughDomainMethod() {
        // given
        LaboratoryReview review = createReview(
                Set.of(LaboratoryReviewTag.PROJECT_OPPORTUNITY)
        );

        // when
        review.replaceTags(
                Set.of(
                        LaboratoryReviewTag.ACTIVE_FEEDBACK,
                        LaboratoryReviewTag.PROFESSOR_COMMUNICATION
                )
        );

        // then
        assertThat(review.getTags())
                .containsExactlyInAnyOrder(
                        LaboratoryReviewTag.ACTIVE_FEEDBACK,
                        LaboratoryReviewTag.PROFESSOR_COMMUNICATION
                );
    }

    @Test
    void rejectsNullTagWhenReplacingTags() {
        // given
        LaboratoryReview review = createReview(
                Set.of(LaboratoryReviewTag.PROJECT_OPPORTUNITY)
        );

        Set<LaboratoryReviewTag> invalidTags =
                new LinkedHashSet<>();
        invalidTags.add(LaboratoryReviewTag.ACTIVE_FEEDBACK);
        invalidTags.add(null);

        // when & then
        assertThatThrownBy(() ->
                review.replaceTags(invalidTags)
        )
                .isInstanceOf(InvalidLaboratoryReviewInputException.class)
                .satisfies(exception -> {
                    InvalidLaboratoryReviewInputException inputException =
                            (InvalidLaboratoryReviewInputException) exception;

                    assertThat(inputException.field()).isEqualTo("tags");
                    assertThat(inputException.reason())
                            .isEqualTo("INVALID_TAG");
                });

        assertThat(review.getTags())
                .containsExactly(
                        LaboratoryReviewTag.PROJECT_OPPORTUNITY
                );
    }

    @Test
    void doesNotAllowReplacingTagsAfterSoftDelete() {
        // given
        LaboratoryReview review = createReview(
                Set.of(LaboratoryReviewTag.PROJECT_OPPORTUNITY)
        );

        review.softDelete();

        // when & then
        assertThatThrownBy(() ->
                review.replaceTags(
                        Set.of(LaboratoryReviewTag.ACTIVE_FEEDBACK)
                )
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "DELETED_LABORATORY_REVIEW_CANNOT_BE_UPDATED"
                );

        assertThat(review.getTags())
                .containsExactly(
                        LaboratoryReviewTag.PROJECT_OPPORTUNITY
                );
    }

    @Test
    void rejectsFutureParticipationYearWithTypedInputException() {
        assertThatThrownBy(() ->
                createReview(
                        Set.of(LaboratoryReviewTag.PROJECT_OPPORTUNITY),
                        "미래 참여 연도 입력을 검증하기 위한 충분히 긴 후기 내용입니다.",
                        java.time.Year.now().getValue() + 1
                )
        )
                .isInstanceOf(InvalidLaboratoryReviewInputException.class)
                .satisfies(exception -> {
                    InvalidLaboratoryReviewInputException inputException =
                            (InvalidLaboratoryReviewInputException) exception;

                    assertThat(inputException.field())
                            .isEqualTo("participationYear");
                    assertThat(inputException.reason())
                            .isEqualTo("OUT_OF_RANGE");
                });
    }

    @Test
    void rejectsContentShorterThanTwentyAfterTrim() {
        assertThatThrownBy(() ->
                createReview(
                        Set.of(LaboratoryReviewTag.PROJECT_OPPORTUNITY),
                        " ".repeat(20) + "짧은 후기",
                        2026
                )
        )
                .isInstanceOf(InvalidLaboratoryReviewInputException.class)
                .satisfies(exception -> {
                    InvalidLaboratoryReviewInputException inputException =
                            (InvalidLaboratoryReviewInputException) exception;

                    assertThat(inputException.field()).isEqualTo("content");
                    assertThat(inputException.reason())
                            .isEqualTo("INVALID_LENGTH");
                });
    }

    @Test
    void failedUpdateDoesNotPartiallyChangeReview() {
        LaboratoryReview review = createReview(
                Set.of(LaboratoryReviewTag.PROJECT_OPPORTUNITY)
        );

        assertThatThrownBy(() -> review.update(
                LaboratoryReviewCategory.ACCEPTANCE,
                ResearchIntensity.HIGH,
                Compensation.SUFFICIENT,
                Atmosphere.COMPETITIVE,
                Set.of(LaboratoryReviewTag.ACTIVE_FEEDBACK),
                "실패한 수정이 일부 필드만 변경하지 않는지 검증하는 후기입니다.",
                java.time.Year.now().getValue() + 1,
                ParticipationTerm.SECOND_SEMESTER
        )).isInstanceOf(InvalidLaboratoryReviewInputException.class);

        assertThat(review.getCategory())
                .isEqualTo(LaboratoryReviewCategory.RESEARCH_ENVIRONMENT);
        assertThat(review.getResearchIntensity())
                .isEqualTo(ResearchIntensity.MEDIUM);
        assertThat(review.getCompensation())
                .isEqualTo(Compensation.SMALL_AMOUNT);
        assertThat(review.getAtmosphere())
                .isEqualTo(Atmosphere.COOPERATIVE);
        assertThat(review.getTags())
                .containsExactly(LaboratoryReviewTag.PROJECT_OPPORTUNITY);
        assertThat(review.getContent())
                .isEqualTo(
                        "연구실 후기 도메인 객체의 태그 캡슐화를 테스트하기 위한 내용입니다."
                );
        assertThat(review.getParticipationYear()).isEqualTo(2026);
        assertThat(review.getParticipationTerm())
                .isEqualTo(ParticipationTerm.FIRST_SEMESTER);
    }

    private LaboratoryReview createReview(
            Set<LaboratoryReviewTag> tags
    ) {
        return createReview(
                tags,
                "연구실 후기 도메인 객체의 태그 캡슐화를 테스트하기 위한 내용입니다.",
                2026
        );
    }

    private LaboratoryReview createReview(
            Set<LaboratoryReviewTag> tags,
            String content,
            int participationYear
    ) {
        College college =
                new College("테스트 단과대학");

        Department department =
                new Department(
                        college,
                        "테스트학과"
                );

        Professor professor =
                new Professor(
                        department,
                        "김교수",
                        "professor@test.com"
                );

        Laboratory laboratory =
                new Laboratory(
                        professor,
                        department,
                        "테스트 연구실",
                        "https://example.com",
                        RecruitmentStatus.RECRUITING
                );

        AppUser user =
                new AppUser("reviewer@test.com");

        return new LaboratoryReview(
                laboratory,
                user,
                LaboratoryReviewCategory.RESEARCH_ENVIRONMENT,
                ResearchIntensity.MEDIUM,
                Compensation.SMALL_AMOUNT,
                Atmosphere.COOPERATIVE,
                tags,
                content,
                participationYear,
                ParticipationTerm.FIRST_SEMESTER
        );
    }
}

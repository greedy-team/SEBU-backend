package com.sebu.backend.laboratoryreview.dto;

import com.sebu.backend.laboratoryreview.domain.Atmosphere;
import com.sebu.backend.laboratoryreview.domain.Compensation;
import com.sebu.backend.laboratoryreview.domain.LaboratoryReviewCategory;
import com.sebu.backend.laboratoryreview.domain.LaboratoryReviewTag;
import com.sebu.backend.laboratoryreview.domain.ParticipationTerm;
import com.sebu.backend.laboratoryreview.domain.ResearchIntensity;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Year;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class LaboratoryReviewRequestValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidatorFactory() {
        validatorFactory.close();
    }

    @Test
    void createRequestRejectsNullTagElement() {
        LaboratoryReviewCreateRequest request =
                new LaboratoryReviewCreateRequest(
                        LaboratoryReviewCategory.RESEARCH_ENVIRONMENT,
                        ResearchIntensity.MEDIUM,
                        Compensation.SMALL_AMOUNT,
                        Atmosphere.COOPERATIVE,
                        tagsContainingNull(),
                        validContent(),
                        Year.now().getValue(),
                        ParticipationTerm.FIRST_SEMESTER
                );

        assertTagsViolation(validator.validate(request));
    }

    @Test
    void updateRequestRejectsNullTagElement() {
        LaboratoryReviewUpdateRequest request =
                new LaboratoryReviewUpdateRequest(
                        LaboratoryReviewCategory.RESEARCH_ENVIRONMENT,
                        ResearchIntensity.MEDIUM,
                        Compensation.SMALL_AMOUNT,
                        Atmosphere.COOPERATIVE,
                        tagsContainingNull(),
                        validContent(),
                        Year.now().getValue(),
                        ParticipationTerm.FIRST_SEMESTER
                );

        assertTagsViolation(validator.validate(request));
    }

    private void assertTagsViolation(
            Set<? extends ConstraintViolation<?>> violations
    ) {
        assertThat(violations)
                .anyMatch(violation ->
                        violation.getPropertyPath()
                                .toString()
                                .startsWith("tags")
                );
    }

    private Set<LaboratoryReviewTag> tagsContainingNull() {
        Set<LaboratoryReviewTag> tags = new LinkedHashSet<>();
        tags.add(LaboratoryReviewTag.PROJECT_OPPORTUNITY);
        tags.add(null);
        return tags;
    }

    private String validContent() {
        return "요청 DTO의 태그 원소 검증을 확인하기 위한 충분히 긴 후기 내용입니다.";
    }
}

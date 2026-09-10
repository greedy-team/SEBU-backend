package com.sebu.backend.laboratoryreview;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sebu.backend.college.domain.College;
import com.sebu.backend.department.domain.Department;
import com.sebu.backend.laboratory.domain.Laboratory;
import com.sebu.backend.laboratory.domain.RecruitmentStatus;
import com.sebu.backend.laboratoryreview.domain.LaboratoryReview;
import com.sebu.backend.professor.domain.Professor;
import com.sebu.backend.user.domain.AppUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItems;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static com.sebu.backend.support.CookieApiRequests.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static com.sebu.backend.support.CookieApiRequests.post;
import static com.sebu.backend.support.CookieApiRequests.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LaboratoryReviewApiIntegrationTest {

    private static final String VALID_CONTENT =
            "프로젝트 참여 기회가 많고 피드백을 충분히 받을 수 있었습니다.";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @PersistenceContext
    EntityManager entityManager;

    @Test
    void anonymousUserCanReadReviewListAndSummary() throws Exception {
        TestFixture fixture = createFixture();

        mockMvc.perform(
                        get(
                                "/api/v1/laboratories/{laboratoryId}/reviews",
                                fixture.laboratoryId()
                        )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.reviewedByMe").value(false));

        mockMvc.perform(
                        get(
                                "/api/v1/laboratories/{laboratoryId}/review-summary",
                                fixture.laboratoryId()
                        )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.reviewCount").value(0));
    }

    @Test
    void anonymousUserCannotCallProtectedReviewApis() throws Exception {
        TestFixture fixture = createFixture();
        String requestBody = requestBody(validRequest());

        RequestBuilder[] protectedRequests = {
                post(
                        "/api/v1/laboratories/{laboratoryId}/reviews",
                        fixture.laboratoryId()
                ).contentType(MediaType.APPLICATION_JSON).content(requestBody),
                get(
                        "/api/v1/laboratories/{laboratoryId}/reviews/me",
                        fixture.laboratoryId()
                ),
                put(
                        "/api/v1/laboratories/{laboratoryId}/reviews/{reviewId}",
                        fixture.laboratoryId(),
                        999_999L
                ).contentType(MediaType.APPLICATION_JSON).content(requestBody),
                delete(
                        "/api/v1/laboratories/{laboratoryId}/reviews/{reviewId}",
                        fixture.laboratoryId(),
                        999_999L
                )
        };

        for (RequestBuilder request : protectedRequests) {
            mockMvc.perform(request)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value("ACCESS_TOKEN_INVALID"));
        }
    }

    @Test
    void ownerCanCreateReadUpdateAndSoftDeleteReview() throws Exception {
        TestFixture fixture = createFixture();
        String createBody = requestBody(validRequest());

        MvcResult createResult = mockMvc.perform(
                        post(
                                "/api/v1/laboratories/{laboratoryId}/reviews",
                                fixture.laboratoryId()
                        )
                                .with(as(fixture.owner()))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(createBody)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        long reviewId = dataId(createResult, "reviewId");

        mockMvc.perform(
                        get(
                                "/api/v1/laboratories/{laboratoryId}/reviews",
                                fixture.laboratoryId()
                        )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewedByMe").value(false))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.reviews[0].id").value(reviewId))
                .andExpect(
                        jsonPath("$.data.reviews[0].category")
                                .value("RESEARCH_ENVIRONMENT")
                )
                .andExpect(
                        jsonPath("$.data.reviews[0].content")
                                .value(VALID_CONTENT)
                )
                .andExpect(
                        jsonPath("$.data.reviews[0].tags", hasItems(
                                "PROJECT_OPPORTUNITY",
                                "ACTIVE_FEEDBACK"
                        ))
                );

        mockMvc.perform(
                        get(
                                "/api/v1/laboratories/{laboratoryId}/review-summary",
                                fixture.laboratoryId()
                        )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewCount").value(1))
                .andExpect(
                        jsonPath(
                                "$.data.evaluationDistributions"
                                        + ".researchIntensity[0].value"
                        ).value("LOW")
                )
                .andExpect(
                        jsonPath(
                                "$.data.evaluationDistributions"
                                        + ".researchIntensity[0].count"
                        ).value(1)
                )
                .andExpect(
                        jsonPath(
                                "$.data.evaluationDistributions"
                                        + ".compensation[0].value"
                        ).value("SUFFICIENT")
                )
                .andExpect(
                        jsonPath(
                                "$.data.evaluationDistributions"
                                        + ".atmosphere[0].value"
                        ).value("COOPERATIVE")
                );

        mockMvc.perform(
                        post(
                                "/api/v1/laboratories/{laboratoryId}/reviews",
                                fixture.laboratoryId()
                        )
                                .with(as(fixture.owner()))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(createBody)
                )
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.error.code")
                                .value("LABORATORY_REVIEW_ALREADY_EXISTS")
                );

        mockMvc.perform(
                        get(
                                "/api/v1/laboratories/{laboratoryId}/reviews/me",
                                fixture.laboratoryId()
                        ).with(as(fixture.owner()))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(reviewId));

        ReviewHttpRequest updateRequest = validRequest().withContent(
                "수정된 후기 내용이며 연구 환경과 피드백 경험을 충분히 설명합니다."
        );

        MvcResult updateResult = mockMvc.perform(
                        put(
                                "/api/v1/laboratories/{laboratoryId}/reviews/{reviewId}",
                                fixture.laboratoryId(),
                                reviewId
                        )
                                .with(as(fixture.owner()))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody(updateRequest))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewId").value(reviewId))
                .andExpect(jsonPath("$.data.updatedAt").isNotEmpty())
                .andReturn();

        LocalDateTime responseUpdatedAt = LocalDateTime.parse(
                objectMapper.readTree(updateResult.getResponse().getContentAsByteArray())
                        .path("data")
                        .path("updatedAt")
                        .asText()
        );
        entityManager.flush();
        entityManager.clear();
        LaboratoryReview storedReview = entityManager.find(
                LaboratoryReview.class,
                reviewId
        );
        assertThat(storedReview.getUpdatedAt()).isCloseTo(
                responseUpdatedAt,
                within(1, ChronoUnit.MICROS)
        );

        MvcResult myReviewResult = mockMvc.perform(
                        get(
                                "/api/v1/laboratories/{laboratoryId}/reviews/me",
                                fixture.laboratoryId()
                        ).with(as(fixture.owner()))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value(updateRequest.content()))
                .andExpect(jsonPath("$.data.updatedAt").isNotEmpty())
                .andReturn();

        LocalDateTime myReviewUpdatedAt = LocalDateTime.parse(
                objectMapper.readTree(
                                myReviewResult.getResponse().getContentAsByteArray()
                        )
                        .path("data")
                        .path("updatedAt")
                        .asText()
        );
        assertThat(myReviewUpdatedAt).isCloseTo(
                responseUpdatedAt,
                within(1, ChronoUnit.MICROS)
        );

        mockMvc.perform(
                        delete(
                                "/api/v1/laboratories/{laboratoryId}/reviews/{reviewId}",
                                fixture.laboratoryId(),
                                reviewId
                        ).with(as(fixture.owner()))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewId").value(reviewId));

        mockMvc.perform(
                        delete(
                                "/api/v1/laboratories/{laboratoryId}/reviews/{reviewId}",
                                fixture.laboratoryId(),
                                reviewId
                        ).with(as(fixture.owner()))
                )
                .andExpect(status().isNotFound())
                .andExpect(
                        jsonPath("$.error.code")
                                .value("LABORATORY_REVIEW_NOT_FOUND")
                );

        mockMvc.perform(
                        get(
                                "/api/v1/laboratories/{laboratoryId}/reviews/me",
                                fixture.laboratoryId()
                        ).with(as(fixture.owner()))
                )
                .andExpect(status().isNotFound())
                .andExpect(
                        jsonPath("$.error.code")
                                .value("LABORATORY_REVIEW_NOT_FOUND")
                );

        mockMvc.perform(
                        put(
                                "/api/v1/laboratories/{laboratoryId}/reviews/{reviewId}",
                                fixture.laboratoryId(),
                                reviewId
                        )
                                .with(as(fixture.owner()))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody(updateRequest))
                )
                .andExpect(status().isNotFound())
                .andExpect(
                        jsonPath("$.error.code")
                                .value("LABORATORY_REVIEW_NOT_FOUND")
                );

        mockMvc.perform(
                        get(
                                "/api/v1/laboratories/{laboratoryId}/reviews",
                                fixture.laboratoryId()
                        )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));

        mockMvc.perform(
                        get(
                                "/api/v1/laboratories/{laboratoryId}/review-summary",
                                fixture.laboratoryId()
                        )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewCount").value(0));
    }

    @Test
    void anotherUserCannotUpdateOrDeleteReview() throws Exception {
        TestFixture fixture = createFixture();
        long reviewId = createReview(fixture, fixture.owner());
        String updateBody = requestBody(validRequest());

        RequestBuilder[] forbiddenRequests = {
                put(
                        "/api/v1/laboratories/{laboratoryId}/reviews/{reviewId}",
                        fixture.laboratoryId(),
                        reviewId
                )
                        .with(as(fixture.anotherUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody),
                delete(
                        "/api/v1/laboratories/{laboratoryId}/reviews/{reviewId}",
                        fixture.laboratoryId(),
                        reviewId
                ).with(as(fixture.anotherUser()))
        };

        for (RequestBuilder request : forbiddenRequests) {
            mockMvc.perform(request)
                    .andExpect(status().isForbidden())
                    .andExpect(
                            jsonPath("$.error.code")
                                    .value("LABORATORY_REVIEW_FORBIDDEN")
                    );
        }
    }

    @Test
    void missingReviewReturnsNotFoundForUpdateAndDelete() throws Exception {
        TestFixture fixture = createFixture();
        String updateBody = requestBody(validRequest());

        RequestBuilder[] missingReviewRequests = {
                put(
                        "/api/v1/laboratories/{laboratoryId}/reviews/{reviewId}",
                        fixture.laboratoryId(),
                        999_999L
                )
                        .with(as(fixture.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody),
                delete(
                        "/api/v1/laboratories/{laboratoryId}/reviews/{reviewId}",
                        fixture.laboratoryId(),
                        999_999L
                ).with(as(fixture.owner()))
        };

        for (RequestBuilder request : missingReviewRequests) {
            mockMvc.perform(request)
                    .andExpect(status().isNotFound())
                    .andExpect(
                            jsonPath("$.error.code")
                                    .value("LABORATORY_REVIEW_NOT_FOUND")
                    );
        }
    }

    @Test
    void invalidPaginationReturnsBadRequest() throws Exception {
        TestFixture fixture = createFixture();

        mockMvc.perform(
                        get(
                                "/api/v1/laboratories/{laboratoryId}/reviews",
                                fixture.laboratoryId()
                        ).param("page", "-1")
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REVIEW_PAGE"));

        for (String invalidSize : List.of("0", "51")) {
            mockMvc.perform(
                            get(
                                    "/api/v1/laboratories/{laboratoryId}/reviews",
                                    fixture.laboratoryId()
                            ).param("size", invalidSize)
                    )
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("INVALID_REVIEW_SIZE"));
        }
    }

    @Test
    void futureParticipationYearReturnsBadRequest() throws Exception {
        TestFixture fixture = createFixture();
        ReviewHttpRequest request = validRequest().withParticipationYear(
                Year.now().getValue() + 1
        );

        assertCreateAndUpdateValidation(fixture, request);
    }

    @Test
    void contentShorterThanTwentyCharactersAfterTrimReturnsBadRequest() throws Exception {
        TestFixture fixture = createFixture();
        ReviewHttpRequest request = validRequest().withContent(
                "짧은 후기                  "
        );

        assertCreateAndUpdateValidation(fixture, request);
    }

    @Test
    void nullTagReturnsBadRequest() throws Exception {
        TestFixture fixture = createFixture();
        ReviewHttpRequest request = validRequest().withTags(
                Arrays.asList("PROJECT_OPPORTUNITY", null)
        );

        assertCreateAndUpdateValidation(fixture, request);
    }

    private void assertCreateAndUpdateValidation(
            TestFixture fixture,
            ReviewHttpRequest invalidRequest
    ) throws Exception {
        performCreate(fixture, invalidRequest)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        long reviewId = createReview(fixture, fixture.owner());

        mockMvc.perform(
                        put(
                                "/api/v1/laboratories/{laboratoryId}/reviews/{reviewId}",
                                fixture.laboratoryId(),
                                reviewId
                        )
                                .with(as(fixture.owner()))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody(invalidRequest))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    private org.springframework.test.web.servlet.ResultActions performCreate(
            TestFixture fixture,
            ReviewHttpRequest request
    ) throws Exception {
        return mockMvc.perform(
                post(
                        "/api/v1/laboratories/{laboratoryId}/reviews",
                        fixture.laboratoryId()
                )
                        .with(as(fixture.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(request))
        );
    }

    private long createReview(
            TestFixture fixture,
            AppUser author
    ) throws Exception {
        MvcResult result = mockMvc.perform(
                        post(
                                "/api/v1/laboratories/{laboratoryId}/reviews",
                                fixture.laboratoryId()
                        )
                                .with(as(author))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody(validRequest()))
                )
                .andExpect(status().isCreated())
                .andReturn();

        return dataId(result, "reviewId");
    }

    private String requestBody(ReviewHttpRequest request) throws Exception {
        return objectMapper.writeValueAsString(request);
    }

    private long dataId(MvcResult result, String field) throws Exception {
        JsonNode root = objectMapper.readTree(
                result.getResponse().getContentAsByteArray()
        );
        return root.path("data").path(field).asLong();
    }

    private RequestPostProcessor as(AppUser user) {
        return jwt().jwt(token -> token
                .subject(user.getId().toString())
                .claim("role", "USER"));
    }

    private ReviewHttpRequest validRequest() {
        return new ReviewHttpRequest(
                "RESEARCH_ENVIRONMENT",
                "LOW",
                "SUFFICIENT",
                "COOPERATIVE",
                List.of("PROJECT_OPPORTUNITY", "ACTIVE_FEEDBACK"),
                VALID_CONTENT,
                Year.now().getValue(),
                "FIRST_SEMESTER"
        );
    }

    private TestFixture createFixture() {
        String key = UUID.randomUUID().toString();

        College college = new College("후기 테스트 단과대학-" + key);
        entityManager.persist(college);

        Department department = new Department(
                college,
                "후기 테스트 학과-" + key
        );
        entityManager.persist(department);

        Professor professor = new Professor(
                department,
                "후기 테스트 교수-" + key,
                "professor-" + key + "@test.com"
        );
        entityManager.persist(professor);

        Laboratory laboratory = new Laboratory(
                professor,
                department,
                "후기 테스트 연구실-" + key,
                "https://example.com/" + key,
                RecruitmentStatus.RECRUITING
        );
        entityManager.persist(laboratory);

        AppUser owner = new AppUser("review-owner-" + key + "@test.com");
        entityManager.persist(owner);

        AppUser anotherUser = new AppUser("review-other-" + key + "@test.com");
        entityManager.persist(anotherUser);

        entityManager.flush();

        return new TestFixture(
                laboratory.getId(),
                owner,
                anotherUser
        );
    }

    private record TestFixture(
            Long laboratoryId,
            AppUser owner,
            AppUser anotherUser
    ) {
    }

    private record ReviewHttpRequest(
            String category,
            String researchIntensity,
            String compensation,
            String atmosphere,
            List<String> tags,
            String content,
            int participationYear,
            String participationTerm
    ) {
        private ReviewHttpRequest withContent(String value) {
            return new ReviewHttpRequest(
                    category,
                    researchIntensity,
                    compensation,
                    atmosphere,
                    tags,
                    value,
                    participationYear,
                    participationTerm
            );
        }

        private ReviewHttpRequest withParticipationYear(int value) {
            return new ReviewHttpRequest(
                    category,
                    researchIntensity,
                    compensation,
                    atmosphere,
                    tags,
                    content,
                    value,
                    participationTerm
            );
        }

        private ReviewHttpRequest withTags(List<String> value) {
            return new ReviewHttpRequest(
                    category,
                    researchIntensity,
                    compensation,
                    atmosphere,
                    value,
                    content,
                    participationYear,
                    participationTerm
            );
        }
    }
}

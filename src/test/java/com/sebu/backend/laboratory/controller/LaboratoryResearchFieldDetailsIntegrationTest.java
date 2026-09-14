package com.sebu.backend.laboratory.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sebu.backend.auth.controller.AuthCookieFactory;
import com.sebu.backend.auth.token.JwtAccessTokenService;
import com.sebu.backend.bookmark.domain.Bookmark;
import com.sebu.backend.bookmark.repository.BookmarkRepository;
import com.sebu.backend.college.domain.College;
import com.sebu.backend.college.repository.CollegeRepository;
import com.sebu.backend.department.domain.Department;
import com.sebu.backend.department.repository.DepartmentRepository;
import com.sebu.backend.laboratory.domain.Laboratory;
import com.sebu.backend.laboratory.domain.LaboratoryDepartment;
import com.sebu.backend.laboratory.domain.LaboratoryResearchField;
import com.sebu.backend.laboratory.domain.RecruitmentStatus;
import com.sebu.backend.laboratory.repository.LaboratoryDepartmentRepository;
import com.sebu.backend.laboratory.repository.LaboratoryRepository;
import com.sebu.backend.laboratory.repository.LaboratoryResearchFieldRepository;
import com.sebu.backend.laboratory.service.LaboratoryQueryService;
import com.sebu.backend.professor.domain.Professor;
import com.sebu.backend.professor.repository.ProfessorRepository;
import com.sebu.backend.researchfield.domain.ResearchField;
import com.sebu.backend.researchfield.repository.ResearchFieldRepository;
import com.sebu.backend.user.domain.AppUser;
import com.sebu.backend.user.repository.AppUserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.servlet.http.Cookie;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LaboratoryResearchFieldDetailsIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired CollegeRepository collegeRepository;
    @Autowired DepartmentRepository departmentRepository;
    @Autowired ProfessorRepository professorRepository;
    @Autowired LaboratoryRepository laboratoryRepository;
    @Autowired LaboratoryDepartmentRepository laboratoryDepartmentRepository;
    @Autowired ResearchFieldRepository researchFieldRepository;
    @Autowired LaboratoryResearchFieldRepository laboratoryResearchFieldRepository;
    @Autowired AppUserRepository appUserRepository;
    @Autowired BookmarkRepository bookmarkRepository;
    @Autowired JwtAccessTokenService accessTokenService;
    @Autowired LaboratoryQueryService queryService;
    @Autowired EntityManager entityManager;
    @Autowired EntityManagerFactory entityManagerFactory;
    @Autowired JdbcTemplate jdbcTemplate;

    private Department department;
    private Professor professor;
    private Laboratory mainLaboratory;
    private Laboratory otherLaboratory;
    private Laboratory unmappedLaboratory;
    private Laboratory emptyLaboratory;
    private ResearchField sharedField;
    private ResearchField singleCategoryField;
    private ResearchField unmappedField;
    private ResearchField otherLaboratoryField;
    private Long earlyCategoryId;
    private Long middleCategoryId;
    private Long lateCategoryId;
    private AppUser bookmarkedUser;

    @BeforeEach
    void setUp() {
        College college = collegeRepository.save(new College("연구분야 상세 테스트 대학"));
        department = departmentRepository.save(new Department(college, "연구분야 상세 테스트 학과"));
        professor = professorRepository.save(new Professor(department, "상세 테스트 교수", null));
        mainLaboratory = saveLaboratory("주 연구실");
        otherLaboratory = saveLaboratory("다른 연구실");
        unmappedLaboratory = saveLaboratory("미분류 연구실");
        emptyLaboratory = saveLaboratory("분야 없는 연구실");

        // Insert IDs in a different order from their names and category display order.
        singleCategoryField = researchFieldRepository.saveAndFlush(new ResearchField("Z 단일 분야"));
        unmappedField = researchFieldRepository.saveAndFlush(new ResearchField("M 미분류 분야"));
        sharedField = researchFieldRepository.saveAndFlush(new ResearchField("A 공유 분야"));
        otherLaboratoryField = researchFieldRepository.saveAndFlush(new ResearchField("B 다른 연구실 전용 분야"));
        lateCategoryId = saveCategory("DETAIL_LATE", "상세 후순위", 930);
        earlyCategoryId = saveCategory("DETAIL_EARLY", "상세 선순위", 910);
        middleCategoryId = saveCategory("DETAIL_MIDDLE", "상세 중간순위", 920);
        mapCategory(sharedField, lateCategoryId);
        mapCategory(sharedField, earlyCategoryId);
        mapCategory(singleCategoryField, earlyCategoryId);
        mapCategory(otherLaboratoryField, middleCategoryId);

        connect(mainLaboratory, singleCategoryField);
        connect(mainLaboratory, unmappedField);
        connect(mainLaboratory, sharedField);
        connect(otherLaboratory, otherLaboratoryField);
        connect(otherLaboratory, sharedField);
        connect(unmappedLaboratory, unmappedField);
        bookmarkedUser = appUserRepository.save(AppUser.sejong("details-user"));
        bookmarkRepository.save(new Bookmark(bookmarkedUser, mainLaboratory));
        flushAndClear();
    }

    @Test
    void returnsFieldIdsWithOrderedPerFieldCategoriesAndPreservesExistingFields() throws Exception {
        JsonNode laboratories = requestData(get("/api/v1/laboratories")).path("laboratories");
        assertThat(laboratories.size()).isEqualTo(4);
        JsonNode main = laboratory(laboratories, mainLaboratory.getId());

        assertMainResearchFields(main);
        assertThat(main.path("affiliations").size()).isOne();
        assertThat(main.path("affiliations").get(0).path("department").path("id").longValue())
            .isEqualTo(department.getId());
        assertThat(main.path("professor").path("id").longValue()).isEqualTo(professor.getId());
        assertThat(main.path("bookmarked").booleanValue()).isFalse();
        assertThat(main.path("bookmarkCount").longValue()).isOne();
        assertThat(main.path("reviewCount").longValue()).isZero();
    }

    @Test
    void preservesUnmappedFieldsAndReturnsEmptyArraysForLaboratoriesWithoutFields() throws Exception {
        JsonNode laboratories = requestData(get("/api/v1/laboratories")).path("laboratories");
        JsonNode unmapped = laboratory(laboratories, unmappedLaboratory.getId());
        assertThat(textValues(unmapped.path("researchFields"))).containsExactly(unmappedField.getName());
        assertThat(unmapped.path("researchFieldDetails").size()).isOne();
        assertResearchField(unmapped.path("researchFieldDetails").get(0), unmappedField);
        assertEmptyArray(unmapped, "researchFieldCategoryIds");
        assertEmptyArray(unmapped, "researchFieldCategories");

        JsonNode empty = laboratory(laboratories, emptyLaboratory.getId());
        for (String field : List.of("researchFields", "researchFieldDetails",
            "researchFieldCategoryIds", "researchFieldCategories")) {
            assertEmptyArray(empty, field);
        }
    }

    @Test
    void keepsSharedIdsConsistentWithoutLeakingAnotherLaboratorysFieldsOrCategories() throws Exception {
        JsonNode laboratories = requestData(get("/api/v1/laboratories")).path("laboratories");
        JsonNode main = laboratory(laboratories, mainLaboratory.getId());
        JsonNode other = laboratory(laboratories, otherLaboratory.getId());

        assertMainResearchFields(main);
        assertThat(other.path("researchFieldDetails").size()).isEqualTo(2);
        assertResearchField(other.path("researchFieldDetails").get(0), sharedField, earlyCategoryId, lateCategoryId);
        assertResearchField(other.path("researchFieldDetails").get(1), otherLaboratoryField, middleCategoryId);
        assertThat(other.path("researchFieldDetails").get(0))
            .isEqualTo(main.path("researchFieldDetails").get(0));
        assertThat(textValues(other.path("researchFields")))
            .containsExactly(sharedField.getName(), otherLaboratoryField.getName());
        assertThat(longValues(other.path("researchFieldCategoryIds")))
            .containsExactly(earlyCategoryId, middleCategoryId, lateCategoryId);
        assertThat(objectIds(other.path("researchFieldCategories")))
            .containsExactly(earlyCategoryId, middleCategoryId, lateCategoryId);
    }

    @Test
    void returnsTheSameResearchFieldContractOnEveryReviewCountPage() throws Exception {
        JsonNode all = requestData(get("/api/v1/laboratories")).path("laboratories");
        for (int page = 0; page < 2; page++) {
            JsonNode data = requestData(get("/api/v1/laboratories")
                .param("sort", "REVIEW_COUNT_DESC").param("page", Integer.toString(page)).param("size", "2"));

            assertThat(data.path("page").intValue()).isEqualTo(page);
            assertThat(data.path("size").intValue()).isEqualTo(2);
            assertThat(data.path("totalElements").longValue()).isEqualTo(4);
            assertThat(data.path("hasNext").booleanValue()).isEqualTo(page == 0);
            assertThat(data.path("laboratories").size()).isEqualTo(2);
            List<Long> expectedIds = page == 0
                ? List.of(emptyLaboratory.getId(), unmappedLaboratory.getId())
                : List.of(otherLaboratory.getId(), mainLaboratory.getId());
            assertThat(objectIds(data.path("laboratories"))).containsExactlyElementsOf(expectedIds);
            for (JsonNode pagedLaboratory : data.path("laboratories")) {
                assertThat(pagedLaboratory).isEqualTo(laboratory(all, pagedLaboratory.path("id").longValue()));
            }
        }
    }

    @Test
    void usesRealAccessCookieForBookmarksWhileKeepingResearchFieldDetailsPublic() throws Exception {
        JsonNode anonymous = laboratory(requestData(get("/api/v1/laboratories")).path("laboratories"),
            mainLaboratory.getId());
        String accessToken = accessTokenService.issue(bookmarkedUser.getId(), bookmarkedUser.getAuthVersion());
        JsonNode authenticated = laboratory(requestData(get("/api/v1/laboratories")
            .cookie(new Cookie(AuthCookieFactory.ACCESS_COOKIE, accessToken))).path("laboratories"),
            mainLaboratory.getId());

        assertThat(anonymous.path("bookmarked").booleanValue()).isFalse();
        assertThat(authenticated.path("bookmarked").booleanValue()).isTrue();
        assertMainResearchFields(authenticated);
        for (String field : List.of("researchFields", "researchFieldDetails", "researchFieldCategoryIds",
            "researchFieldCategories", "affiliations", "bookmarkCount")) {
            assertThat(authenticated.path(field)).isEqualTo(anonymous.path(field));
        }
    }

    @Test
    void keepsFiveQueriesWhenTheNumberOfLaboratoriesAndFieldLinksGrows() {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        assertThat(queryService.getAll().laboratories()).hasSize(4);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(5);

        for (int index = 0; index < 12; index++) {
            Laboratory laboratory = saveLaboratory("일괄 조회 테스트 연구실 " + index);
            connect(laboratory, sharedField);
            connect(laboratory, singleCategoryField);
            connect(laboratory, unmappedField);
        }
        flushAndClear();
        statistics.clear();
        assertThat(queryService.getAll().laboratories()).hasSize(16);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(5);
    }

    private void assertMainResearchFields(JsonNode laboratory) {
        JsonNode details = laboratory.path("researchFieldDetails");
        assertThat(details.isArray()).isTrue();
        assertThat(details.size()).isEqualTo(3);
        assertResearchField(details.get(0), sharedField, earlyCategoryId, lateCategoryId);
        assertResearchField(details.get(1), unmappedField);
        assertResearchField(details.get(2), singleCategoryField, earlyCategoryId);
        assertThat(textValues(laboratory.path("researchFields")))
            .containsExactly(sharedField.getName(), unmappedField.getName(), singleCategoryField.getName());
        assertThat(longValues(laboratory.path("researchFieldCategoryIds")))
            .containsExactly(earlyCategoryId, lateCategoryId);
        assertThat(objectIds(laboratory.path("researchFieldCategories")))
            .containsExactly(earlyCategoryId, lateCategoryId);
        assertThat(laboratory.path("researchFieldCategories").get(0).path("code").textValue())
            .isEqualTo("DETAIL_EARLY");
        assertThat(laboratory.path("researchFieldCategories").get(1).path("code").textValue())
            .isEqualTo("DETAIL_LATE");
    }

    private void assertResearchField(JsonNode field, ResearchField expected, Long... categoryIds) {
        assertThat(field.path("researchFieldId").isIntegralNumber()).isTrue();
        assertThat(field.path("researchFieldId").longValue()).isEqualTo(expected.getId());
        assertThat(field.path("name").textValue()).isEqualTo(expected.getName());
        assertThat(field.path("categoryIds").isArray()).isTrue();
        assertThat(longValues(field.path("categoryIds"))).containsExactly(categoryIds);
    }

    private void assertEmptyArray(JsonNode object, String property) {
        assertThat(object.path(property).isArray()).as(property).isTrue();
        assertThat(object.path(property).size()).as(property).isZero();
    }

    private JsonNode requestData(MockHttpServletRequestBuilder request) throws Exception {
        byte[] response = mockMvc.perform(request).andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true)).andReturn().getResponse().getContentAsByteArray();
        return objectMapper.readTree(response).path("data");
    }

    private JsonNode laboratory(JsonNode laboratories, long laboratoryId) {
        return StreamSupport.stream(laboratories.spliterator(), false)
            .filter(laboratory -> laboratory.path("id").longValue() == laboratoryId)
            .findFirst().orElseThrow(() -> new AssertionError("Missing laboratory " + laboratoryId));
    }

    private List<Long> longValues(JsonNode array) {
        return StreamSupport.stream(array.spliterator(), false).map(JsonNode::longValue).toList();
    }

    private List<Long> objectIds(JsonNode objects) {
        return StreamSupport.stream(objects.spliterator(), false)
            .map(object -> object.path("id").longValue()).toList();
    }

    private List<String> textValues(JsonNode array) {
        return StreamSupport.stream(array.spliterator(), false).map(JsonNode::textValue).toList();
    }

    private Laboratory saveLaboratory(String name) {
        Laboratory laboratory = laboratoryRepository.save(new Laboratory(professor, department, name,
            null, RecruitmentStatus.UNKNOWN));
        laboratoryDepartmentRepository.save(new LaboratoryDepartment(laboratory, department));
        return laboratory;
    }

    private Long saveCategory(String code, String name, int displayOrder) {
        jdbcTemplate.update("""
            INSERT INTO research_field_category (code, name, description, display_order)
            VALUES (?, ?, ?, ?)
            """, code, name, "연구 분야 상세 응답 테스트", displayOrder);
        return jdbcTemplate.queryForObject("SELECT id FROM research_field_category WHERE code = ?", Long.class, code);
    }

    private void mapCategory(ResearchField field, Long categoryId) {
        jdbcTemplate.update("INSERT INTO research_field_category_mapping (research_field_id, category_id) VALUES (?, ?)",
            field.getId(), categoryId);
    }

    private void connect(Laboratory laboratory, ResearchField field) {
        laboratoryResearchFieldRepository.save(new LaboratoryResearchField(laboratory, field));
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}

package com.sebu.backend.laboratory.controller;

import com.sebu.backend.bookmark.domain.Bookmark;
import com.sebu.backend.bookmark.repository.BookmarkRepository;
import com.sebu.backend.college.domain.College;
import com.sebu.backend.college.repository.CollegeRepository;
import com.sebu.backend.department.domain.Department;
import com.sebu.backend.department.repository.DepartmentRepository;
import com.sebu.backend.global.auth.CurrentUserProvider;
import com.sebu.backend.laboratory.domain.Laboratory;
import com.sebu.backend.laboratory.domain.LaboratoryDepartment;
import com.sebu.backend.laboratory.domain.LaboratoryNameSource;
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
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LaboratoryApiIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired CollegeRepository collegeRepository;
    @Autowired DepartmentRepository departmentRepository;
    @Autowired ProfessorRepository professorRepository;
    @Autowired LaboratoryRepository laboratoryRepository;
    @Autowired LaboratoryDepartmentRepository laboratoryDepartmentRepository;
    @Autowired ResearchFieldRepository researchFieldRepository;
    @Autowired LaboratoryResearchFieldRepository laboratoryResearchFieldRepository;
    @Autowired AppUserRepository appUserRepository;
    @Autowired BookmarkRepository bookmarkRepository;
    @Autowired LaboratoryQueryService queryService;
    @Autowired EntityManager entityManager;
    @Autowired EntityManagerFactory entityManagerFactory;
    @Autowired JdbcTemplate jdbcTemplate;
    @MockitoBean CurrentUserProvider currentUserProvider;

    private Long firstUserId;

    @BeforeEach
    void setUp() {
        // Isolate API fixtures from deployment catalogue data; rolled back with this test.
        jdbcTemplate.update("UPDATE laboratory SET deleted_at=CURRENT_TIMESTAMP WHERE deleted_at IS NULL");
        College college = collegeRepository.save(new College("API테스트 인공지능융합대학"));
        Department ai = departmentRepository.save(new Department(college, "인공지능학과"));
        Department computer = departmentRepository.save(new Department(college, "컴퓨터공학과"));
        Professor kim = professorRepository.save(new Professor(ai, "김민준", "minjun.kim@example.ac.kr"));
        Professor park = professorRepository.save(new Professor(computer, "박지훈", null));
        Laboratory lab1 = laboratoryRepository.save(new Laboratory(kim, ai, "인공지능연구실", "https://ai-lab.example.ac.kr", RecruitmentStatus.RECRUITING));
        Laboratory lab2 = laboratoryRepository.save(new Laboratory(
            park,
            computer,
            "박지훈 교수님 연구실",
            null,
            null,
            RecruitmentStatus.ALWAYS_OPEN,
            LaboratoryNameSource.GENERATED
        ));
        Laboratory deleted = laboratoryRepository.save(new Laboratory(park, computer, "삭제된연구실", null, RecruitmentStatus.CLOSED));
        deleted.softDelete();
        laboratoryDepartmentRepository.save(new LaboratoryDepartment(lab1, ai));
        laboratoryDepartmentRepository.save(new LaboratoryDepartment(lab1, computer));
        laboratoryDepartmentRepository.save(new LaboratoryDepartment(lab2, computer));
        laboratoryDepartmentRepository.save(new LaboratoryDepartment(deleted, computer));
        ResearchField machineLearning = researchFieldRepository.saveAndFlush(
            new ResearchField("API테스트 머신러닝")
        );
        ResearchField aiField = researchFieldRepository.saveAndFlush(
            new ResearchField("API테스트 인공지능")
        );
        ResearchField computerVision = researchFieldRepository.saveAndFlush(
            new ResearchField("API테스트 컴퓨터 비전")
        );
        mapCategory(machineLearning, "AI_ML");
        mapCategory(aiField, "AI_ML");
        mapCategory(computerVision, "SIGNAL_MEDIA");
        laboratoryResearchFieldRepository.save(new LaboratoryResearchField(lab1, machineLearning));
        laboratoryResearchFieldRepository.save(new LaboratoryResearchField(lab1, aiField));
        laboratoryResearchFieldRepository.save(new LaboratoryResearchField(lab1, computerVision));
        AppUser user1 = appUserRepository.save(new AppUser("one@example.com"));
        AppUser user2 = appUserRepository.save(new AppUser("two@example.com"));
        bookmarkRepository.save(new Bookmark(user1, lab1));
        bookmarkRepository.save(new Bookmark(user2, lab1));
        firstUserId = user1.getId();
        entityManager.flush();
        entityManager.clear();
    }

    private void mapCategory(ResearchField researchField, String categoryCode) {
        assertThat(jdbcTemplate.update(
            """
                INSERT INTO research_field_category_mapping (research_field_id, category_id)
                SELECT ?, category.id
                FROM research_field_category category
                WHERE category.code = ?
                """,
            researchField.getId(),
            categoryCode
        )).isOne();
    }

    @Test
    void returnsAuthenticatedLaboratoryResponseMatchingSpecification() throws Exception {
        when(currentUserProvider.currentUserId()).thenReturn(Optional.of(firstUserId));
        mockMvc.perform(get("/api/v1/laboratories"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.error").doesNotExist())
            .andExpect(jsonPath("$.data.laboratories.length()").value(2))
            .andExpect(jsonPath("$.data.laboratories[0].name").value("인공지능연구실"))
            .andExpect(jsonPath("$.data.laboratories[0].nameSource").value("OFFICIAL"))
            .andExpect(jsonPath("$.data.laboratories[0].professor.name").value("김민준"))
            .andExpect(jsonPath("$.data.laboratories[0].college.name").value("API테스트 인공지능융합대학"))
            .andExpect(jsonPath("$.data.laboratories[0].department.name").value("인공지능학과"))
            .andExpect(jsonPath("$.data.laboratories[0].affiliations.length()").value(2))
            .andExpect(jsonPath("$.data.laboratories[0].affiliations[0].department.name").value("인공지능학과"))
            .andExpect(jsonPath("$.data.laboratories[0].affiliations[1].department.name").value("컴퓨터공학과"))
            .andExpect(jsonPath("$.data.laboratories[0].researchFields.length()").value(3))
            .andExpect(jsonPath("$.data.laboratories[0].researchFieldCategoryIds[0]")
                .value(1))
            .andExpect(jsonPath("$.data.laboratories[0].researchFieldCategoryIds[1]")
                .value(6))
            .andExpect(jsonPath("$.data.laboratories[0].researchFieldCategories.length()").value(2))
            .andExpect(jsonPath("$.data.laboratories[0].researchFieldCategories[0].code")
                .value("AI_ML"))
            .andExpect(jsonPath("$.data.laboratories[0].researchFieldCategories[1].code")
                .value("SIGNAL_MEDIA"))
            .andExpect(jsonPath("$.data.laboratories[0].bookmarkCount").value(2))
            .andExpect(jsonPath("$.data.laboratories[0].bookmarked").value(true))
            .andExpect(jsonPath("$.data.laboratories[1].websiteUrl").doesNotExist())
            .andExpect(jsonPath("$.data.laboratories[1].name").value("박지훈 교수님 연구실"))
            .andExpect(jsonPath("$.data.laboratories[1].nameSource").value("GENERATED"))
            .andExpect(jsonPath("$.data.laboratories[1].affiliations.length()").value(1))
            .andExpect(jsonPath("$.data.laboratories[1].professor.email").doesNotExist())
            .andExpect(jsonPath("$.data.laboratories[1].researchFields").isEmpty())
            .andExpect(jsonPath("$.data.laboratories[1].researchFieldCategoryIds").isEmpty())
            .andExpect(jsonPath("$.data.laboratories[1].researchFieldCategories").isEmpty())
            .andExpect(jsonPath("$.data.laboratories[1].bookmarkCount").value(0));
    }

    @Test
    void anonymousUserIsNeverBookmarked() throws Exception {
        when(currentUserProvider.currentUserId()).thenReturn(Optional.empty());
        mockMvc.perform(get("/api/v1/laboratories"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.laboratories[0].bookmarked").value(false));
    }

    @Test
    void queryCountStaysFixedWithoutNPlusOne() {
        when(currentUserProvider.currentUserId())
                .thenReturn(Optional.of(firstUserId));

        Statistics statistics = entityManagerFactory
                .unwrap(SessionFactory.class)
                .getStatistics();

        statistics.clear();

        assertThat(queryService.getAll().laboratories())
                .hasSize(2);

        assertThat(statistics.getPrepareStatementCount())
                .isEqualTo(5);
    }
}

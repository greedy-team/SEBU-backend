package com.sebu.backend.laboratory.repository;

import com.sebu.backend.college.domain.College;
import com.sebu.backend.college.repository.CollegeRepository;
import com.sebu.backend.department.domain.Department;
import com.sebu.backend.department.repository.DepartmentRepository;
import com.sebu.backend.laboratory.domain.Laboratory;
import com.sebu.backend.laboratory.domain.RecruitmentStatus;
import com.sebu.backend.professor.domain.Professor;
import com.sebu.backend.professor.repository.ProfessorRepository;
import com.sebu.backend.researchfield.domain.ResearchField;
import com.sebu.backend.researchfield.repository.ResearchFieldRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class LaboratoryResearchFieldCategoryMySqlRepositoryTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Autowired
    LaboratoryResearchFieldCategoryQueryRepository repository;

    @Autowired CollegeRepository collegeRepository;
    @Autowired DepartmentRepository departmentRepository;
    @Autowired ProfessorRepository professorRepository;
    @Autowired LaboratoryRepository laboratoryRepository;
    @Autowired ResearchFieldRepository researchFieldRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void configureMySql(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add(
            "spring.datasource.driver-class-name",
            () -> "com.mysql.cj.jdbc.Driver"
        );
    }

    @Test
    void executesDistinctCategoryOrderingOnMySql() {
        assertThat(repository.findAllByLaboratoryIds(List.of(Long.MAX_VALUE)))
            .isEmpty();
    }

    @Test
    void preservesEachFieldCategoryRelationshipInDisplayOrderForSelectedLaboratories() {
        College college = collegeRepository.save(new College("MySQL 관계 테스트 대학"));
        Department department = departmentRepository.save(new Department(college, "MySQL 관계 테스트 학과"));
        Professor professor = professorRepository.save(new Professor(department, "MySQL 관계 테스트 교수", null));
        Laboratory selected = laboratoryRepository.saveAndFlush(new Laboratory(
            professor, department, "MySQL 조회 대상 연구실", null, RecruitmentStatus.UNKNOWN
        ));
        Laboratory unrelated = laboratoryRepository.saveAndFlush(new Laboratory(
            professor, department, "MySQL 다른 연구실", null, RecruitmentStatus.UNKNOWN
        ));
        ResearchField multipleCategories = researchFieldRepository.saveAndFlush(new ResearchField("MySQL 다중 분야"));
        ResearchField sharedCategory = researchFieldRepository.saveAndFlush(new ResearchField("MySQL 공유 분야"));
        ResearchField unrelatedField = researchFieldRepository.saveAndFlush(new ResearchField("MySQL 다른 분야"));

        // Create category IDs in the opposite order from their display order.
        Long lateCategoryId = saveCategory("MYSQL_LATE", "MySQL 후순위", 920);
        Long earlyCategoryId = saveCategory("MYSQL_EARLY", "MySQL 선순위", 910);
        jdbcTemplate.update("""
            INSERT INTO laboratory_research_field (laboratory_id, research_field_id)
            VALUES (?, ?), (?, ?), (?, ?)
            """, selected.getId(), multipleCategories.getId(),
            selected.getId(), sharedCategory.getId(), unrelated.getId(), unrelatedField.getId());
        jdbcTemplate.update("""
            INSERT INTO research_field_category_mapping (research_field_id, category_id)
            VALUES (?, ?), (?, ?), (?, ?), (?, ?)
            """, multipleCategories.getId(), lateCategoryId,
            multipleCategories.getId(), earlyCategoryId,
            sharedCategory.getId(), earlyCategoryId, unrelatedField.getId(), earlyCategoryId);

        assertThat(repository.findAllByLaboratoryIds(List.of(selected.getId())))
            .extracting(
                LaboratoryResearchFieldCategoryProjection::getLaboratoryId,
                LaboratoryResearchFieldCategoryProjection::getResearchFieldId,
                LaboratoryResearchFieldCategoryProjection::getCategoryId,
                LaboratoryResearchFieldCategoryProjection::getCategoryCode,
                LaboratoryResearchFieldCategoryProjection::getCategoryName,
                LaboratoryResearchFieldCategoryProjection::getDisplayOrder
            )
            .containsExactly(
                tuple(selected.getId(), multipleCategories.getId(), earlyCategoryId,
                    "MYSQL_EARLY", "MySQL 선순위", 910),
                tuple(selected.getId(), sharedCategory.getId(), earlyCategoryId,
                    "MYSQL_EARLY", "MySQL 선순위", 910),
                tuple(selected.getId(), multipleCategories.getId(), lateCategoryId,
                    "MYSQL_LATE", "MySQL 후순위", 920)
            );
    }

    private Long saveCategory(String code, String name, int displayOrder) {
        jdbcTemplate.update("""
            INSERT INTO research_field_category (code, name, description, display_order)
            VALUES (?, ?, ?, ?)
            """, code, name, "MySQL 연구 분야 관계 테스트", displayOrder);
        return jdbcTemplate.queryForObject(
            "SELECT id FROM research_field_category WHERE code = ?", Long.class, code
        );
    }
}

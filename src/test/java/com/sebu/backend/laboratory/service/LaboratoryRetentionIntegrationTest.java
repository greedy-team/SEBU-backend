package com.sebu.backend.laboratory.service;

import com.sebu.backend.laboratory.service.LaboratoryPurgeService;
import com.sebu.backend.laboratory.config.LaboratoryRetentionProperties;
import com.sebu.backend.bookmark.domain.Bookmark;
import com.sebu.backend.bookmark.repository.BookmarkRepository;
import com.sebu.backend.college.domain.College;
import com.sebu.backend.college.repository.CollegeRepository;
import com.sebu.backend.department.domain.Department;
import com.sebu.backend.department.repository.DepartmentRepository;
import com.sebu.backend.laboratory.domain.Laboratory;
import com.sebu.backend.laboratory.repository.LaboratoryRepository;
import com.sebu.backend.laboratory.domain.RecruitmentStatus;
import com.sebu.backend.professor.domain.Professor;
import com.sebu.backend.professor.repository.ProfessorRepository;
import com.sebu.backend.laboratory.domain.LaboratoryResearchField;
import com.sebu.backend.laboratory.repository.LaboratoryResearchFieldRepository;
import com.sebu.backend.researchfield.domain.ResearchField;
import com.sebu.backend.researchfield.repository.ResearchFieldRepository;
import com.sebu.backend.user.domain.AppUser;
import com.sebu.backend.user.repository.AppUserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class LaboratoryRetentionIntegrationTest {
    @Autowired CollegeRepository collegeRepository;
    @Autowired DepartmentRepository departmentRepository;
    @Autowired ProfessorRepository professorRepository;
    @Autowired LaboratoryRepository laboratoryRepository;
    @Autowired ResearchFieldRepository researchFieldRepository;
    @Autowired LaboratoryResearchFieldRepository laboratoryResearchFieldRepository;
    @Autowired AppUserRepository appUserRepository;
    @Autowired BookmarkRepository bookmarkRepository;
    @Autowired LaboratoryPurgeService purgeService;
    @Autowired LaboratoryRetentionProperties retentionProperties;
    @Autowired EntityManager entityManager;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void purgesLaboratoriesAfterThirtyDaysAndCascadesRelatedData() {
        LocalDateTime currentTime = LocalDateTime.of(2026, 8, 14, 3, 0);
        College college = collegeRepository.save(new College("보존정책대학"));
        Department department = departmentRepository.save(new Department(college, "보존정책학과"));
        Professor professor = professorRepository.save(new Professor(department, "보존정책교수", null));
        Laboratory expired = laboratoryRepository.saveAndFlush(
            new Laboratory(professor, department, "30일 경과 연구실", null, RecruitmentStatus.CLOSED)
        );
        Laboratory retained = laboratoryRepository.saveAndFlush(
            new Laboratory(professor, department, "29일 경과 연구실", null, RecruitmentStatus.CLOSED)
        );
        ResearchField researchField = researchFieldRepository.saveAndFlush(new ResearchField("보존정책 테스트"));
        AppUser user = appUserRepository.saveAndFlush(new AppUser("retention@example.com"));
        laboratoryResearchFieldRepository.saveAndFlush(new LaboratoryResearchField(expired, researchField));
        bookmarkRepository.saveAndFlush(new Bookmark(user, expired));

        expired.softDelete();
        retained.softDelete();
        laboratoryRepository.flush();
        updateDeletedAt(expired.getId(), currentTime.minusDays(30));
        updateDeletedAt(retained.getId(), currentTime.minusDays(29));
        entityManager.clear();

        int deletedCount = purgeService.purgeExpiredLaboratories(currentTime);

        assertThat(retentionProperties.getDays()).isEqualTo(30);
        assertThat(deletedCount).isOne();
        assertThat(laboratoryRepository.findById(expired.getId())).isEmpty();
        assertThat(laboratoryRepository.findById(retained.getId())).isPresent();
        assertThat(countRows("laboratory_research_field", expired.getId())).isZero();
        assertThat(countRows("bookmark", expired.getId())).isZero();
    }

    private void updateDeletedAt(Long laboratoryId, LocalDateTime deletedAt) {
        jdbcTemplate.update(
            "UPDATE laboratory SET deleted_at = ? WHERE id = ?",
            Timestamp.valueOf(deletedAt),
            laboratoryId
        );
    }

    private long countRows(String tableName, Long laboratoryId) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM " + tableName + " WHERE laboratory_id = ?",
            Long.class,
            laboratoryId
        );
    }
}

package com.sebu.backend.global.integration;

import com.sebu.backend.bookmark.domain.Bookmark;
import com.sebu.backend.bookmark.domain.BookmarkId;
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
import com.sebu.backend.laboratory.domain.LaboratoryResearchFieldId;
import com.sebu.backend.laboratory.repository.LaboratoryResearchFieldRepository;
import com.sebu.backend.researchfield.domain.ResearchField;
import com.sebu.backend.researchfield.repository.ResearchFieldRepository;
import com.sebu.backend.user.domain.AppUser;
import com.sebu.backend.user.repository.AppUserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class DatabaseConstraintsIntegrationTest {
    @Autowired CollegeRepository collegeRepository;
    @Autowired DepartmentRepository departmentRepository;
    @Autowired ProfessorRepository professorRepository;
    @Autowired LaboratoryRepository laboratoryRepository;
    @Autowired ResearchFieldRepository researchFieldRepository;
    @Autowired LaboratoryResearchFieldRepository laboratoryResearchFieldRepository;
    @Autowired AppUserRepository appUserRepository;
    @Autowired BookmarkRepository bookmarkRepository;
    @Autowired EntityManager entityManager;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void identityEnumAndResearchFieldOptionalityAreMapped() {
        Hierarchy h = hierarchy("AI", "인공지능학과", "김교수", null);
        Laboratory lab = laboratoryRepository.saveAndFlush(new Laboratory(h.professor, h.department, "AI 연구실", null, RecruitmentStatus.ALWAYS_OPEN));
        assertThat(lab.getId()).isNotNull();
        entityManager.clear();
        Laboratory found = laboratoryRepository.findById(lab.getId()).orElseThrow();
        assertThat(found.getRecruitmentStatus()).isEqualTo(RecruitmentStatus.ALWAYS_OPEN);
        assertThat(found.getWebsiteUrl()).isNull();
        assertThat(laboratoryResearchFieldRepository.findFieldsByLaboratoryIds(List.of(lab.getId())))
            .isEmpty();
    }

    @Test
    void unknownRecruitmentStatusIsStored() {
        Hierarchy h = hierarchy("정보대학", "미분류학과", "정보없음", null);
        Laboratory lab = laboratoryRepository.saveAndFlush(
            new Laboratory(h.professor, h.department, "모집정보 미정 연구실", null, RecruitmentStatus.UNKNOWN)
        );

        entityManager.clear();

        assertThat(laboratoryRepository.findById(lab.getId()).orElseThrow().getRecruitmentStatus())
            .isEqualTo(RecruitmentStatus.UNKNOWN);
    }

    @Test
    void arbitraryRecruitmentStatusIsRejected() {
        Hierarchy h = hierarchy("제약대학", "제약학과", "제약교수", null);

        assertThatThrownBy(() -> jdbcTemplate.update("""
            INSERT INTO laboratory (
                professor_id, department_id, name, name_source, recruitment_status
            ) VALUES (?, ?, ?, 'OFFICIAL', ?)
            """, h.professor.getId(), h.department.getId(), "잘못된 모집상태 연구실", "UNDEFINED"))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void laboratoryNameSourceMustBeExplicitInDirectSql() {
        Hierarchy h = hierarchy("출처제약대학", "출처제약학과", "출처제약교수", null);

        assertThatThrownBy(() -> jdbcTemplate.update("""
            INSERT INTO laboratory (professor_id, department_id, name, recruitment_status)
            VALUES (?, ?, ?, 'UNKNOWN')
            """, h.professor.getId(), h.department.getId(), "출처 없는 연구실"))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void duplicateActiveLaboratoryNameInSameDepartmentIsRejectedByDatabase() {
        Hierarchy h = hierarchy("공학대학", "중복학과", "중복교수", null);
        laboratoryRepository.saveAndFlush(
            new Laboratory(h.professor, h.department, "같은 연구실", null, RecruitmentStatus.RECRUITING)
        );

        assertThatThrownBy(() -> laboratoryRepository.saveAndFlush(
            new Laboratory(h.professor, h.department, "같은 연구실", null, RecruitmentStatus.CLOSED)
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sameLaboratoryNameInDifferentDepartmentsIsAllowed() {
        College college = collegeRepository.save(new College("자유전공대학"));
        Department firstDepartment = departmentRepository.save(new Department(college, "제1학과"));
        Department secondDepartment = departmentRepository.save(new Department(college, "제2학과"));
        Professor firstProfessor = professorRepository.save(new Professor(firstDepartment, "1교수", null));
        Professor secondProfessor = professorRepository.save(new Professor(secondDepartment, "2교수", null));

        laboratoryRepository.save(new Laboratory(firstProfessor, firstDepartment, "공통 연구실", null, RecruitmentStatus.RECRUITING));
        laboratoryRepository.save(new Laboratory(secondProfessor, secondDepartment, "공통 연구실", null, RecruitmentStatus.UNKNOWN));

        assertThatCode(laboratoryRepository::flush).doesNotThrowAnyException();
    }

    @Test
    void deletedLaboratoriesDoNotBlockReusingTheName() {
        Hierarchy h = hierarchy("사회대학", "기록학과", "기록교수", null);
        Laboratory first = laboratoryRepository.saveAndFlush(
            new Laboratory(h.professor, h.department, "재사용 연구실", null, RecruitmentStatus.CLOSED)
        );
        first.softDelete();
        laboratoryRepository.flush();

        Laboratory second = laboratoryRepository.saveAndFlush(
            new Laboratory(h.professor, h.department, "재사용 연구실", null, RecruitmentStatus.UNKNOWN)
        );
        second.softDelete();

        assertThatCode(laboratoryRepository::flush).doesNotThrowAnyException();
    }

    @Test
    void professorEmailIsUniqueButMultipleNullsAreAllowed() {
        College college = collegeRepository.save(new College("교수이메일제약테스트대학"));
        Department department = departmentRepository.save(new Department(college, "컴퓨터공학과"));
        professorRepository.save(new Professor(department, "교수1", null));
        professorRepository.save(new Professor(department, "교수2", null));
        professorRepository.flush();
        Long professorCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM professor WHERE department_id = ?",
            Long.class,
            department.getId()
        );
        assertThat(professorCount).isEqualTo(2L);
    }

    @Test
    void duplicateProfessorEmailIsRejectedAfterNormalization() {
        College college = collegeRepository.save(new College("융합대학"));
        Department department = departmentRepository.save(new Department(college, "소프트웨어학과"));
        professorRepository.saveAndFlush(new Professor(department, "교수1", "USER@EXAMPLE.COM"));
        assertThatThrownBy(() -> professorRepository.saveAndFlush(new Professor(department, "교수2", " user@example.com ")))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void duplicateBookmarkAndResearchFieldMappingAreRejected() {
        Hierarchy h = hierarchy("자연대학", "수학과", "이교수", "lee@example.com");
        Laboratory lab = laboratoryRepository.saveAndFlush(new Laboratory(h.professor, h.department, "수리 연구실", null, RecruitmentStatus.RECRUITING));
        AppUser user = appUserRepository.saveAndFlush(new AppUser("student@example.com"));
        ResearchField field = researchFieldRepository.saveAndFlush(
            new ResearchField("제약조건 테스트 최적화")
        );
        bookmarkRepository.saveAndFlush(new Bookmark(user, lab));
        laboratoryResearchFieldRepository.saveAndFlush(new LaboratoryResearchField(lab, field));
        assertThat(bookmarkRepository.existsById(new BookmarkId(user.getId(), lab.getId()))).isTrue();
        assertThat(laboratoryResearchFieldRepository.existsById(new LaboratoryResearchFieldId(lab.getId(), field.getId()))).isTrue();
    }

    @Test
    void referencedProfessorCannotBePhysicallyDeleted() {
        Hierarchy h = hierarchy("의과대학", "의학과", "박교수", "park@example.com");
        laboratoryRepository.saveAndFlush(new Laboratory(h.professor, h.department, "의학 연구실", null, RecruitmentStatus.CLOSED));
        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM professor WHERE id = ?", h.professor.getId()))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void physicalLaboratoryDeletionCascadesMappingsAndBookmarks() {
        Hierarchy h = hierarchy("생명대학", "생명과학과", "최교수", "choi@example.com");
        Laboratory lab = laboratoryRepository.saveAndFlush(new Laboratory(h.professor, h.department, "생명 연구실", null, RecruitmentStatus.RECRUITING));
        ResearchField field = researchFieldRepository.saveAndFlush(new ResearchField("바이오"));
        AppUser user = appUserRepository.saveAndFlush(new AppUser("bio@example.com"));
        laboratoryResearchFieldRepository.saveAndFlush(new LaboratoryResearchField(lab, field));
        bookmarkRepository.saveAndFlush(new Bookmark(user, lab));
        jdbcTemplate.update("DELETE FROM laboratory WHERE id = ?", lab.getId());
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM laboratory_research_field WHERE laboratory_id = ?", Long.class, lab.getId())).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM bookmark WHERE laboratory_id = ?", Long.class, lab.getId())).isZero();
    }

    private Hierarchy hierarchy(String collegeName, String departmentName, String professorName, String email) {
        College college = collegeRepository.save(new College(collegeName));
        Department department = departmentRepository.save(new Department(college, departmentName));
        Professor professor = professorRepository.save(new Professor(department, professorName, email));
        return new Hierarchy(department, professor);
    }

    private record Hierarchy(Department department, Professor professor) { }
}

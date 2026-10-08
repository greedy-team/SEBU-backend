package com.sebu.backend.user.repository;

import com.sebu.backend.college.domain.College;
import com.sebu.backend.college.repository.CollegeRepository;
import com.sebu.backend.department.domain.Department;
import com.sebu.backend.department.repository.DepartmentRepository;
import com.sebu.backend.user.domain.AppUser;
import com.sebu.backend.user.domain.GpaBand;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class UserProfileSchemaIntegrationTest {
    @Autowired CollegeRepository collegeRepository;
    @Autowired DepartmentRepository departmentRepository;
    @Autowired AppUserRepository appUserRepository;
    @Autowired EntityManager entityManager;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void profileFieldsAreMappedToAppUser() {
        College college = collegeRepository.save(new College("프로필대학"));
        Department department = departmentRepository.save(new Department(college, "프로필학과"));
        AppUser user = appUserRepository.saveAndFlush(new AppUser(" Profile@Example.com "));
        LocalDateTime moderatedAt = LocalDateTime.of(2026, 8, 18, 12, 30);
        LocalDateTime profileUpdatedAt = moderatedAt.plusMinutes(1);
        LocalDateTime deletedAt = moderatedAt.plusDays(1);

        jdbcTemplate.update("""
            UPDATE app_user
            SET name = ?,
                nickname = ?,
                nickname_normalized = ?,
                grade = ?,
                major_department_id = ?,
                gpa_band = ?,
                introduction = ?,
                introduction_moderated_at = ?,
                introduction_policy_version = ?,
                introduction_provider_version = ?,
                profile_updated_at = ?,
                deleted_at = ?
            WHERE id = ?
            """,
            "홍길동",
            "길동이",
            "길동이",
            3,
            department.getId(),
            GpaBand.GTE_3_5.name(),
            "머신러닝과 컴퓨터 비전에 관심이 있습니다.",
            moderatedAt,
            "intro-content-policy-v1",
            "local-rules-v1",
            profileUpdatedAt,
            deletedAt,
            user.getId()
        );
        entityManager.clear();

        AppUser found = appUserRepository.findById(user.getId()).orElseThrow();
        assertThat(found.getEmail()).isEqualTo("profile@example.com");
        assertThat(found.getName()).isEqualTo("홍길동");
        assertThat(found.getNickname()).isEqualTo("길동이");
        assertThat(found.getNicknameNormalized()).isEqualTo("길동이");
        assertThat(found.getGrade()).isEqualTo((short) 3);
        assertThat(found.getMajorDepartment().getId()).isEqualTo(department.getId());
        assertThat(found.getGpaBand()).isEqualTo(GpaBand.GTE_3_5);
        assertThat(found.getIntroduction()).isEqualTo("머신러닝과 컴퓨터 비전에 관심이 있습니다.");
        assertThat(found.getIntroductionModeratedAt()).isEqualTo(moderatedAt);
        assertThat(found.getIntroductionPolicyVersion()).isEqualTo("intro-content-policy-v1");
        assertThat(found.getIntroductionProviderVersion()).isEqualTo("local-rules-v1");
        assertThat(found.getProfileUpdatedAt()).isEqualTo(profileUpdatedAt);
        assertThat(found.getDeletedAt()).isEqualTo(deletedAt);
    }

    @Test
    void newUserCanExistWithAnIncompleteProfile() {
        AppUser user = appUserRepository.saveAndFlush(new AppUser("new-user@example.com"));
        entityManager.clear();

        AppUser found = appUserRepository.findById(user.getId()).orElseThrow();
        assertThat(found.getName()).isNull();
        assertThat(found.getNickname()).isNull();
        assertThat(found.getNicknameNormalized()).isNull();
        assertThat(found.getVersion()).isZero();
        assertThat(found.getGrade()).isNull();
        assertThat(found.getMajorDepartment()).isNull();
        assertThat(found.getGpaBand()).isNull();
        assertThat(found.getIntroduction()).isEmpty();
        assertThat(found.getProfileUpdatedAt()).isNull();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 6})
    void gradeOutsideSupportedYearsAndGraduateIsRejected(int grade) {
        assertThatThrownBy(() -> jdbcTemplate.update(
            "INSERT INTO app_user (email, grade) VALUES (?, ?)",
            "invalid-grade-" + grade + "@example.com",
            grade
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void unsupportedGpaBandIsRejected() {
        assertThatThrownBy(() -> jdbcTemplate.update(
            "INSERT INTO app_user (email, gpa_band) VALUES (?, ?)",
            "invalid-gpa@example.com",
            "GTE_2_0"
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void unknownMajorDepartmentIsRejected() {
        assertThatThrownBy(() -> jdbcTemplate.update(
            "INSERT INTO app_user (email, major_department_id) VALUES (?, ?)",
            "invalid-major@example.com",
            Long.MAX_VALUE
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void introductionLongerThanFiveHundredCharactersIsRejected() {
        assertThatThrownBy(() -> jdbcTemplate.update(
            "INSERT INTO app_user (email, introduction) VALUES (?, ?)",
            "long-introduction@example.com",
            "가".repeat(501)
        )).isInstanceOf(DataIntegrityViolationException.class);
    }
}

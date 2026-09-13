package com.sebu.backend.user.repository;

import com.sebu.backend.user.domain.AcademicField;
import com.sebu.backend.user.domain.AppUser;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class AcademicFieldSchemaIntegrationTest {
    @Autowired AppUserRepository users;
    @Autowired EntityManager entityManager;
    @Autowired JdbcTemplate jdbc;

    @ParameterizedTest
    @EnumSource(AcademicField.class)
    void persistsEnumAsItsCodeAndReloadsIt(AcademicField field) {
        AppUser user = users.saveAndFlush(AppUser.sejong("academic-schema-" + field.name()));
        assertThat(user.getAcademicField()).isNull();
        user.selectAcademicField(field, LocalDateTime.of(2026, 9, 12, 10, 0));
        users.flush();
        entityManager.clear();

        assertThat(users.findById(user.getId()).orElseThrow().getAcademicField()).isEqualTo(field);
        assertThat(jdbc.queryForObject("SELECT academic_field FROM app_user WHERE id = ?", String.class, user.getId()))
            .isEqualTo(field.name());
    }
}

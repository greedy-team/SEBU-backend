package com.sebu.backend.laboratory.repository;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LaboratoryWebsiteUrlSourceMigrationTest {

    @Test
    void classifiesExistingUrlsAndRegistersParkGihoWebsiteAsManual() {
        DataSource dataSource = new DriverManagerDataSource(
            "jdbc:h2:mem:website-source-" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
            "sa",
            ""
        );
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .cleanDisabled(false)
            .target("45")
            .load()
            .migrate();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Long departmentId = jdbc.queryForObject(
            "SELECT id FROM department WHERE name = '컴퓨터공학과'",
            Long.class
        );
        jdbc.update(
            "INSERT INTO professor (department_id, name, email) VALUES (?, '박기호', 'ghpark@sejong.ac.kr')",
            departmentId
        );
        Long professorId = jdbc.queryForObject(
            "SELECT id FROM professor WHERE email = 'ghpark@sejong.ac.kr'",
            Long.class
        );
        jdbc.update("""
            INSERT INTO laboratory (
                professor_id, department_id, name, website_url,
                recruitment_status, name_source
            ) VALUES (?, ?, '박기호 교수님 연구실', NULL, 'UNKNOWN', 'GENERATED')
            """, professorId, departmentId);
        jdbc.update("""
            INSERT INTO laboratory (
                professor_id, department_id, name, website_url,
                recruitment_status, name_source, deleted_at
            ) VALUES (?, ?, '삭제된 박기호 연구실', 'https://example.com/deleted',
                'CLOSED', 'GENERATED', CURRENT_TIMESTAMP)
            """, professorId, departmentId);

        var migration = Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .target("46")
            .load()
            .migrate();

        assertThat(migration.migrationsExecuted).isOne();
        var active = jdbc.queryForMap("""
            SELECT website_url, website_url_source
            FROM laboratory
            WHERE professor_id = ? AND deleted_at IS NULL
            """, professorId);
        assertThat(active)
            .containsEntry("website_url", "https://sdl.sejong.ac.kr/")
            .containsEntry("website_url_source", "MANUAL");
        assertThat(jdbc.queryForObject("""
            SELECT website_url_source
            FROM laboratory
            WHERE professor_id = ? AND deleted_at IS NOT NULL
            """, String.class, professorId)).isEqualTo("CRAWLED");
        assertThatThrownBy(() -> jdbc.update("""
            UPDATE laboratory
            SET website_url_source = NULL
            WHERE professor_id = ? AND deleted_at IS NULL
            """, professorId)).isInstanceOf(DataIntegrityViolationException.class);
    }
}

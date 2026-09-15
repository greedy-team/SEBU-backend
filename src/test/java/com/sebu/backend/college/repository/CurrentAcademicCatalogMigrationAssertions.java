package com.sebu.backend.college.repository;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import com.sebu.backend.college.domain.CommunityCollegeGroup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

abstract class CurrentAcademicCatalogMigrationAssertions {
    protected abstract DriverManagerDataSource dataSource();

    protected Flyway flyway(String target) {
        var configuration = Flyway.configure().dataSource(dataSource())
            .locations("classpath:db/migration");
        if (target != null) {
            configuration.target(target);
        }
        return configuration.load();
    }

    @Test
    void emptyDatabaseContainsNineCollegesAndDistinctSchoolsAndMajors() {
        Flyway migration = flyway(null);
        migration.migrate();
        var jdbc = new JdbcTemplate(dataSource());

        assertThat(migration.info().pending()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM college", Integer.class)).isEqualTo(9);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM department", Integer.class)).isEqualTo(64);
        assertThat(jdbc.queryForList("""
            SELECT CONCAT(c.name, ':', COUNT(d.id))
            FROM college c LEFT JOIN department d ON d.college_id = c.id
            GROUP BY c.id, c.name
            """, String.class)).containsExactlyInAnyOrder(
                "인문과학대학:11", "사회과학대학:3", "경영경제대학:2",
                "호텔관광대학:6", "자연과학대학:3", "생명과학대학:5",
                "인공지능융합대학:15", "공과대학:13", "예체능대학:6"
            );
        assertThat(jdbc.queryForList("""
            SELECT d.name FROM department d JOIN college c ON c.id = d.college_id
            WHERE c.name = '생명과학대학'
            """, String.class)).containsExactlyInAnyOrder(
                "생명시스템학부", "식품생명공학전공", "바이오융합공학전공",
                "바이오산업자원공학전공", "스마트생명산업융합학과"
            );
        assertThat(jdbc.queryForList("SELECT name FROM department", String.class))
            .contains("국제학부", "국제일본학전공", "창의소프트학부", "디자인이노베이션전공",
                "우주항공시스템공학부", "항공시스템공학과", "콘텐츠소프트웨어학과")
            .doesNotContain("자유전공학부", "인문사회계열", "IT계열", "글로벌자유전공학부",
                "무인이동체공학전공", "소프트웨어학과", "콘텐츠소프트웨어학과/소프트웨어학과");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM crawl_source", Integer.class)).isEqualTo(12);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app_user", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("""
            SELECT COUNT(*) FROM department WHERE name IN (
                '영어영문학전공', '일어일문학전공', '법학전공', '글로벌조리학과', '수학전공', '응용통계학전공', '전자정보통신공학과', '소프트웨어학과', '데이터사이언스학과', '무인이동체공학전공', '스마트기기공학전공', '지능기전공학과', '인공지능학과', '건축공학전공', '건축학전공', '환경에너지공간융합학과', '지구자원시스템공학과', '기계공학전공', '항공우주공학전공', '항공시스템공학전공', '국방시스템공학과'
            )
            """, Integer.class)).isZero();
    }

    @Test
    void groupMigrationPreservesAcademicDataAndOnlyClassifiesApprovedColleges() throws Exception {
        flyway("42").migrate();
        var jdbc = new JdbcTemplate(dataSource());
        jdbc.update("INSERT INTO college (name) VALUES ('미확정 칼리지')");
        var departments = jdbc.queryForList("SELECT * FROM department ORDER BY id");
        var colleges = jdbc.queryForList("SELECT id, name, created_at, updated_at FROM college ORDER BY id");
        flyway(null).migrate();

        assertThat(jdbc.queryForList("SELECT * FROM department ORDER BY id")).isEqualTo(departments);
        assertThat(jdbc.queryForList("SELECT id, name, created_at, updated_at FROM college ORDER BY id"))
            .isEqualTo(colleges);
        assertThat(jdbc.queryForList(
            "SELECT CONCAT(name, ':', community_group) FROM college WHERE community_group IS NOT NULL",
            String.class)).containsExactlyInAnyOrder(
                "인문과학대학:HUMANITIES_SOCIAL", "사회과학대학:HUMANITIES_SOCIAL",
                "경영경제대학:BUSINESS_HOSPITALITY", "호텔관광대학:BUSINESS_HOSPITALITY",
                "자연과학대학:NATURAL_LIFE", "생명과학대학:NATURAL_LIFE",
                "인공지능융합대학:AI_CONVERGENCE", "공과대학:ENGINEERING", "예체능대학:ARTS_PHYSICAL");
        assertThat(jdbc.queryForObject(
            "SELECT community_group FROM college WHERE name = '미확정 칼리지'", String.class)).isNull();
        try (var connection = dataSource().getConnection(); var statement = connection.createStatement()) {
            assertThatThrownBy(() -> statement.executeUpdate(
                "UPDATE college SET community_group = 'UNKNOWN' WHERE name = '미확정 칼리지'"))
                .isInstanceOf(SQLException.class);
            for (CommunityCollegeGroup group : CommunityCollegeGroup.values()) {
                statement.executeUpdate("UPDATE college SET community_group = '" + group.name()
                    + "' WHERE name = '미확정 칼리지'");
            }
            statement.executeUpdate("UPDATE college SET community_group = NULL WHERE name = '미확정 칼리지'");
        }
    }

    @Test
    void upgradePreservesDevelopmentCatalogIdsCustomRowsAndUserAffiliation() throws Exception {
        flyway("41").migrate();
        var jdbc = new JdbcTemplate(dataSource());
        jdbc.update("INSERT INTO college (id, name) VALUES (2, '자연과학대학'), (3, '생명과학대학')");
        jdbc.update("""
            INSERT INTO department (id, college_id, name) VALUES
            (13, 2, '수학통계학과'), (14, 2, '물리천문학과'), (15, 2, '화학과'),
            (16, 3, '식품생명공학전공'), (17, 3, '바이오융합공학전공'),
            (18, 3, '바이오산업자원공학전공'), (19, 3, '스마트생명산업융합학과')
            """);
        jdbc.update("INSERT INTO college (id, name) VALUES (88, '기존 별도 대학')");
        jdbc.update("INSERT INTO department (id, college_id, name) VALUES (900, 88, '컴퓨터공학과')");
        jdbc.update("""
            INSERT INTO app_user (provider, provider_user_id, major_department_id, sejong_department_name)
            VALUES ('SEJONG', '21000000', 16, '식품생명공학전공')
            """);
        var colleges = jdbc.queryForList("SELECT id, name, created_at, updated_at FROM college ORDER BY id");
        var departments = jdbc.queryForList("SELECT * FROM department ORDER BY id");
        var users = jdbc.queryForList("SELECT * FROM app_user ORDER BY id");
        var sources = jdbc.queryForList("SELECT * FROM crawl_source ORDER BY id");

        flyway(null).migrate();

        assertThat(jdbc.queryForList("SELECT id, name, created_at, updated_at FROM college ORDER BY id")).containsAll(colleges);
        assertThat(jdbc.queryForList("SELECT * FROM department ORDER BY id")).containsAll(departments);
        assertThat(jdbc.queryForList("SELECT * FROM app_user ORDER BY id")).isEqualTo(users);
        assertThat(jdbc.queryForList("SELECT * FROM crawl_source ORDER BY id")).isEqualTo(sources);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM college", Integer.class)).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM department", Integer.class)).isEqualTo(65);

        // Prove the seed itself is additive when rows already exist, independently of Flyway history.
        var seededColleges = jdbc.queryForList("SELECT * FROM college ORDER BY id");
        var seededDepartments = jdbc.queryForList("SELECT * FROM department ORDER BY id");
        try (var connection = dataSource().getConnection()) {
            ScriptUtils.executeSqlScript(connection, new EncodedResource(
                new ClassPathResource("db/migration/V42__seed_current_academic_catalog.sql"),
                StandardCharsets.UTF_8));
        }
        assertThat(jdbc.queryForList("SELECT * FROM college ORDER BY id")).isEqualTo(seededColleges);
        assertThat(jdbc.queryForList("SELECT * FROM department ORDER BY id")).isEqualTo(seededDepartments);
    }
}

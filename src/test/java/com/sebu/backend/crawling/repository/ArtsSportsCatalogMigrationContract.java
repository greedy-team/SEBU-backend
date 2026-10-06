package com.sebu.backend.crawling.repository;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

abstract class ArtsSportsCatalogMigrationContract {
    private static final String SOURCE = "https://dept.sejong.ac.kr/picdpt/intro/professor.do";
    private static final String MIGRATION = "db/migration/V48__import_reviewed_arts_sports_professors.sql";
    private static final Map<String, Integer> DEPARTMENT_COUNTS = Map.of(
        "회화과", 1, "패션디자인학과", 3, "음악과", 5,
        "체육학과", 2, "무용과", 1, "영화예술학과", 6);
    private JdbcTemplate jdbc;

    protected abstract DataSource dataSource();

    @BeforeEach
    void prepareDedicatedDatabase() {
        jdbc = new JdbcTemplate(dataSource());
        flyway("48").clean();
    }

    @Test
    void blankDatabaseImportsReviewedValuesAndSupportsIdempotentRetryAndHibernateValidation() throws IOException {
        flyway("48").migrate();

        assertReviewedCatalogue();
        assertEveryReviewedCsvValue();
        assertThat(count("professor_crawl_candidate")).isZero();
        assertThat(count("laboratory_research_field_candidate")).isZero();
        flyway("48").validate();
        assertThat(flyway("48").migrate().migrationsExecuted).isZero();

        var beforeRetry = snapshotCanonicalData();
        var retry = new ResourceDatabasePopulator(new ClassPathResource(MIGRATION));
        retry.setSqlScriptEncoding("UTF-8");
        retry.execute(dataSource());
        assertThat(snapshotCanonicalData()).isEqualTo(beforeRetry);
        assertReviewedCatalogue();
        validateHibernate();
    }

    @Test
    void upgradeFromV47PreservesAcademicIdsUsersExistingProfilesAndResearchLinks() {
        flyway("47").migrate();
        long previousDepartment = department("AI융합전자공학과");
        jdbc.update("""
            INSERT INTO app_user (provider,provider_user_id,major_department_id,sejong_department_name)
            VALUES ('SEJONG','v48-affiliation-test',?,'회화과')
            """, department("회화과"));
        jdbc.update("""
            INSERT INTO professor (id,department_id,name,email,position)
            VALUES (9001,?,'정재호','runner23@sejong.ac.kr','기존 직위')
            """, previousDepartment);
        jdbc.update("""
            INSERT INTO laboratory (id,professor_id,department_id,name,website_url,website_url_source,
                description,recruitment_status,name_source)
            VALUES (9002,9001,?,'검수된 공식 연구실','https://example.com/official','MANUAL',
                '기존 검수 소개','RECRUITING','OFFICIAL')
            """, previousDepartment);
        jdbc.update("INSERT INTO professor_department (professor_id,department_id,position) VALUES (9001,?,'기존 직위')", previousDepartment);
        jdbc.update("INSERT INTO laboratory_department (laboratory_id,department_id) VALUES (9002,?)", previousDepartment);
        jdbc.update("INSERT INTO research_field (id,name) VALUES (9003,'기존 예술 연구 분야')");
        jdbc.update("INSERT INTO laboratory_research_field (laboratory_id,research_field_id) VALUES (9002,9003)");
        jdbc.update("""
            INSERT INTO crawl_source (department_id,source_name,source_url,parser_type,version)
            VALUES (?,'기존 회화과 수집 설정',?,'SEJONG_STANDARD',7)
            """, department("회화과"), SOURCE);
        var before = snapshotCanonicalData();

        assertThat(flyway("48").migrate().migrationsExecuted).isEqualTo(1);

        var after = snapshotCanonicalData();
        for (String unchanged : List.of("college", "department", "app_user", "research_field",
            "research_field_category", "research_field_category_mapping", "laboratory_research_field")) {
            assertThat(after.get(unchanged)).as(unchanged).isEqualTo(before.get(unchanged));
        }
        for (String extended : List.of("professor", "laboratory", "professor_department",
            "laboratory_department", "crawl_source")) {
            assertThat(after.get(extended)).as(extended).containsAll(before.get(extended));
        }
        assertThat(count("professor")).isEqualTo(before.get("professor").size() + 17);
        assertThat(count("laboratory")).isEqualTo(before.get("laboratory").size() + 17);
        assertThat(count("crawl_source")).isEqualTo(before.get("crawl_source").size() + 5);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM professor_department WHERE professor_id=9001", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM laboratory_department WHERE laboratory_id=9002", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM professor_department WHERE professor_id=9001 AND department_id=?", Integer.class, department("회화과"))).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM laboratory_department WHERE laboratory_id=9002 AND department_id=?", Integer.class, department("회화과"))).isEqualTo(1);
        flyway("48").validate();
        validateHibernate();
    }

    @Test
    void conflictingEmailIdentityFailsBeforeCanonicalWrites() {
        flyway("47").migrate();
        jdbc.update("INSERT INTO professor (department_id,name,email) VALUES (?,'다른 사람','runner23@sejong.ac.kr')", department("회화과"));
        assertFailsWithoutCanonicalWrites();
    }

    @Test
    void conflictingSourceDepartmentIsNotMoved() {
        flyway("47").migrate();
        jdbc.update("""
            INSERT INTO crawl_source (department_id,source_name,source_url,parser_type,version)
            VALUES (?,'다른 학과 수집 설정',?,'SEJONG_STANDARD',0)
            """, department("음악과"), SOURCE);
        assertFailsWithoutCanonicalWrites();
    }

    @Test
    void deletedLaboratoryIsNotResurrected() {
        flyway("47").migrate();
        insertReviewedProfessor();
        jdbc.update("""
            INSERT INTO laboratory (professor_id,department_id,name,recruitment_status,name_source,deleted_at)
            VALUES (9001,?,'삭제된 연구실','CLOSED','OFFICIAL',CURRENT_TIMESTAMP)
            """, department("회화과"));
        assertFailsWithoutCanonicalWrites();
    }

    @Test
    void multipleActiveLaboratoriesRequireResolution() {
        flyway("47").migrate();
        insertReviewedProfessor();
        for (String name : List.of("공식 연구실 A", "공식 연구실 B")) {
            jdbc.update("""
                INSERT INTO laboratory (professor_id,department_id,name,recruitment_status,name_source)
                VALUES (9001,?,?,'UNKNOWN','OFFICIAL')
                """, department("회화과"), name);
        }
        assertFailsWithoutCanonicalWrites();
    }

    @Test
    void laboratoryNameOwnedByAnotherProfessorFailsBeforeCanonicalWrites() {
        flyway("47").migrate();
        jdbc.update("INSERT INTO professor (id,department_id,name,email) VALUES (9001,?,'동명이인','other@example.com')", department("회화과"));
        jdbc.update("""
            INSERT INTO laboratory (professor_id,department_id,name,recruitment_status,name_source)
            VALUES (9001,?,'정재호 교수님 연구실','UNKNOWN','OFFICIAL')
            """, department("회화과"));
        assertFailsWithoutCanonicalWrites();
    }

    @Test
    void missingAcademicDepartmentFailsInsteadOfRecreatingIt() {
        flyway("47").migrate();
        jdbc.update("DELETE FROM department WHERE id=?", department("회화과"));
        assertFailsWithoutCanonicalWrites();
    }

    private void assertReviewedCatalogue() {
        assertThat(count("professor")).isEqualTo(321);
        assertThat(count("laboratory")).isEqualTo(321);
        assertThat(count("professor_department")).isEqualTo(353);
        assertThat(count("laboratory_department")).isEqualTo(353);
        assertThat(count("crawl_source")).isEqualTo(48);
        assertThat(count("college")).isEqualTo(9);
        assertThat(count("department")).isEqualTo(64);
        for (var entry : DEPARTMENT_COUNTS.entrySet()) {
            long departmentId = department(entry.getKey());
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM professor_department WHERE department_id=?", Integer.class, departmentId))
                .as(entry.getKey() + " 교수 소속").isEqualTo(entry.getValue());
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM laboratory_department WHERE department_id=?", Integer.class, departmentId))
                .as(entry.getKey() + " 연구실 소속").isEqualTo(entry.getValue());
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM crawl_source WHERE department_id=? AND parser_type='SEJONG_STANDARD'", Integer.class, departmentId))
                .as(entry.getKey() + " 크롤링 소스").isEqualTo(1);
        }
        assertThat(jdbc.queryForObject("""
            SELECT COUNT(*) FROM laboratory l JOIN department d ON d.id=l.department_id
            JOIN college c ON c.id=d.college_id WHERE c.name='예체능대학'
            AND l.name_source='GENERATED' AND l.recruitment_status='UNKNOWN'
            """, Integer.class)).isEqualTo(18);
        assertThat(jdbc.queryForObject("""
            SELECT COUNT(*) FROM laboratory l JOIN department d ON d.id=l.department_id
            JOIN college c ON c.id=d.college_id WHERE c.name='예체능대학'
            AND l.website_url IS NOT NULL AND l.website_url_source='CRAWLED'
            """, Integer.class)).isEqualTo(5);
        assertThat(jdbc.queryForObject("""
            SELECT COUNT(*) FROM laboratory l JOIN department d ON d.id=l.department_id
            JOIN college c ON c.id=d.college_id WHERE c.name='예체능대학'
            AND l.website_url IS NULL AND l.website_url_source IS NULL
            """, Integer.class)).isEqualTo(13);
        assertThat(jdbc.queryForList("""
            SELECT p.name FROM laboratory l JOIN professor p ON p.id=l.professor_id
            JOIN department d ON d.id=l.department_id JOIN college c ON c.id=d.college_id
            WHERE c.name='예체능대학' AND l.description IS NULL ORDER BY p.name
            """, String.class)).containsExactlyInAnyOrder("김기훈", "이은경", "안연석");
        assertThat(jdbc.queryForObject("SELECT email FROM professor WHERE name='최두영'", String.class)).isEqualTo("filmdoo@sejong.ac.kr");
        assertThat(jdbc.queryForObject("SELECT position FROM professor WHERE email='kimbm@sejong.ac.kr'", String.class)).isEqualTo("초빙교수");
        assertThat(jdbc.queryForObject("SELECT position FROM professor WHERE email='ysahn@sejong.ac.kr'", String.class)).isEqualTo("특임교수");
        assertThat(jdbc.queryForObject("SELECT website_url FROM laboratory l JOIN professor p ON p.id=l.professor_id WHERE p.email='hyoungnam@sejong.ac.kr'", String.class)).isNull();
        assertThat(jdbc.queryForObject("SELECT website_url FROM laboratory l JOIN professor p ON p.id=l.professor_id WHERE p.email='ksjina@sejong.ac.kr'", String.class))
            .isEqualTo("https://prof.sejong.ac.kr/ksjina/");
        assertThat(jdbc.queryForObject("SELECT description FROM laboratory l JOIN professor p ON p.id=l.professor_id WHERE p.email='runner23@sejong.ac.kr'", String.class)).isEqualTo("한국화");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name LIKE 'v48_catalog_%'", Integer.class)).isZero();
    }

    private void insertReviewedProfessor() {
        jdbc.update("INSERT INTO professor (id,department_id,name,email) VALUES (9001,?,'정재호','runner23@sejong.ac.kr')", department("회화과"));
    }

    private void assertEveryReviewedCsvValue() throws IOException {
        var rows = readCsv(Path.of("docs/data/arts-sports-professors-reviewed.csv"));
        assertThat(rows).hasSize(19);
        var header = rows.getFirst();
        for (var columns : rows.subList(1, rows.size())) {
            assertThat(columns).hasSize(header.size());
            var reviewed = new LinkedHashMap<String, String>();
            for (int index = 0; index < header.size(); index++) {
                reviewed.put(header.get(index), columns.get(index).isEmpty() ? null : columns.get(index));
            }
            assertThat(reviewed.get("review_status")).isEqualTo("APPROVED");
            var expected = new LinkedHashMap<String, Object>();
            for (String field : List.of("college_name", "department_name", "source_url", "parser_type",
                "professor_name", "position", "email", "research_introduction", "homepage_url",
                "laboratory_name", "name_source")) {
                expected.put(field, reviewed.get(field));
            }
            expected.put("website_url_source", reviewed.get("homepage_url") == null ? null : "CRAWLED");
            var actual = jdbc.queryForMap("""
                SELECT c.name AS college_name,d.name AS department_name,s.source_url,s.parser_type,
                    p.name AS professor_name,p.position,p.email,l.description AS research_introduction,
                    l.website_url AS homepage_url,l.name AS laboratory_name,l.name_source,l.website_url_source
                FROM professor p JOIN laboratory l ON l.professor_id=p.id
                JOIN professor_department pd ON pd.professor_id=p.id
                JOIN laboratory_department ld ON ld.laboratory_id=l.id AND ld.department_id=pd.department_id
                JOIN department d ON d.id=pd.department_id JOIN college c ON c.id=d.college_id
                JOIN crawl_source s ON s.department_id=d.id
                WHERE p.email=? AND s.source_url=? AND l.deleted_at IS NULL
                """, reviewed.get("email"), reviewed.get("source_url"));
            assertThat(actual).as(reviewed.get("professor_name") + " 검수 CSV와 배포 값").isEqualTo(expected);
        }
    }

    private List<List<String>> readCsv(Path path) throws IOException {
        String text = Files.readString(path, StandardCharsets.UTF_8);
        var rows = new ArrayList<List<String>>();
        var columns = new ArrayList<String>();
        var value = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (current == '"') {
                if (quoted && index + 1 < text.length() && text.charAt(index + 1) == '"') {
                    value.append('"');
                    index++;
                } else {
                    quoted = !quoted;
                }
            } else if (!quoted && (current == ',' || current == '\n' || current == '\r')) {
                columns.add(value.toString());
                value.setLength(0);
                if (current != ',') {
                    rows.add(new ArrayList<>(columns));
                    columns.clear();
                    if (current == '\r' && index + 1 < text.length() && text.charAt(index + 1) == '\n') index++;
                }
            } else {
                value.append(current);
            }
        }
        assertThat(quoted).as("검수 CSV의 따옴표가 모두 닫혀 있어야 한다").isFalse();
        if (!columns.isEmpty() || !value.isEmpty()) {
            columns.add(value.toString());
            rows.add(columns);
        }
        return rows;
    }

    private void assertFailsWithoutCanonicalWrites() {
        var before = snapshotCanonicalData();
        assertThatThrownBy(() -> flyway("48").migrate()).isInstanceOf(RuntimeException.class);
        assertThat(snapshotCanonicalData()).isEqualTo(before);
    }

    private Map<String, List<Map<String, Object>>> snapshotCanonicalData() {
        var snapshot = new LinkedHashMap<String, List<Map<String, Object>>>();
        for (String table : List.of("college", "department", "app_user", "professor", "laboratory",
            "crawl_source", "research_field", "research_field_category")) {
            snapshot.put(table, jdbc.queryForList("SELECT * FROM " + table + " ORDER BY id"));
        }
        snapshot.put("professor_department", jdbc.queryForList("SELECT * FROM professor_department ORDER BY professor_id,department_id"));
        snapshot.put("laboratory_department", jdbc.queryForList("SELECT * FROM laboratory_department ORDER BY laboratory_id,department_id"));
        snapshot.put("laboratory_research_field", jdbc.queryForList("SELECT * FROM laboratory_research_field ORDER BY laboratory_id,research_field_id"));
        snapshot.put("research_field_category_mapping", jdbc.queryForList("SELECT * FROM research_field_category_mapping ORDER BY research_field_id,category_id"));
        return snapshot;
    }

    private long department(String name) {
        return jdbc.queryForObject("SELECT id FROM department WHERE name=?", Long.class, name);
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private Flyway flyway(String target) {
        return Flyway.configure().dataSource(dataSource()).locations("classpath:db/migration")
            .cleanDisabled(false).target(target).load();
    }

    private void validateHibernate() {
        var factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource());
        factory.setPackagesToScan("com.sebu.backend");
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "validate",
            "hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"));
        try {
            factory.afterPropertiesSet();
            assertThat(factory.getObject()).isNotNull();
            assertThat(factory.getObject().isOpen()).isTrue();
        } finally {
            factory.destroy();
        }
    }
}

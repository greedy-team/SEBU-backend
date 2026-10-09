package com.sebu.backend.laboratory.repository;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

abstract class PhysicsAstronomyLaboratoryLinksMigrationContract {
    private static final String MIGRATION = "db/migration/V50__add_missing_physics_astronomy_laboratory_links.sql";
    private JdbcTemplate jdbc;

    protected abstract DataSource dataSource();

    @BeforeEach
    void prepareDedicatedDatabase() {
        jdbc = new JdbcTemplate(dataSource());
        flyway("50").clean();
    }

    @Test
    void blankDatabaseMigratesWithoutCreatingMissingProfessorsAndValidatesHibernate() {
        flyway("49").migrate();
        var before = snapshotData();

        assertThat(flyway("50").migrate().migrationsExecuted).isOne();

        assertThat(snapshotData()).isEqualTo(before);
        flyway("50").validate();
        assertThat(flyway("50").migrate().migrationsExecuted).isZero();
        validateHibernate();
    }

    @ParameterizedTest
    @CsvSource({
        "김용선,yongsun@sejong.ac.kr,https://prof.sejong.ac.kr/yongsun/index.do",
        "김광희,gkim@sejong.ac.kr,https://prof.sejong.ac.kr/gkim/index.do",
        "김세용,skim@sejong.ac.kr,https://prof.sejong.ac.kr/skim/index.do",
        "정형채,hcj@sejong.ac.kr,https://prof.sejong.ac.kr/hcj/index.do",
        "홍석륜,hong@sejong.ac.kr,https://prof.sejong.ac.kr/hong/index.do",
        "천승현,schun@sejong.ac.kr,https://prof.sejong.ac.kr/schun/index.do",
        "채규현,chae@sejong.ac.kr,https://prof.sejong.ac.kr/chae/index.do",
        "이남경,lee@sejong.ac.kr,https://prof.sejong.ac.kr/lee/index.do",
        "최희진,hjchoi@sejong.ac.kr,https://prof.sejong.ac.kr/hjchoi/index.do",
        "김근수,kskim2676@sejong.ac.kr,https://prof.sejong.ac.kr/kskim2676/index.do",
        "서순애,sunaeseo@sejong.ac.kr,https://prof.sejong.ac.kr/sunaeseo/index.do",
        "Maurice VanPutten,mvp@sejong.ac.kr,https://prof.sejong.ac.kr/mvp/index.do",
        "오새한슬,saehanseul.oh@sejong.ac.kr,https://prof.sejong.ac.kr/saehanseul_oh/index.do",
        "김경호,kyungho@sejong.ac.kr,https://app.rndcircle.io/lab/e514af30-b319-405d-a5cd-7ac0d197a25e"
    })
    void upgradeAddsManualLinkWhilePreservingIdentityReviewBookmarkAndResearchFields(
        String professorName, String professorEmail, String websiteUrl
    ) {
        flyway("49").migrate();
        long physics = department("물리천문학과");
        insertProfessor(professorName, professorEmail, physics);
        insertLaboratory(physics, professorName + " 교수님 연구실", false);
        jdbc.update("INSERT INTO professor_department (professor_id,department_id) VALUES (9001,?)", physics);
        jdbc.update("INSERT INTO laboratory_department (laboratory_id,department_id) VALUES (9002,?)", physics);
        jdbc.update("INSERT INTO research_field (id,name) VALUES (9003,'링크 보정 전 연구 분야')");
        jdbc.update("INSERT INTO laboratory_research_field (laboratory_id,research_field_id) VALUES (9002,9003)");
        jdbc.update("INSERT INTO app_user (id,provider,provider_user_id) VALUES (9004,'SEJONG','v50-link-test-user')");
        jdbc.update("INSERT INTO bookmark (user_id,laboratory_id) VALUES (9004,9002)");
        jdbc.update("""
            INSERT INTO laboratory_review (id,laboratory_id,author_id,category,research_intensity,
                compensation,atmosphere,content,participation_year,participation_term)
            VALUES (9005,9002,9004,'RESEARCH_ENVIRONMENT','MEDIUM','NONE','NORMAL',
                '링크 보정 이후에도 유지해야 하는 후기',2026,'FIRST_SEMESTER')
            """);
        jdbc.update("INSERT INTO laboratory_review_tag (review_id,tag) VALUES (9005,'FREE_ATMOSPHERE')");
        var before = snapshotData();

        assertThat(flyway("50").migrate().migrationsExecuted).isOne();

        var updated = jdbc.queryForMap("SELECT * FROM laboratory WHERE id=9002");
        assertThat(updated).containsEntry("website_url", websiteUrl).containsEntry("website_url_source", "MANUAL");
        assertThat(jdbc.queryForObject("SELECT updated_at FROM laboratory WHERE id=9002", Timestamp.class))
            .isAfter(Timestamp.valueOf("2001-01-01 00:00:00"));
        var expectedLaboratory = before.get("laboratory").stream()
            .filter(row -> ((Number) row.get("id")).longValue() == 9002).findFirst().orElseThrow();
        expectedLaboratory.put("website_url", websiteUrl);
        expectedLaboratory.put("website_url_source", "MANUAL");
        expectedLaboratory.put("updated_at", updated.get("updated_at"));
        assertThat(snapshotData()).isEqualTo(before);
        flyway("50").validate();
        assertThat(flyway("50").migrate().migrationsExecuted).isZero();

        // A repeated SQL execution must not even refresh the row's timestamp.
        jdbc.update("UPDATE laboratory SET updated_at='2002-01-01 00:00:00' WHERE id=9002");
        var beforeRetry = snapshotData();
        var retry = new ResourceDatabasePopulator(new ClassPathResource(MIGRATION));
        retry.setSqlScriptEncoding("UTF-8");
        retry.execute(dataSource());
        assertThat(snapshotData()).isEqualTo(beforeRetry);
    }

    @Test
    void existingWebsiteIsNotOverwritten() {
        flyway("49").migrate();
        long physics = department("물리천문학과");
        insertProfessor("김경호", "kyungho@sejong.ac.kr", physics);
        insertLaboratory(physics, "김경호 교수님 연구실", false);
        jdbc.update("""
            UPDATE laboratory SET website_url='https://example.com/reviewed-official-lab',
                website_url_source='MANUAL' WHERE id=9002
            """);
        assertUpgradeLeavesDataUnchanged();
    }

    @Test
    void deletedLaboratoryIsNotRestoredOrUpdated() {
        flyway("49").migrate();
        long physics = department("물리천문학과");
        insertProfessor("김경호", "kyungho@sejong.ac.kr", physics);
        insertLaboratory(physics, "김경호 교수님 연구실", true);
        assertUpgradeLeavesDataUnchanged();
    }

    @Test
    void namesakeWithDifferentEmailIsNotUpdated() {
        flyway("49").migrate();
        long physics = department("물리천문학과");
        insertProfessor("김경호", "namesake@example.com", physics);
        insertLaboratory(physics, "김경호 교수님 연구실", false);
        assertUpgradeLeavesDataUnchanged();
    }

    @Test
    void mismatchedProfessorNameIsNotUpdated() {
        flyway("49").migrate();
        long physics = department("물리천문학과");
        insertProfessor("다른 교수", "kyungho@sejong.ac.kr", physics);
        insertLaboratory(physics, "김경호 교수님 연구실", false);
        assertUpgradeLeavesDataUnchanged();
    }

    @Test
    void laboratoryInDifferentDepartmentIsNotUpdated() {
        flyway("49").migrate();
        insertProfessor("김경호", "kyungho@sejong.ac.kr", department("물리천문학과"));
        insertLaboratory(department("화학과"), "김경호 교수님 연구실", false);
        assertUpgradeLeavesDataUnchanged();
    }

    @Test
    void sameDepartmentNameInDifferentCollegeIsNotUpdated() {
        flyway("49").migrate();
        jdbc.update("INSERT INTO department (id,college_id,name) SELECT 9006,id,'물리천문학과' FROM college WHERE name='공과대학'");
        insertProfessor("김경호", "kyungho@sejong.ac.kr", 9006);
        insertLaboratory(9006, "김경호 교수님 연구실", false);
        assertUpgradeLeavesDataUnchanged();
    }

    @Test
    void laboratoryWithDifferentNameIsNotUpdated() {
        flyway("49").migrate();
        long physics = department("물리천문학과");
        insertProfessor("김경호", "kyungho@sejong.ac.kr", physics);
        insertLaboratory(physics, "별도로 검수한 연구실", false);
        assertUpgradeLeavesDataUnchanged();
    }

    private void insertProfessor(String name, String email, long departmentId) {
        jdbc.update("INSERT INTO professor (id,department_id,name,email,position) VALUES (9001,?,?,?,'교수')",
            departmentId, name, email);
    }

    private void insertLaboratory(long departmentId, String name, boolean deleted) {
        jdbc.update("""
            INSERT INTO laboratory (id,professor_id,department_id,name,recruitment_status,name_source,
                description,deleted_at,created_at,updated_at)
            VALUES (9002,9001,?,?,'RECRUITING','GENERATED','기존 연구실 소개',?,
                '2001-01-01 00:00:00','2001-01-01 00:00:00')
            """, departmentId, name, deleted ? Timestamp.valueOf("2002-01-01 00:00:00") : null);
    }

    private void assertUpgradeLeavesDataUnchanged() {
        var before = snapshotData();
        assertThat(flyway("50").migrate().migrationsExecuted).isOne();
        assertThat(snapshotData()).isEqualTo(before);
    }

    private Map<String, List<Map<String, Object>>> snapshotData() {
        var snapshot = new LinkedHashMap<String, List<Map<String, Object>>>();
        for (String table : List.of("college", "department", "professor", "laboratory", "app_user",
            "research_field", "laboratory_review")) {
            snapshot.put(table, jdbc.queryForList("SELECT * FROM " + table + " ORDER BY id"));
        }
        snapshot.put("professor_department", jdbc.queryForList("SELECT * FROM professor_department ORDER BY professor_id,department_id"));
        snapshot.put("laboratory_department", jdbc.queryForList("SELECT * FROM laboratory_department ORDER BY laboratory_id,department_id"));
        snapshot.put("laboratory_research_field", jdbc.queryForList("SELECT * FROM laboratory_research_field ORDER BY laboratory_id,research_field_id"));
        snapshot.put("bookmark", jdbc.queryForList("SELECT * FROM bookmark ORDER BY user_id,laboratory_id"));
        snapshot.put("laboratory_review_tag", jdbc.queryForList("SELECT * FROM laboratory_review_tag ORDER BY review_id,tag"));
        return snapshot;
    }

    private long department(String name) {
        return jdbc.queryForObject("SELECT id FROM department WHERE name=?", Long.class, name);
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

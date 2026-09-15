package com.sebu.backend.crawling.repository;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

import javax.sql.DataSource;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

abstract class MixedCollegeCatalogMigrationContract {
    private static final String SOURCE = "https://dept.sejong.ac.kr/archeng/intro/professor.do";
    protected JdbcTemplate jdbc;

    protected abstract DataSource dataSource();

    @BeforeEach
    void prepareDedicatedDatabase() {
        jdbc = new JdbcTemplate(dataSource());
        flyway("44").clean();
    }

    @Test
    void blankDatabaseImportsCanonicalCatalogueAndAffiliationsAndValidatesHibernate() {
        flyway("44").migrate();
        assertCatalogue();
        assertThat(count("professor_crawl_candidate")).isZero();
        assertThat(count("laboratory_research_field_candidate")).isZero();
        validateHibernate();
        flyway("44").validate();
        assertThat(flyway("44").migrate().migrationsExecuted).isZero();

        var professors = jdbc.queryForList("SELECT * FROM professor ORDER BY id");
        var laboratories = jdbc.queryForList("SELECT * FROM laboratory ORDER BY id");
        var retry = new ResourceDatabasePopulator(new ClassPathResource(
            "db/migration/V44__import_reviewed_mixed_college_professors.sql"));
        retry.setSqlScriptEncoding("UTF-8");
        retry.execute(dataSource());
        assertCatalogue();
        assertThat(jdbc.queryForList("SELECT * FROM professor ORDER BY id")).isEqualTo(professors);
        assertThat(jdbc.queryForList("SELECT * FROM laboratory ORDER BY id")).isEqualTo(laboratories);
    }

    @Test
    void upgradeFromCommunityCollegeGroupsPreservesAcademicCatalogAndUserAffiliation() {
        flyway("43").migrate();
        jdbc.update("""
            INSERT INTO app_user (provider, provider_user_id, major_department_id, sejong_department_name)
            VALUES ('SEJONG', 'v44-affiliation-test', ?, 'AI융합전자공학과')
            """, department());
        var colleges = jdbc.queryForList("SELECT * FROM college ORDER BY id");
        var departments = jdbc.queryForList("SELECT * FROM department ORDER BY id");
        var users = jdbc.queryForList("SELECT * FROM app_user ORDER BY id");
        assertThat(colleges).hasSize(9);
        assertThat(departments).hasSize(64);
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM college WHERE community_group IS NOT NULL", Integer.class))
            .isEqualTo(9);

        var migration = flyway("44").migrate();

        assertThat(migration.migrationsExecuted).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT * FROM college ORDER BY id")).isEqualTo(colleges);
        assertThat(jdbc.queryForList("SELECT * FROM department ORDER BY id")).isEqualTo(departments);
        assertThat(jdbc.queryForList("SELECT * FROM app_user ORDER BY id")).isEqualTo(users);
        assertCatalogue();
        validateHibernate();
        flyway("44").validate();
    }

    @Test
    void upgradePreservesExistingProfilesIdsAndResearchLinksWhileAddingSharedAffiliations() {
        flyway("41").migrate();
        long department = department();
        jdbc.update("INSERT INTO professor (id,department_id,name,email,position) VALUES (9001,?,'임준석','jslim@sejong.ac.kr','기존 직위')", department);
        jdbc.update("""
            INSERT INTO laboratory (id,professor_id,department_id,name,website_url,description,recruitment_status,name_source)
            VALUES (9002,9001,?,'검수된 공식 연구실','https://example.com/official','기존 검수 소개','RECRUITING','OFFICIAL')
            """, department);
        jdbc.update("INSERT INTO professor_department (professor_id,department_id,position) VALUES (9001,?,'기존 직위')", department);
        jdbc.update("INSERT INTO laboratory_department (laboratory_id,department_id) VALUES (9002,?)", department);
        jdbc.update("INSERT INTO research_field (id,name) VALUES (9003,'기존 연구 분야')");
        jdbc.update("INSERT INTO laboratory_research_field (laboratory_id,research_field_id) VALUES (9002,9003)");
        var professor = jdbc.queryForMap("SELECT * FROM professor WHERE id=9001");
        var lab = jdbc.queryForMap("SELECT * FROM laboratory WHERE id=9002");
        var fields = jdbc.queryForList("SELECT * FROM research_field ORDER BY id");
        var mappings = jdbc.queryForList("SELECT * FROM research_field_category_mapping ORDER BY research_field_id,category_id");

        flyway("44").migrate();

        assertCatalogue();
        assertThat(jdbc.queryForMap("SELECT * FROM professor WHERE id=9001")).isEqualTo(professor);
        assertThat(jdbc.queryForMap("SELECT * FROM laboratory WHERE id=9002")).isEqualTo(lab);
        assertThat(jdbc.queryForList("SELECT * FROM research_field ORDER BY id")).isEqualTo(fields);
        assertThat(jdbc.queryForList("SELECT * FROM research_field_category_mapping ORDER BY research_field_id,category_id")).isEqualTo(mappings);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM laboratory_research_field WHERE laboratory_id=9002 AND research_field_id=9003", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM professor_department WHERE professor_id=9001", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM laboratory_department WHERE laboratory_id=9002", Integer.class)).isEqualTo(2);
        validateHibernate();
    }

    @Test
    void conflictingEmailIdentityFailsBeforeCanonicalWrites() {
        flyway("41").migrate();
        jdbc.update("INSERT INTO professor (department_id,name,email) VALUES (?,'임다른사람','jslim@sejong.ac.kr')", department());
        assertThatThrownBy(() -> flyway("44").migrate()).isInstanceOf(RuntimeException.class);
        assertThat(count("professor")).isEqualTo(1);
        assertThat(count("laboratory")).isZero();
        assertThat(count("crawl_source")).isEqualTo(12);
    }

    @Test
    void conflictingSourceDepartmentFailsInsteadOfMovingIt() {
        flyway("41").migrate();
        jdbc.update("INSERT INTO crawl_source (department_id,source_name,source_url,parser_type,version) VALUES (?,'다른 학과',?,'SEJONG_STANDARD',0)", department(), SOURCE);
        assertThatThrownBy(() -> flyway("44").migrate()).isInstanceOf(RuntimeException.class);
        assertThat(count("professor")).isZero();
        assertThat(count("laboratory")).isZero();
        assertThat(jdbc.queryForObject("SELECT source_name FROM crawl_source WHERE source_url=?", String.class, SOURCE)).isEqualTo("다른 학과");
    }

    @Test
    void deletedLaboratoryIsNotResurrectedByDeployment() {
        flyway("41").migrate();
        jdbc.update("INSERT INTO professor (id,department_id,name,email) VALUES (9001,?,'임준석','jslim@sejong.ac.kr')", department());
        jdbc.update("""
            INSERT INTO laboratory (professor_id,department_id,name,recruitment_status,name_source,deleted_at)
            VALUES (9001,?,'삭제된 연구실','CLOSED','OFFICIAL',CURRENT_TIMESTAMP)
            """, department());
        assertThatThrownBy(() -> flyway("44").migrate()).isInstanceOf(RuntimeException.class);
        assertThat(count("professor")).isEqualTo(1);
        assertThat(count("laboratory")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM laboratory WHERE deleted_at IS NULL", Integer.class)).isZero();
    }

    @Test
    void ambiguousNullEmailIdentityIsNotGuessed() {
        flyway("41").migrate();
        jdbc.update("INSERT INTO college (name) VALUES ('공과대학')");
        jdbc.update("INSERT INTO department (college_id,name) SELECT id,'나노신소재공학과' FROM college WHERE name='공과대학'");
        for (int i=0; i<2; i++) {
            jdbc.update("INSERT INTO professor (department_id,name,email) SELECT id,'박경순',NULL FROM department WHERE name='나노신소재공학과'");
        }
        assertThatThrownBy(() -> flyway("44").migrate()).isInstanceOf(RuntimeException.class);
        assertThat(count("professor")).isEqualTo(2);
        assertThat(count("laboratory")).isZero();
    }

    @Test
    void multipleActiveLaboratoriesRequireManualResolution() {
        flyway("41").migrate();
        jdbc.update("INSERT INTO professor (id,department_id,name,email) VALUES (9001,?,'임준석','jslim@sejong.ac.kr')", department());
        for (String name : new String[]{"공식 연구실 A", "공식 연구실 B"}) {
            jdbc.update("INSERT INTO laboratory (professor_id,department_id,name,recruitment_status,name_source) VALUES (9001,?,?,'UNKNOWN','OFFICIAL')", department(), name);
        }
        assertThatThrownBy(() -> flyway("44").migrate()).isInstanceOf(RuntimeException.class);
        assertThat(count("professor")).isEqualTo(1);
        assertThat(count("laboratory")).isEqualTo(2);
    }

    private void assertCatalogue() {
        assertThat(count("professor")).isEqualTo(303);
        assertThat(count("laboratory")).isEqualTo(303);
        assertThat(count("professor_department")).isEqualTo(335);
        assertThat(count("laboratory_department")).isEqualTo(335);
        assertThat(count("crawl_source")).isEqualTo(42);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM department WHERE name IN ('영어데이터융합학','항공시스템공학전공')", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM department WHERE name IN ('영어데이터융합전공','항공시스템공학과')", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM professor WHERE email IS NULL", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM professor WHERE email='fsc77@sejong.ac.kr'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM laboratory l JOIN professor p ON p.id=l.professor_id WHERE p.email='fsc77@sejong.ac.kr' AND l.website_url IS NULL", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM laboratory_department ld JOIN professor p ON p.id=(SELECT l.professor_id FROM laboratory l WHERE l.id=ld.laboratory_id) WHERE p.email='jhwang@sejong.ac.kr'", Integer.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name LIKE 'v44_catalog_%'", Integer.class)).isZero();
    }

    private long department() {
        return jdbc.queryForObject("SELECT id FROM department WHERE name='AI융합전자공학과'", Long.class);
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private Flyway flyway(String target) {
        FluentConfiguration config = Flyway.configure().dataSource(dataSource())
            .locations("classpath:db/migration").cleanDisabled(false).target(target);
        return config.load();
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

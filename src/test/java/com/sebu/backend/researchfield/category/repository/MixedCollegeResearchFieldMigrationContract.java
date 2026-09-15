package com.sebu.backend.researchfield.category.repository;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

import javax.sql.DataSource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

abstract class MixedCollegeResearchFieldMigrationContract {
    private static final String TARGET_EMAIL = "kbjeong7@sejong.ac.kr";
    private static final String MIGRATION = "db/migration/V45__import_reviewed_mixed_college_research_fields.sql";
    protected JdbcTemplate jdbc;

    protected abstract DataSource dataSource();

    @BeforeEach
    void cleanDedicatedDatabase() {
        jdbc = new JdbcTemplate(dataSource());
        String database = jdbc.queryForObject("SELECT DATABASE()", String.class);
        assertThat(database).startsWith("sebu_field80_test");
        flyway("45").clean();
    }

    @Test
    void blankDatabaseImportsReviewedFieldsAndMappingsWithoutCandidateAuditData() {
        flyway("45").migrate();

        assertThat(count("research_field_category")).isEqualTo(32);
        assertThat(count("research_field")).isEqualTo(981);
        assertThat(count("laboratory_research_field")).isEqualTo(1072);
        assertThat(count("research_field_category_mapping")).isEqualTo(1223);
        assertThat(count("laboratory_research_field_candidate")).isZero();
        assertThat(count("professor_crawl_candidate")).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT laboratory_id) FROM laboratory_research_field", Integer.class)).isEqualTo(272);
        assertNoUnmappedOrDuplicatePairs();
        assertThat(jdbc.queryForObject("""
            SELECT COUNT(*) FROM laboratory_research_field lf JOIN laboratory l ON l.id=lf.laboratory_id
            JOIN professor p ON p.id=l.professor_id WHERE p.email='clee@sejong.ac.kr'
            """, Integer.class)).isZero();
        assertThat(jdbc.queryForList("""
            SELECT f.name FROM laboratory_research_field lf JOIN research_field f ON f.id=lf.research_field_id
            JOIN laboratory l ON l.id=lf.laboratory_id JOIN professor p ON p.id=l.professor_id
            WHERE p.email='sufyanahmedali@sejong.ac.kr' ORDER BY f.name
            """, String.class)).containsExactlyInAnyOrder("유도", "항법", "추정 및 추적");
        assertCategory("천체물리 분광학", "PHYSICS_ASTRONOMY");
        assertCategory("중국문학", "LANGUAGE_LITERATURE");
        assertCategory("탄소나노소재 열전도성 복합재료", "CHEMISTRY_MATERIALS");
        assertThat(jdbc.queryForObject("""
            SELECT COUNT(*) FROM research_field f JOIN research_field_category_mapping m ON m.research_field_id=f.id
            JOIN research_field_category c ON c.id=m.category_id
            WHERE f.name='탄소나노소재 열전도성 복합재료' AND c.code='SIGNAL_MEDIA'
            """, Integer.class)).isZero();
        validateLatestHibernate();
        flyway("45").validate();
        assertThat(flyway("45").migrate().migrationsExecuted).isZero();
    }

    @Test
    void upgradeReusesDifferentIdsAndPreservesProfilesAuditRowsAndExistingMappings() {
        flyway("43").migrate();
        long department = jdbc.queryForObject("SELECT id FROM department WHERE name='건축공학과'", Long.class);
        jdbc.update("INSERT INTO professor (id,department_id,name,email) VALUES (90001,?,'정광복',?)", department,TARGET_EMAIL);
        jdbc.update("""
            INSERT INTO laboratory (id,professor_id,department_id,name,description,recruitment_status,name_source)
            VALUES (90002,90001,?,'검수된 공식 연구실','직접 작성한 소개','RECRUITING','OFFICIAL')
            """,department);
        flyway("44").migrate();
        jdbc.update("INSERT INTO research_field (id,name) VALUES (80001,'인공지능'),(80002,'사용자 정의 보존 분야')");
        jdbc.update("INSERT INTO research_field_category (id,code,name,description,display_order) VALUES (80003,'ARCHITECTURE_CIVIL','건축·토목·도시','기존 관리자 설명',400)");
        jdbc.update("INSERT INTO laboratory_research_field (laboratory_id,research_field_id) VALUES (90002,80002)");
        jdbc.update("INSERT INTO research_field_category_mapping (research_field_id,category_id) SELECT 80002,id FROM research_field_category WHERE code='AI_ML'");
        jdbc.update("""
            INSERT INTO laboratory_research_field_candidate
            (laboratory_id,source_field_key,source_description_hash,raw_field_text,candidate_name,
             extraction_method,source_order,extraction_rule_version,extracted_at,version)
            VALUES (90002,?,?,'검수 대기 원문','검수 대기 분야','WHOLE_TEXT',0,'test-v1',CURRENT_TIMESTAMP,0)
            ""","a".repeat(64),"b".repeat(64));
        var preserved = snapshotProfilesAndAudit();
        var oldFields = jdbc.queryForList("SELECT * FROM research_field ORDER BY id");
        var oldCategory = jdbc.queryForMap("SELECT * FROM research_field_category WHERE id=80003");

        assertThat(flyway("45").migrate().migrationsExecuted).isEqualTo(1);

        assertThat(snapshotProfilesAndAudit()).isEqualTo(preserved);
        assertThat(jdbc.queryForList("SELECT * FROM research_field WHERE id IN (80001,80002) ORDER BY id")).isEqualTo(oldFields);
        assertThat(jdbc.queryForMap("SELECT * FROM research_field_category WHERE id=80003")).isEqualTo(oldCategory);
        assertThat(jdbc.queryForObject("SELECT id FROM research_field WHERE name='인공지능'",Long.class)).isEqualTo(80001);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM laboratory_research_field WHERE laboratory_id=90002",Integer.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM laboratory_research_field WHERE laboratory_id=90002 AND research_field_id=80002",Integer.class)).isEqualTo(1);
        assertCategory("사용자 정의 보존 분야","AI_ML");
        assertNoUnmappedOrDuplicatePairs();
        validateLatestHibernate();
    }

    @Test
    void replayDoesNotChangeIdsTimestampsOrMappings() {
        flyway("45").migrate();
        var before = snapshotCanonical();
        var replay = new ResourceDatabasePopulator(new ClassPathResource(MIGRATION));
        replay.setSqlScriptEncoding("UTF-8");
        replay.execute(dataSource());
        replay.execute(dataSource());
        assertThat(snapshotCanonical()).isEqualTo(before);
        assertNoUnmappedOrDuplicatePairs();
        flyway("45").validate();
    }

    @Test
    void wrongProfessorNameFailsBeforeCanonicalWrites() {
        flyway("44").migrate();
        jdbc.update("UPDATE professor SET name='다른 사람' WHERE email=?",TARGET_EMAIL);
        assertGuardedFailure();
    }

    @Test
    void softDeletedLaboratoryIsNotResurrected() {
        flyway("44").migrate();
        jdbc.update("UPDATE laboratory SET deleted_at=CURRENT_TIMESTAMP WHERE professor_id=(SELECT id FROM professor WHERE email=?)",TARGET_EMAIL);
        assertGuardedFailure();
    }

    @Test
    void multipleActiveLaboratoriesAreNotGuessed() {
        flyway("44").migrate();
        jdbc.update("""
            INSERT INTO laboratory (professor_id,department_id,name,recruitment_status,name_source)
            SELECT id,department_id,'또 다른 공식 연구실','UNKNOWN','OFFICIAL' FROM professor WHERE email=?
            """,TARGET_EMAIL);
        assertGuardedFailure();
    }

    @Test
    void ambiguousNullEmailProfessorIsNotGuessed() {
        flyway("44").migrate();
        jdbc.update("INSERT INTO professor (department_id,name,email) SELECT department_id,name,NULL FROM professor WHERE name='김홍범' AND email IS NULL");
        assertGuardedFailure();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void conflictingCategoryCodeOrNameFailsBeforeCanonicalWrites(boolean sameCode) {
        flyway("44").migrate();
        jdbc.update("INSERT INTO research_field_category (code,name,description,display_order) VALUES (?,?, '관리자 분류',400)",
            sameCode ? "ARCHITECTURE_CIVIL" : "CUSTOM_ARCH", sameCode ? "서로 다른 분류" : "건축·토목·도시");
        assertGuardedFailure();
    }

    @Test
    void missingReferencedCategoryFailsInsteadOfDroppingMappings() {
        flyway("44").migrate();
        jdbc.update("DELETE FROM research_field_category WHERE code='AI_ML'");
        assertGuardedFailure();
    }

    private void assertGuardedFailure() {
        var before = snapshotCanonical();
        assertThatThrownBy(() -> flyway("45").migrate()).isInstanceOf(RuntimeException.class);
        assertThat(snapshotCanonical()).isEqualTo(before);
    }

    private void assertNoUnmappedOrDuplicatePairs() {
        assertThat(jdbc.queryForObject("""
            SELECT COUNT(*) FROM research_field f WHERE NOT EXISTS
            (SELECT 1 FROM research_field_category_mapping m WHERE m.research_field_id=f.id)
            """, Integer.class)).isZero();
        for (String query : List.of(
            "SELECT COUNT(*) FROM (SELECT laboratory_id,research_field_id FROM laboratory_research_field GROUP BY laboratory_id,research_field_id HAVING COUNT(*)>1) duplicates",
            "SELECT COUNT(*) FROM (SELECT research_field_id,category_id FROM research_field_category_mapping GROUP BY research_field_id,category_id HAVING COUNT(*)>1) duplicates",
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name LIKE 'v45_%'")) {
            assertThat(jdbc.queryForObject(query, Integer.class)).isZero();
        }
    }

    private void assertCategory(String field, String code) {
        assertThat(jdbc.queryForObject("""
            SELECT COUNT(*) FROM research_field f JOIN research_field_category_mapping m ON m.research_field_id=f.id
            JOIN research_field_category c ON c.id=m.category_id WHERE f.name=? AND c.code=?
            """, Integer.class, field, code)).isEqualTo(1);
    }

    private Map<String, Object> snapshotProfilesAndAudit() {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String table : List.of(
            "college", "department", "professor", "laboratory", "app_user",
            "professor_crawl_candidate", "laboratory_research_field_candidate"
        )) {
            result.put(table, jdbc.queryForList("SELECT * FROM " + table + " ORDER BY id"));
        }
        return result;
    }

    private Map<String, Object> snapshotCanonical() {
        var result = snapshotProfilesAndAudit();
        result.put("fields", jdbc.queryForList("SELECT * FROM research_field ORDER BY id"));
        result.put("categories", jdbc.queryForList("SELECT * FROM research_field_category ORDER BY id"));
        result.put("labFields", jdbc.queryForList("SELECT * FROM laboratory_research_field ORDER BY laboratory_id,research_field_id"));
        result.put("categoryMappings", jdbc.queryForList("SELECT * FROM research_field_category_mapping ORDER BY research_field_id,category_id"));
        return result;
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private Flyway flyway(String target) {
        return Flyway.configure()
            .dataSource(dataSource())
            .locations("classpath:db/migration")
            .target(target)
            .cleanDisabled(false)
            .load();
    }

    private void validateLatestHibernate() {
        Flyway.configure().dataSource(dataSource()).locations("classpath:db/migration").load().migrate();
        var factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource());
        factory.setPackagesToScan("com.sebu.backend");
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(Map.of(
            "hibernate.hbm2ddl.auto", "validate",
            "hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"
        ));
        try {
            factory.afterPropertiesSet();
            assertThat(factory.getObject()).isNotNull();
        } finally {
            factory.destroy();
        }
    }
}

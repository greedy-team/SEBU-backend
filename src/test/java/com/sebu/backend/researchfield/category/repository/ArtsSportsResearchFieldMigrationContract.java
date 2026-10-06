package com.sebu.backend.researchfield.category.repository;

import com.sebu.backend.laboratory.repository.LaboratoryResearchFieldCategoryQueryRepository;
import com.sebu.backend.laboratory.repository.LaboratoryResearchFieldRepository;
import com.sebu.backend.researchfield.category.controller.ResearchFieldCategoryController;
import com.sebu.backend.researchfield.category.dto.ResearchFieldCategoriesResponse;
import com.sebu.backend.researchfield.category.service.ResearchFieldCategoryQueryService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

import javax.sql.DataSource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

abstract class ArtsSportsResearchFieldMigrationContract {
    private static final String TARGET_EMAIL = "runner23@sejong.ac.kr";
    private static final String MIGRATION = "db/migration/V49__import_reviewed_arts_sports_research_fields.sql";
    private JdbcTemplate jdbc;

    protected abstract DataSource dataSource();

    @BeforeEach
    void cleanDedicatedDatabase() {
        jdbc = new JdbcTemplate(dataSource());
        assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).startsWith("sebu_arts_sports_test");
        flyway("49").clean();
    }

    @Test
    void blankDatabaseImportsReviewedFieldsAndExposesCategoriesAndResearchFields() {
        flyway("49").migrate();

        assertThat(count("research_field_category")).isEqualTo(54);
        assertThat(count("research_field")).isEqualTo(1035);
        assertThat(count("laboratory_research_field")).isEqualTo(1133);
        assertThat(count("professor")).isEqualTo(321);
        assertThat(count("laboratory")).isEqualTo(321);
        assertThat(count("professor_crawl_candidate")).isZero();
        assertThat(count("laboratory_research_field_candidate")).isZero();
        assertThat(jdbc.queryForObject("""
            SELECT COUNT(DISTINCT l.id) FROM laboratory l JOIN professor p ON p.id=l.professor_id
            JOIN department d ON d.id=l.department_id JOIN college c ON c.id=d.college_id
            JOIN laboratory_research_field lf ON lf.laboratory_id=l.id WHERE c.name='예체능대학'
            """, Integer.class)).isEqualTo(15);
        assertThat(jdbc.queryForObject("""
            SELECT COUNT(*) FROM laboratory_research_field lf JOIN laboratory l ON l.id=lf.laboratory_id
            JOIN professor p ON p.id=l.professor_id
            WHERE p.email IN ('actscene@naver.com','zungbu@daum.net','ysahn@sejong.ac.kr')
            """, Integer.class)).isZero();
        assertReviewedFieldsAndMappings();
        assertNoUnmappedOrDuplicatePairs();
        validateHibernateAndQueryResponses();
        flyway("49").validate();
        assertThat(flyway("49").migrate().migrationsExecuted).isZero();
    }

    @Test
    void upgradeReusesExistingIdsAndPreservesProfilesAuditMappingsAndRobotHierarchy() {
        flyway("48").migrate();
        long laboratory = laboratory(TARGET_EMAIL);
        jdbc.update("""
            UPDATE laboratory SET name='관리자가 검수한 공식 연구실',name_source='OFFICIAL',
                description='수동으로 보존하는 연구소개',website_url='https://example.com/official',
                website_url_source='MANUAL',recruitment_status='RECRUITING' WHERE id=?
            """, laboratory);
        jdbc.update("INSERT INTO research_field (id,name) VALUES (80001,'한국화'),(80002,'기존 보존 분야')");
        jdbc.update("""
            INSERT INTO research_field_category (id,code,name,description,display_order)
            VALUES (80003,'MUSIC_PERFORMING_ARTS','음악·공연예술','기존 관리자 설명',400)
            """);
        jdbc.update("INSERT INTO laboratory_research_field (laboratory_id,research_field_id) VALUES (?,80002)", laboratory);
        jdbc.update("INSERT INTO research_field_category_mapping (research_field_id,category_id) SELECT 80002,id FROM research_field_category WHERE code='AI_ML'");
        jdbc.update("""
            INSERT INTO laboratory_research_field_candidate
                (laboratory_id,source_field_key,source_description_hash,raw_field_text,candidate_name,
                extraction_method,source_order,extraction_rule_version,extracted_at,version)
            VALUES (?, ?, ?, '검수 대기 원문','검수 대기 분야','WHOLE_TEXT',0,'test-v1',CURRENT_TIMESTAMP,0)
            """, laboratory, "a".repeat(64), "b".repeat(64));
        var before = snapshotCanonical();

        assertThat(flyway("49").migrate().migrationsExecuted).isEqualTo(1);

        var after = snapshotCanonical();
        for (var entry : before.entrySet()) {
            if (Set.of("research_field", "research_field_category", "laboratory_research_field", "research_field_category_mapping").contains(entry.getKey())) {
                assertThat(after.get(entry.getKey())).as(entry.getKey()).containsAll(entry.getValue());
            } else {
                assertThat(after.get(entry.getKey())).as(entry.getKey()).isEqualTo(entry.getValue());
            }
        }
        assertThat(count("research_field")).isEqualTo(before.get("research_field").size() + 53);
        assertThat(count("research_field_category")).isEqualTo(before.get("research_field_category").size() + 1);
        assertThat(count("laboratory_research_field")).isEqualTo(before.get("laboratory_research_field").size() + 61);
        assertThat(count("research_field_category_mapping")).isEqualTo(before.get("research_field_category_mapping").size() + 68);
        assertThat(jdbc.queryForObject("SELECT id FROM research_field WHERE name='한국화'", Long.class)).isEqualTo(80001L);
        assertThat(jdbc.queryForObject("SELECT id FROM research_field_category WHERE code='MUSIC_PERFORMING_ARTS'", Long.class)).isEqualTo(80003L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM laboratory_research_field WHERE laboratory_id=? AND research_field_id=80001", Integer.class, laboratory)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT display_order FROM research_field_category WHERE code='SPORTS_PHYSICAL_EDUCATION'", Integer.class)).isGreaterThan(400);
        assertNoUnmappedOrDuplicatePairs();
        flyway("49").validate();
    }

    @Test
    void replayDoesNotChangeIdsTimestampsOrMappings() {
        flyway("49").migrate();
        var before = snapshotCanonical();
        var replay = new ResourceDatabasePopulator(new ClassPathResource(MIGRATION));
        replay.setSqlScriptEncoding("UTF-8");
        replay.execute(dataSource());
        replay.execute(dataSource());
        assertThat(snapshotCanonical()).isEqualTo(before);
        assertNoUnmappedOrDuplicatePairs();
    }

    @Test
    void wrongProfessorNameFailsBeforeCanonicalWrites() {
        flyway("48").migrate();
        jdbc.update("UPDATE professor SET name='다른 사람' WHERE email=?", TARGET_EMAIL);
        assertGuardedFailure();
    }

    @Test
    void softDeletedLaboratoryIsNotResurrected() {
        flyway("48").migrate();
        jdbc.update("UPDATE laboratory SET deleted_at=CURRENT_TIMESTAMP WHERE id=?", laboratory(TARGET_EMAIL));
        assertGuardedFailure();
    }

    @Test
    void multipleActiveLaboratoriesAreNotGuessed() {
        flyway("48").migrate();
        jdbc.update("""
            INSERT INTO laboratory (professor_id,department_id,name,recruitment_status,name_source)
            SELECT id,department_id,'또 다른 공식 연구실','UNKNOWN','OFFICIAL' FROM professor WHERE email=?
            """, TARGET_EMAIL);
        assertGuardedFailure();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void conflictingCategoryCodeOrNameFailsBeforeCanonicalWrites(boolean sameCode) {
        flyway("48").migrate();
        jdbc.update("INSERT INTO research_field_category (code,name,description,display_order) VALUES (?,?,'관리자 분류',400)",
            sameCode ? "MUSIC_PERFORMING_ARTS" : "CUSTOM_MUSIC", sameCode ? "다른 분류" : "음악·공연예술");
        assertGuardedFailure();
    }

    @Test
    void rootCategoryWithUnexpectedParentFailsBeforeCanonicalWrites() {
        flyway("48").migrate();
        jdbc.update("""
            INSERT INTO research_field_category (code,name,description,display_order,parent_id)
            SELECT 'MUSIC_PERFORMING_ARTS','음악·공연예술','기존 잘못된 계층',400,id
            FROM research_field_category WHERE code='ROBOT_AUTONOMOUS'
            """);
        assertGuardedFailure();
    }

    @Test
    void missingReferencedCategoryFailsInsteadOfDroppingMappings() {
        flyway("48").migrate();
        jdbc.update("UPDATE research_field_category SET code='UNRECOGNIZED_DESIGN' WHERE code='DESIGN_ARTS'");
        assertGuardedFailure();
    }

    private void assertReviewedFieldsAndMappings() {
        var departmentLinks = Map.of("회화과", 1, "패션디자인학과", 12, "음악과", 24,
            "체육학과", 9, "무용과", 3, "영화예술학과", 12);
        for (var entry : departmentLinks.entrySet()) {
            assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM laboratory_research_field lf
                JOIN laboratory l ON l.id=lf.laboratory_id JOIN department d ON d.id=l.department_id
                WHERE d.name=?
                """, Integer.class, entry.getKey())).as(entry.getKey()).isEqualTo(entry.getValue());
        }
        assertThat(jdbc.queryForObject("""
            SELECT COUNT(DISTINCT lf.research_field_id) FROM laboratory_research_field lf
            JOIN laboratory l ON l.id=lf.laboratory_id JOIN department d ON d.id=l.department_id
            JOIN college c ON c.id=d.college_id WHERE c.name='예체능대학'
            """, Integer.class)).isEqualTo(55);
        assertThat(jdbc.queryForObject("""
            SELECT COUNT(*) FROM research_field_category_mapping m WHERE m.research_field_id IN (
                SELECT lf.research_field_id FROM laboratory_research_field lf
                JOIN laboratory l ON l.id=lf.laboratory_id JOIN department d ON d.id=l.department_id
                JOIN college c ON c.id=d.college_id WHERE c.name='예체능대학')
            """, Integer.class)).isEqualTo(69);
        assertLabFields(TARGET_EMAIL, "한국화");
        assertLabFields("kclee@sejong.ac.kr", "piano performance", "piano literature", "collaborative piano");
        assertLabFields("hyoungnam@sejong.ac.kr", "무용교육", "예술교육", "융합예술콘텐츠");
        assertLabFields("filmdoo@sejong.ac.kr", "영화", "드라마 기획", "시나리오", "제작", "영화 미학 연구");
        assertCategories("한국화", "DESIGN_ARTS");
        assertCategories("예술/디자인사", "DESIGN_ARTS", "HISTORY_CULTURE");
        assertCategories("인공지능과 패션 디자인", "AI_ML", "DESIGN_ARTS");
        assertCategories("piano performance", "MUSIC_PERFORMING_ARTS");
        assertCategories("체육학", "SPORTS_PHYSICAL_EDUCATION");
        assertCategories("영화", "DESIGN_ARTS");
    }

    private void assertLabFields(String email, String... expected) {
        assertThat(jdbc.queryForList("""
            SELECT f.name FROM laboratory_research_field lf JOIN research_field f ON f.id=lf.research_field_id
            JOIN laboratory l ON l.id=lf.laboratory_id JOIN professor p ON p.id=l.professor_id
            WHERE p.email=?
            """, String.class, email)).containsExactlyInAnyOrder(expected);
    }

    private void assertCategories(String field, String... expected) {
        assertThat(jdbc.queryForList("""
            SELECT c.code FROM research_field f JOIN research_field_category_mapping m ON m.research_field_id=f.id
            JOIN research_field_category c ON c.id=m.category_id WHERE f.name=?
            """, String.class, field)).containsExactlyInAnyOrder(expected);
    }

    private void validateHibernateAndQueryResponses() {
        var factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource());
        factory.setPackagesToScan("com.sebu.backend");
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "validate",
            "hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"));
        try {
            factory.afterPropertiesSet();
            assertThat(factory.getObject()).isNotNull();
            try (var entityManager = factory.getObject().createEntityManager()) {
                var repositories = new JpaRepositoryFactory(entityManager);
                var controller = new ResearchFieldCategoryController(new ResearchFieldCategoryQueryService(
                    repositories.getRepository(ResearchFieldCategoryRepository.class)));
                var response = controller.getAll();
                assertThat(response.success()).isTrue();
                var categories = response.data().categories();
                assertThat(categories).hasSize(54);
                assertThat(categories).extracting(ResearchFieldCategoriesResponse.CategoryResponse::displayOrder).isSorted();
                assertThat(categories).filteredOn(category -> category.code().equals("MUSIC_PERFORMING_ARTS"))
                    .singleElement().satisfies(category -> {
                        assertThat(category.name()).isEqualTo("음악·공연예술");
                        assertThat(category.displayOrder()).isEqualTo(53);
                        assertThat(category.parentId()).isNull();
                    });
                assertThat(categories).filteredOn(category -> category.code().equals("SPORTS_PHYSICAL_EDUCATION"))
                    .singleElement().satisfies(category -> {
                        assertThat(category.name()).isEqualTo("체육·스포츠");
                        assertThat(category.displayOrder()).isEqualTo(54);
                        assertThat(category.parentId()).isNull();
                    });
                long robotParent = categories.stream().filter(category -> category.code().equals("ROBOT_AUTONOMOUS"))
                    .findFirst().orElseThrow().id();
                assertThat(categories).filteredOn(category -> category.code().startsWith("ROBOT_AUTONOMOUS_"))
                    .hasSize(20).allSatisfy(category -> assertThat(category.parentId()).isEqualTo(robotParent));
                var laboratoryEmails = new LinkedHashMap<Long, String>();
                jdbc.query("""
                    SELECT l.id,p.email FROM laboratory l JOIN professor p ON p.id=l.professor_id
                    JOIN department d ON d.id=l.department_id JOIN college c ON c.id=d.college_id
                    WHERE c.name='예체능대학'
                    """, rs -> { laboratoryEmails.put(rs.getLong(1), rs.getString(2)); });
                var fields = repositories.getRepository(LaboratoryResearchFieldRepository.class)
                    .findFieldsByLaboratoryIds(laboratoryEmails.keySet());
                assertThat(fields).hasSize(61);
                var mappings = repositories.getRepository(LaboratoryResearchFieldCategoryQueryRepository.class)
                    .findAllByLaboratoryIds(laboratoryEmails.keySet());
                assertThat(mappings).extracting(row -> List.of(row.getLaboratoryId(), row.getResearchFieldId(), row.getCategoryId()))
                    .doesNotHaveDuplicates();
                assertThat(mappings).filteredOn(row -> row.getLaboratoryId().equals(laboratory("kclee@sejong.ac.kr")))
                    .hasSize(3).allSatisfy(row -> {
                        assertThat(row.getCategoryCode()).isEqualTo("MUSIC_PERFORMING_ARTS");
                        assertThat(row.getParentId()).isNull();
                    });
                assertThat(mappings).filteredOn(row -> row.getLaboratoryId().equals(laboratory("kimbm@sejong.ac.kr")))
                    .singleElement().satisfies(row -> assertThat(row.getCategoryCode()).isEqualTo("SPORTS_PHYSICAL_EDUCATION"));
            }
        } finally {
            factory.destroy();
        }
    }

    private void assertGuardedFailure() {
        var before = snapshotCanonical();
        assertThatThrownBy(() -> flyway("49").migrate()).isInstanceOf(RuntimeException.class);
        assertThat(snapshotCanonical()).isEqualTo(before);
    }

    private void assertNoUnmappedOrDuplicatePairs() {
        for (String query : List.of(
            "SELECT COUNT(*) FROM research_field f WHERE NOT EXISTS (SELECT 1 FROM research_field_category_mapping m WHERE m.research_field_id=f.id)",
            "SELECT COUNT(*) FROM (SELECT laboratory_id,research_field_id FROM laboratory_research_field GROUP BY laboratory_id,research_field_id HAVING COUNT(*)>1) duplicates",
            "SELECT COUNT(*) FROM (SELECT research_field_id,category_id FROM research_field_category_mapping GROUP BY research_field_id,category_id HAVING COUNT(*)>1) duplicates",
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name LIKE 'v49_%'")) {
            assertThat(jdbc.queryForObject(query, Integer.class)).isZero();
        }
    }

    private Map<String, List<Map<String, Object>>> snapshotCanonical() {
        var result = new LinkedHashMap<String, List<Map<String, Object>>>();
        for (String table : List.of("college", "department", "professor", "laboratory", "app_user", "crawl_source",
            "professor_crawl_candidate", "laboratory_research_field_candidate", "research_field", "research_field_category")) {
            result.put(table, jdbc.queryForList("SELECT * FROM " + table + " ORDER BY id"));
        }
        result.put("professor_department", jdbc.queryForList("SELECT * FROM professor_department ORDER BY professor_id,department_id"));
        result.put("laboratory_department", jdbc.queryForList("SELECT * FROM laboratory_department ORDER BY laboratory_id,department_id"));
        result.put("laboratory_research_field", jdbc.queryForList("SELECT * FROM laboratory_research_field ORDER BY laboratory_id,research_field_id"));
        result.put("research_field_category_mapping", jdbc.queryForList("SELECT * FROM research_field_category_mapping ORDER BY research_field_id,category_id"));
        return result;
    }

    private long laboratory(String email) {
        return jdbc.queryForObject("SELECT l.id FROM laboratory l JOIN professor p ON p.id=l.professor_id WHERE p.email=?", Long.class, email);
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private Flyway flyway(String target) {
        return Flyway.configure().dataSource(dataSource()).locations("classpath:db/migration")
            .target(target).cleanDisabled(false).load();
    }
}

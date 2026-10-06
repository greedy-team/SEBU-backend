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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

abstract class ArtsSportsResearchFieldMigrationContract {
    private static final String TARGET_EMAIL = "runner23@sejong.ac.kr";
    private static final String MIGRATION = "db/migration/V49__import_reviewed_arts_sports_research_fields.sql";
    private static final Path LINKS_CSV = Path.of("docs/data/arts-sports-research-field-links.csv");
    private static final Path CATEGORIES_CSV = Path.of("docs/data/arts-sports-research-field-categories.csv");
    private JdbcTemplate jdbc;

    protected abstract DataSource dataSource();

    @BeforeEach
    void cleanDedicatedDatabase() {
        jdbc = new JdbcTemplate(dataSource());
        assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).startsWith("sebu_arts_sports_test");
        flyway("49").clean();
    }

    @Test
    void blankDatabaseMatchesEveryReviewedCsvValueAndExposesCategoriesAndResearchFields() throws IOException {
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
        assertReviewedCsvValues();
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

    private void assertReviewedCsvValues() throws IOException {
        var links = reviewedRows(LINKS_CSV);
        var categories = reviewedRows(CATEGORIES_CSV);
        assertThat(links).hasSize(61);
        assertThat(categories).hasSize(69);
        var expectedLinks = new LinkedHashSet<String>();
        var expectedCategories = new LinkedHashSet<String>();
        for (var row : links) {
            expectedLinks.add(pair(row.get("email"), row.get("research_field_name")));
            var profile = jdbc.queryForMap("""
                SELECT c.name AS college_name,d.name AS department_name,p.name AS professor_name,
                    l.description AS source_research_introduction,s.source_url
                FROM laboratory l JOIN professor p ON p.id=l.professor_id
                JOIN department d ON d.id=l.department_id JOIN college c ON c.id=d.college_id
                JOIN crawl_source s ON s.department_id=d.id
                WHERE p.email=? AND s.source_url=?
                """, row.get("email"), row.get("source_url"));
            for (String field : List.of("college_name", "department_name", "professor_name", "source_research_introduction", "source_url")) {
                assertThat(profile.get(field)).as(row.get("email") + " " + field).isEqualTo(row.get(field));
            }
            assertThat(jdbc.queryForList("""
                SELECT c.code FROM research_field f
                JOIN research_field_category_mapping m ON m.research_field_id=f.id
                JOIN research_field_category c ON c.id=m.category_id WHERE f.name=?
                """, String.class, row.get("research_field_name")))
                .containsExactlyInAnyOrder(row.get("category_codes").split("\\|"));
        }
        for (var row : categories) {
            expectedCategories.add(pair(row.get("research_field_name"), row.get("category_code")));
            var category = jdbc.queryForMap("""
                SELECT c.name,parent.code AS parent_code FROM research_field_category c
                LEFT JOIN research_field_category parent ON parent.id=c.parent_id WHERE c.code=?
                """, row.get("category_code"));
            assertThat(category.get("name")).isEqualTo(row.get("category_name"));
            assertThat(category.get("parent_code")).isEqualTo(row.get("parent_category_code").isEmpty() ? null : row.get("parent_category_code"));
            assertThat(row.get("category_is_new")).isEqualTo(Boolean.toString(
                Set.of("MUSIC_PERFORMING_ARTS", "SPORTS_PHYSICAL_EDUCATION").contains(row.get("category_code"))));
        }
        assertThat(expectedLinks).hasSize(61);
        assertThat(expectedCategories).hasSize(69);
        assertThat(links.stream().map(row -> row.get("research_field_name")).distinct()).hasSize(55);
        assertThat(jdbc.query("""
            SELECT p.email,f.name FROM laboratory_research_field lf
            JOIN laboratory l ON l.id=lf.laboratory_id JOIN professor p ON p.id=l.professor_id
            JOIN department d ON d.id=l.department_id JOIN college c ON c.id=d.college_id
            JOIN research_field f ON f.id=lf.research_field_id WHERE c.name='예체능대학'
            """, (rs, index) -> pair(rs.getString(1), rs.getString(2))))
            .doesNotHaveDuplicates().containsExactlyInAnyOrderElementsOf(expectedLinks);
        var actualCategories = new LinkedHashSet<String>();
        for (String field : links.stream().map(row -> row.get("research_field_name")).distinct().toList()) {
            actualCategories.addAll(jdbc.query("""
                SELECT f.name,c.code FROM research_field f
                JOIN research_field_category_mapping m ON m.research_field_id=f.id
                JOIN research_field_category c ON c.id=m.category_id WHERE f.name=?
                """, (rs, index) -> pair(rs.getString(1), rs.getString(2)), field));
        }
        assertThat(actualCategories).containsExactlyInAnyOrderElementsOf(expectedCategories);
    }

    private void validateHibernateAndQueryResponses() throws IOException {
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
                var fieldNames = new LinkedHashMap<Long, String>();
                fields.forEach(field -> fieldNames.put(field.getResearchFieldId(), field.getName()));
                var actual = repositories.getRepository(LaboratoryResearchFieldCategoryQueryRepository.class)
                    .findAllByLaboratoryIds(laboratoryEmails.keySet()).stream()
                    .map(row -> pair(laboratoryEmails.get(row.getLaboratoryId()), pair(fieldNames.get(row.getResearchFieldId()), row.getCategoryCode())))
                    .toList();
                var expected = new LinkedHashSet<String>();
                var reviewedCategories = reviewedRows(CATEGORIES_CSV);
                for (var link : reviewedRows(LINKS_CSV)) {
                    for (var category : reviewedCategories) {
                        if (link.get("research_field_name").equals(category.get("research_field_name"))) {
                            expected.add(pair(link.get("email"), pair(link.get("research_field_name"), category.get("category_code"))));
                        }
                    }
                }
                assertThat(actual).doesNotHaveDuplicates().containsExactlyInAnyOrderElementsOf(expected);
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

    private List<Map<String, String>> reviewedRows(Path path) throws IOException {
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
        assertThat(quoted).as("CSV 인용 필드가 닫혀 있어야 한다").isFalse();
        if (!columns.isEmpty() || !value.isEmpty()) {
            columns.add(value.toString());
            rows.add(columns);
        }
        var header = rows.getFirst();
        var reviewed = new ArrayList<Map<String, String>>();
        for (var row : rows.subList(1, rows.size())) {
            assertThat(row).hasSize(header.size());
            var mapped = new LinkedHashMap<String, String>();
            for (int index = 0; index < header.size(); index++) mapped.put(header.get(index), row.get(index));
            assertThat(mapped.get("review_status")).isEqualTo("APPROVED");
            reviewed.add(mapped);
        }
        return reviewed;
    }

    private String pair(String first, String second) {
        return first + "\t" + second;
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

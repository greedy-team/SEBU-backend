package com.sebu.backend.researchfield.category.repository;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class LifeScienceResearchFieldCategoryMySqlMigrationTest {
    private static final int EXPECTED_CATEGORY_COUNT = 24;
    private static final int EXPECTED_REFERENCE_FIELD_COUNT = 193;
    private static final int EXPECTED_REFERENCE_PAIR_COUNT = 247;
    private static final String TEST_DATABASE = "sebu_life_science_migration_test";
    private static final String CATEGORY_MIGRATION =
        "db/migration/V35__add_life_science_research_field_categories.sql";
    private static final String MAPPING_MIGRATION =
        "db/migration/V36__seed_life_science_research_field_category_mappings.sql";
    private static final String CUSTOM_FIELD = "V34 사용자 정의 연구 분야";
    private static final String UNMAPPED_CUSTOM_FIELD = "V34 미분류 사용자 정의 연구 분야";
    private static final String ALLOWED_LOCAL_URL_OPTION =
        "(?:useUnicode=true|characterEncoding=UTF-8|serverTimezone=Asia/Seoul)";
    private static final Pattern LOCAL_TEST_URL = Pattern.compile(
        "^jdbc:mysql://(?:localhost|127\\.0\\.0\\.1):13316/" + TEST_DATABASE
            + "(?:\\?" + ALLOWED_LOCAL_URL_OPTION
            + "(?:&" + ALLOWED_LOCAL_URL_OPTION + ")*)?$"
    );
    private static final Pattern SQL_ASSIGNMENT_PATTERN = Pattern.compile(
        "^\\s*\\('((?:[^']|'')+)', '([A-Z_]+)'\\)[,;]$"
    );

    private static MySQLContainer<?> mysql;
    private static DriverManagerDataSource dataSource;

    @BeforeAll
    static void startDatabase() {
        String configuredUrl = System.getenv("SEBU_TEST_MYSQL_URL");
        if (configuredUrl != null && !configuredUrl.isBlank()) {
            if (!LOCAL_TEST_URL.matcher(configuredUrl).matches()) {
                throw new IllegalArgumentException(
                    "SEBU_TEST_MYSQL_URL must select the dedicated " + TEST_DATABASE
                        + " database on localhost or 127.0.0.1 port 13316"
                );
            }
            dataSource = new DriverManagerDataSource(
                configuredUrl,
                System.getenv().getOrDefault("SEBU_TEST_MYSQL_USERNAME", "root"),
                System.getenv().getOrDefault("SEBU_TEST_MYSQL_PASSWORD", "")
            );
            return;
        }
        assumeTrue(
            DockerClientFactory.instance().isDockerAvailable(),
            "Docker or an explicitly configured dedicated local MySQL test database is required"
        );
        mysql = new MySQLContainer<>("mysql:8.4").withDatabaseName(TEST_DATABASE);
        mysql.start();
        dataSource = new DriverManagerDataSource(
            mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword()
        );
    }

    @AfterAll
    static void stopDatabase() {
        if (mysql != null) {
            mysql.stop();
        }
    }

    @BeforeEach
    void cleanDedicatedTestDatabase() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT DATABASE()");
             ResultSet result = statement.executeQuery()) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString(1)).isEqualTo(TEST_DATABASE);
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("MySQL");
        }
        flyway(null).clean();
    }

    @Test
    void blankDatabaseCreatesCategoriesWithoutInventingResearchFieldsAndPassesHibernateValidation()
        throws Exception {
        flyway(null).migrate();

        try (Connection connection = dataSource.getConnection()) {
            assertLatestCategories(connection);
            assertThat(count(connection, "SELECT COUNT(*) FROM research_field")).isZero();
            assertThat(count(connection, "SELECT COUNT(*) FROM research_field_category_mapping"))
                .isZero();
        }
        validateHibernateSchema();
    }

    @Test
    void v34UpgradeMapsOnlyExistingFieldsAndPreservesPreviousRows() throws Exception {
        flyway("34").migrate();
        Map<String, Set<String>> assignments = readLifeScienceAssignments();
        String referenceOnlyField = assignments.keySet().iterator().next();
        Map<String, Set<String>> expectedMappings = copyAssignments(assignments);
        expectedMappings.remove(referenceOnlyField);
        Map<String, CategorySnapshot> categoriesBefore;
        Map<String, Long> fieldsBefore;

        try (Connection connection = dataSource.getConnection()) {
            categoriesBefore = findCategories(connection);
            assertThat(categoriesBefore).hasSize(21);
            insertFields(connection, expectedMappings.keySet());
            insertFields(connection, Set.of(CUSTOM_FIELD, UNMAPPED_CUSTOM_FIELD));

            Map.Entry<String, Set<String>> reusableAssignment = expectedMappings.entrySet()
                .stream()
                .filter(entry -> entry.getValue().stream().anyMatch(code ->
                    !code.equals("AI_ML") && categoriesBefore.containsKey(code)
                ))
                .findFirst()
                .orElseThrow();
            String existingCategoryCode = reusableAssignment.getValue().stream()
                .filter(code -> !code.equals("AI_ML") && categoriesBefore.containsKey(code))
                .findFirst()
                .orElseThrow();
            insertMapping(connection, reusableAssignment.getKey(), existingCategoryCode);
            insertMapping(connection, reusableAssignment.getKey(), "AI_ML");
            expectedMappings.get(reusableAssignment.getKey()).add("AI_ML");
            insertMapping(connection, CUSTOM_FIELD, "SECURITY_CRYPTO");
            expectedMappings.put(CUSTOM_FIELD, Set.of("SECURITY_CRYPTO"));
            fieldsBefore = findFields(connection);
        }

        assertThat(flyway(null).migrate().migrationsExecuted).isEqualTo(2);

        try (Connection connection = dataSource.getConnection()) {
            assertLatestCategories(connection);
            assertThat(findCategories(connection)).containsAllEntriesOf(categoriesBefore);
            assertThat(findFields(connection)).isEqualTo(fieldsBefore);
            assertThat(findFields(connection)).doesNotContainKey(referenceOnlyField);
            assertThat(findMappings(connection)).isEqualTo(expectedMappings);
            assertThat(count(connection, "SELECT COUNT(*) FROM research_field_category_mapping"))
                .isEqualTo(mappingCount(expectedMappings));
            assertNoDuplicateMappings(connection);
        }
        validateHibernateSchema();
    }

    @Test
    void seedSqlCanBeReexecutedWithoutChangingExistingCategoriesFieldsOrMappingPairs()
        throws Exception {
        flyway("34").migrate();
        Map<String, Set<String>> expectedMappings = readLifeScienceAssignments();
        try (Connection connection = dataSource.getConnection()) {
            insertFields(connection, expectedMappings.keySet());
        }
        flyway(null).migrate();

        try (Connection connection = dataSource.getConnection()) {
            Map<String, CategorySnapshot> categoriesBefore = findCategories(connection);
            Map<String, Long> fieldsBefore = findFields(connection);
            assertThat(findMappings(connection)).isEqualTo(expectedMappings);

            for (int attempt = 0; attempt < 2; attempt++) {
                executeMigrationSql(connection, CATEGORY_MIGRATION);
                executeMigrationSql(connection, MAPPING_MIGRATION);
                assertThat(findCategories(connection)).isEqualTo(categoriesBefore);
                assertThat(findFields(connection)).isEqualTo(fieldsBefore);
                assertThat(findMappings(connection)).isEqualTo(expectedMappings);
                assertThat(count(connection, "SELECT COUNT(*) FROM research_field_category_mapping"))
                    .isEqualTo(mappingCount(expectedMappings));
                assertNoDuplicateMappings(connection);
            }

            Map.Entry<String, Set<String>> existingMapping = expectedMappings.entrySet()
                .iterator().next();
            assertThatThrownBy(() -> insertMapping(
                connection,
                existingMapping.getKey(),
                existingMapping.getValue().iterator().next()
            )).isInstanceOf(SQLException.class);
        }
        assertThat(flyway(null).migrate().migrationsExecuted).isZero();
        flyway(null).validate();
    }

    @Test
    void registeringAReferenceFieldAfterMigrationDoesNotAutomaticallyAssignACategory()
        throws Exception {
        flyway(null).migrate();
        String laterRegisteredField = readLifeScienceAssignments().keySet().iterator().next();
        try (Connection connection = dataSource.getConnection()) {
            insertFields(connection, Set.of(laterRegisteredField));
        }

        assertThat(flyway(null).migrate().migrationsExecuted).isZero();

        try (Connection connection = dataSource.getConnection()) {
            assertThat(findFields(connection)).containsOnlyKeys(laterRegisteredField);
            assertThat(findMappings(connection)).isEmpty();
        }
    }

    private void assertLatestCategories(Connection connection) throws SQLException {
        Map<String, CategorySnapshot> categories = findCategories(connection);
        assertThat(categories).hasSize(EXPECTED_CATEGORY_COUNT);
        assertThat(categories.keySet().stream().skip(21))
            .containsExactly("FOOD_NUTRITION", "PLANT_AGRICULTURE", "MOLECULAR_BIOTECH");
        assertThat(List.of(
            categories.get("FOOD_NUTRITION"),
            categories.get("PLANT_AGRICULTURE"),
            categories.get("MOLECULAR_BIOTECH")
        )).extracting(CategorySnapshot::name, CategorySnapshot::displayOrder)
            .containsExactly(
                tuple("식품·영양", 22),
                tuple("식물·농업생명과학", 23),
                tuple("분자·세포생물학·생명공학", 24)
            );
        assertThat(count(connection, "SELECT COUNT(DISTINCT name) FROM research_field_category"))
            .isEqualTo(categories.size());
        assertThat(count(
            connection,
            "SELECT COUNT(DISTINCT display_order) FROM research_field_category"
        )).isEqualTo(categories.size());
        assertThat(categories.values()).allSatisfy(category -> {
            assertThat(category.name()).isNotBlank();
            assertThat(category.description()).isNotBlank();
            assertThat(category.displayOrder()).isPositive();
        });
    }

    private Map<String, Set<String>> readLifeScienceAssignments() throws Exception {
        String sql = new ClassPathResource(MAPPING_MIGRATION)
            .getContentAsString(StandardCharsets.UTF_8);
        List<Assignment> assignments = sql.lines()
            .map(SQL_ASSIGNMENT_PATTERN::matcher)
            .filter(Matcher::matches)
            .map(matcher -> new Assignment(
                matcher.group(1).replace("''", "'"), matcher.group(2)
            ))
            .toList();
        assertThat(assignments)
            .hasSize(EXPECTED_REFERENCE_PAIR_COUNT)
            .doesNotHaveDuplicates();
        Map<String, Set<String>> result = new LinkedHashMap<>();
        assignments.forEach(assignment -> result.computeIfAbsent(
            assignment.fieldName(), ignored -> new LinkedHashSet<>()
        ).add(assignment.categoryCode()));
        assertThat(result)
            .hasSize(EXPECTED_REFERENCE_FIELD_COUNT)
            .containsEntry("지소화성탄수화물", Set.of("FOOD_NUTRITION"))
            .containsEntry("식품 인공지능", Set.of("AI_ML", "FOOD_NUTRITION"))
            .containsEntry(
                "벼-벼도열병균 상호작용을 모델로 한 식물 면역학 연구",
                Set.of("PLANT_AGRICULTURE")
            )
            .containsEntry("유전자 복제", Set.of("MOLECULAR_BIOTECH"))
            .containsEntry(
                "장내 마이크로바이옴",
                Set.of("BIOMED_HEALTH", "FOOD_NUTRITION", "MOLECULAR_BIOTECH")
            )
            .containsEntry("메타분석", Set.of("MATH_STATISTICS"))
            .containsEntry("나노기술", Set.of("CHEMISTRY_MATERIALS"))
            .containsEntry("뇌신경 질환 치료제 개발", Set.of("BIOMED_HEALTH"));
        return result;
    }

    private Map<String, Set<String>> copyAssignments(Map<String, Set<String>> assignments) {
        Map<String, Set<String>> copy = new LinkedHashMap<>();
        assignments.forEach((field, codes) -> copy.put(field, new LinkedHashSet<>(codes)));
        return copy;
    }

    private void validateHibernateSchema() {
        LocalContainerEntityManagerFactoryBean factory =
            new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setPackagesToScan("com.sebu.backend");
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(Map.of(
            "hibernate.hbm2ddl.auto", "validate",
            "hibernate.physical_naming_strategy",
            "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"
        ));
        try {
            factory.afterPropertiesSet();
            assertThat(factory.getObject()).isNotNull();
            assertThat(factory.getObject().isOpen()).isTrue();
        } finally {
            factory.destroy();
        }
    }

    private void executeMigrationSql(Connection connection, String resource) {
        ScriptUtils.executeSqlScript(
            connection,
            new EncodedResource(new ClassPathResource(resource), StandardCharsets.UTF_8)
        );
    }

    private Map<String, CategorySnapshot> findCategories(Connection connection)
        throws SQLException {
        Map<String, CategorySnapshot> result = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT id, code, name, description, display_order, created_at, updated_at
            FROM research_field_category
            ORDER BY display_order
            """); ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                result.put(rows.getString("code"), new CategorySnapshot(
                    rows.getLong("id"),
                    rows.getString("name"),
                    rows.getString("description"),
                    rows.getInt("display_order"),
                    rows.getTimestamp("created_at"),
                    rows.getTimestamp("updated_at")
                ));
            }
        }
        return result;
    }

    private Map<String, Long> findFields(Connection connection) throws SQLException {
        Map<String, Long> result = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT id, name FROM research_field ORDER BY id"
        ); ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                result.put(rows.getString("name"), rows.getLong("id"));
            }
        }
        return result;
    }

    private Map<String, Set<String>> findMappings(Connection connection) throws SQLException {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT field.name, category.code
            FROM research_field_category_mapping mapping
            JOIN research_field field ON field.id = mapping.research_field_id
            JOIN research_field_category category ON category.id = mapping.category_id
            ORDER BY field.id, category.display_order
            """); ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                result.computeIfAbsent(
                    rows.getString("name"), ignored -> new LinkedHashSet<>()
                ).add(rows.getString("code"));
            }
        }
        return result;
    }

    private void insertFields(Connection connection, Set<String> fields) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "INSERT INTO research_field (name) VALUES (?)"
        )) {
            for (String name : fields) {
                statement.setString(1, name);
                statement.addBatch();
            }
            assertThat(statement.executeBatch()).hasSize(fields.size());
        }
    }

    private void insertMapping(Connection connection, String fieldName, String categoryCode)
        throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            INSERT INTO research_field_category_mapping (research_field_id, category_id)
            SELECT field.id, category.id
            FROM research_field field
            JOIN research_field_category category ON category.code = ?
            WHERE field.name = ?
            """)) {
            statement.setString(1, categoryCode);
            statement.setString(2, fieldName);
            assertThat(statement.executeUpdate()).isOne();
        }
    }

    private void assertNoDuplicateMappings(Connection connection) throws SQLException {
        assertThat(count(connection, """
            SELECT COUNT(*)
            FROM (
                SELECT research_field_id, category_id
                FROM research_field_category_mapping
                GROUP BY research_field_id, category_id
                HAVING COUNT(*) > 1
            ) duplicate_pairs
            """)).isZero();
    }

    private long mappingCount(Map<String, Set<String>> assignments) {
        return assignments.values().stream().mapToLong(Set::size).sum();
    }

    private long count(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            assertThat(result.next()).isTrue();
            return result.getLong(1);
        }
    }

    private Flyway flyway(String target) {
        FluentConfiguration configuration = Flyway.configure()
            .dataSource(dataSource)
            .cleanDisabled(false)
            .locations("classpath:db/migration");
        if (target != null) {
            configuration.target(MigrationVersion.fromVersion(target));
        }
        return configuration.load();
    }

    private record Assignment(String fieldName, String categoryCode) {
    }

    private record CategorySnapshot(
        long id,
        String name,
        String description,
        int displayOrder,
        Timestamp createdAt,
        Timestamp updatedAt
    ) {
    }
}

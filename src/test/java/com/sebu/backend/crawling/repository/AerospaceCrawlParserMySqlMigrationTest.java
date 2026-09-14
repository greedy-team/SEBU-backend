package com.sebu.backend.crawling.repository;

import com.sebu.backend.crawling.domain.CrawlParserType;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class AerospaceCrawlParserMySqlMigrationTest {
    private static final String DATABASE = "sebu_aerospace_migration_test";
    private static final String LOCAL_URL = "jdbc:mysql://127.0.0.1:13342/" + DATABASE;
    private static MySQLContainer<?> mysql;
    private static DriverManagerDataSource dataSource;
    private static JdbcTemplate jdbc;

    @BeforeAll
    static void startDatabase() {
        String url = System.getenv("SEBU_AEROSPACE_TEST_MYSQL_URL");
        if (url != null && !url.isBlank()) {
            if (!LOCAL_URL.equals(url)) {
                throw new IllegalArgumentException("Only the dedicated local test URL is allowed: " + LOCAL_URL);
            }
            dataSource = new DriverManagerDataSource(url,
                System.getenv().getOrDefault("SEBU_AEROSPACE_TEST_MYSQL_USERNAME", "root"),
                System.getenv().getOrDefault("SEBU_AEROSPACE_TEST_MYSQL_PASSWORD", ""));
        } else {
            assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                "Docker or the dedicated local MySQL test instance is required");
            mysql = new MySQLContainer<>("mysql:8.0.45").withDatabaseName(DATABASE);
            mysql.start();
            dataSource = new DriverManagerDataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
        }
        jdbc = new JdbcTemplate(dataSource);
    }

    @AfterAll
    static void stopDatabase() {
        if (mysql != null) {
            mysql.stop();
        }
    }

    @BeforeEach
    void cleanOnlyTheDedicatedDatabase() {
        assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).isEqualTo(DATABASE);
        flyway(null).clean();
    }

    @Test
    void blankDatabaseAllowsEveryParserAndPassesHibernateValidationWithoutSeedingSources() {
        flyway(null).migrate();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM crawl_source", Integer.class)).isEqualTo(12);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM professor_crawl_candidate", Integer.class)).isZero();
        assertAllParserTypesCanBeStored();
        assertInvalidParserTypesAreRejected();
        validateHibernate();
        flyway(null).validate();
        assertThat(flyway(null).migrate().migrationsExecuted).isZero();
    }

    @Test
    void v39UpgradePreservesSourcesCandidatesAndReviewStateAndExpandsBothConstraints() {
        flyway("39").migrate();
        for (String type : new String[]{"SEJONG_STANDARD", "SEJONG_QUANTUM"}) {
            insertCandidate(sourceId(type), type);
        }
        jdbc.update("""
            UPDATE professor_crawl_candidate
            SET review_status='APPROVED', reviewed_by='migration-test',
                reviewed_at='2026-09-12 10:00:00', review_revision=1
            """);
        var sourcesBefore = jdbc.queryForList("SELECT * FROM crawl_source ORDER BY id");
        var candidatesBefore = jdbc.queryForList("SELECT * FROM professor_crawl_candidate ORDER BY id");
        assertThatThrownBy(() -> jdbc.update("UPDATE crawl_source SET parser_type='SEJONG_AEROSPACE'"))
            .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update(
            "UPDATE professor_crawl_candidate SET parser_type_at_crawl='SEJONG_AEROSPACE'"))
            .isInstanceOf(DataAccessException.class);

        flyway(null).migrate();

        assertThat(jdbc.queryForList("SELECT * FROM crawl_source ORDER BY id")).isEqualTo(sourcesBefore);
        assertThat(jdbc.queryForList("SELECT * FROM professor_crawl_candidate ORDER BY id"))
            .isEqualTo(candidatesBefore);
        assertAllParserTypesCanBeStored();
        assertInvalidParserTypesAreRejected();
        validateHibernate();
    }

    private void assertAllParserTypesCanBeStored() {
        for (CrawlParserType type : CrawlParserType.values()) {
            jdbc.update("""
                INSERT INTO crawl_source (department_id, source_name, source_url, parser_type, version)
                SELECT MIN(id), ?, ?, ?, 0 FROM department
                """, "Parser test " + type, "https://example.com/aerospace-test/" + type, type.name());
            long id = jdbc.queryForObject("SELECT id FROM crawl_source WHERE source_url=?", Long.class,
                "https://example.com/aerospace-test/" + type);
            insertCandidate(id, type.name());
            assertThat(jdbc.queryForObject("""
                SELECT review_status FROM professor_crawl_candidate WHERE source_id=?
                """, String.class, id)).isEqualTo("PENDING");
        }
    }

    private void assertInvalidParserTypesAreRejected() {
        assertThatThrownBy(() -> jdbc.update("UPDATE crawl_source SET parser_type='UNKNOWN_PARSER'"))
            .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update(
            "UPDATE professor_crawl_candidate SET parser_type_at_crawl='UNKNOWN_PARSER'"))
            .isInstanceOf(DataAccessException.class);
    }

    private void insertCandidate(long sourceId, String type) {
        jdbc.update("""
            INSERT INTO professor_crawl_candidate
                (source_id, professor_name, source_identity_key, source_url_at_crawl, parser_type_at_crawl, version)
            SELECT id, '테스트교수', ?, source_url, ?, 0 FROM crawl_source WHERE id=?
            """, "email:parser-" + sourceId + "@example.com", type, sourceId);
    }

    private long sourceId(String type) {
        return jdbc.queryForObject("SELECT MIN(id) FROM crawl_source WHERE parser_type=?", Long.class, type);
    }

    private Flyway flyway(String target) {
        FluentConfiguration configuration = Flyway.configure().dataSource(dataSource)
            .locations("classpath:db/migration").cleanDisabled(false);
        if (target != null) {
            configuration.target(target);
        }
        return configuration.load();
    }

    private void validateHibernate() {
        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setPackagesToScan("com.sebu.backend");
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(Map.of(
            "hibernate.hbm2ddl.auto", "validate",
            "hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"
        ));
        try {
            factory.afterPropertiesSet();
            assertThat(factory.getObject()).isNotNull();
            assertThat(factory.getObject().isOpen()).isTrue();
        } finally {
            factory.destroy();
        }
    }
}

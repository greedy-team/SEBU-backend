package com.sebu.backend.crawling.repository;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

class MixedCollegeCatalogMySqlMigrationTest extends MixedCollegeCatalogMigrationContract {
    private static final String DATABASE = "sebu_mixed_college_test";
    private static final String LOCAL_URL = "jdbc:mysql://127.0.0.1:13376/" + DATABASE;
    private static DataSource database;
    private static MySQLContainer<?> mysql;

    @BeforeAll
    static void startDedicatedDatabase() {
        String url = System.getenv("SEBU_MIXED_COLLEGE_TEST_MYSQL_URL");
        if (url != null && !url.isBlank()) {
            if (!LOCAL_URL.equals(url)) throw new IllegalArgumentException("Only dedicated local test URL allowed");
            database = new DriverManagerDataSource(url,
                System.getenv().getOrDefault("SEBU_MIXED_COLLEGE_TEST_MYSQL_USERNAME", "root"),
                System.getenv().getOrDefault("SEBU_MIXED_COLLEGE_TEST_MYSQL_PASSWORD", ""));
        } else {
            assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker or dedicated local MySQL required");
            mysql = new MySQLContainer<>("mysql:8.0.45").withDatabaseName(DATABASE);
            mysql.start();
            database = new DriverManagerDataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
        }
    }

    @AfterAll
    static void stopContainer() {
        if (mysql != null) mysql.stop();
    }

    @Override
    protected DataSource dataSource() {
        return database;
    }
}

package com.sebu.backend.college.repository;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class CurrentAcademicCatalogMySqlMigrationTest extends CurrentAcademicCatalogMigrationAssertions {
    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
        .withDatabaseName("sebu_academic_catalog_test");

    @BeforeEach
    void cleanDedicatedContainerDatabase() {
        org.flywaydb.core.Flyway.configure().dataSource(dataSource())
            .cleanDisabled(false).load().clean();
    }

    @Override
    protected DriverManagerDataSource dataSource() {
        return new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    }
}

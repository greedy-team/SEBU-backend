package com.sebu.backend.college.repository;

import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.UUID;

class CurrentAcademicCatalogMigrationTest extends CurrentAcademicCatalogMigrationAssertions {
    private final DriverManagerDataSource dataSource = new DriverManagerDataSource(
        "jdbc:h2:mem:academic-catalog-" + UUID.randomUUID()
            + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "sa", "");

    @Override
    protected DriverManagerDataSource dataSource() {
        return dataSource;
    }
}

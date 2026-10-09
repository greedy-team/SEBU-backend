package com.sebu.backend.laboratory.repository;

import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.util.UUID;

class AerospaceLaboratoryLinksMigrationTest extends AerospaceLaboratoryLinksMigrationContract {
    private final DataSource database = new DriverManagerDataSource(
        "jdbc:h2:mem:aerospace-lab-links-" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");

    @Override
    protected DataSource dataSource() {
        return database;
    }
}

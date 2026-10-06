package com.sebu.backend.researchfield.category.repository;

import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.util.UUID;

class ArtsSportsResearchFieldMigrationTest extends ArtsSportsResearchFieldMigrationContract {
    private final DataSource database = new DriverManagerDataSource(
        "jdbc:h2:mem:sebu_arts_sports_test_" + UUID.randomUUID()
            + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");

    @Override
    protected DataSource dataSource() {
        return database;
    }
}

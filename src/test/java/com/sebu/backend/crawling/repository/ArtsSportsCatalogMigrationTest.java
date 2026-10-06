package com.sebu.backend.crawling.repository;

import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.util.UUID;

class ArtsSportsCatalogMigrationTest extends ArtsSportsCatalogMigrationContract {
    private final DataSource database = new DriverManagerDataSource(
        "jdbc:h2:mem:arts-sports-" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");

    @Override
    protected DataSource dataSource() {
        return database;
    }
}

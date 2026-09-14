package com.sebu.backend.user.repository;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;

@Testcontainers(disabledWithoutDocker = true)
class GraduateGradeMySqlMigrationTest {
    @Container
    final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
        .withCommand("--innodb-buffer-pool-size=64M")
        .withStartupTimeout(Duration.ofMinutes(4));

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void migratesFreshAndExistingDatabasesOnMySql(boolean upgrade) throws Exception {
        GraduateGradeMigrationAssertions.verify(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword(), upgrade);
    }
}

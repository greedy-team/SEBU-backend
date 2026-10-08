package com.sebu.backend.auth.service;

import org.junit.jupiter.api.Tag;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;

/** Runs the same first-login/rotation/logout/withdrawal races against real MySQL locking semantics. */
@Tag("mysql")
@Testcontainers(disabledWithoutDocker = true)
class AuthConcurrencyMySqlIntegrationTest extends AuthConcurrencyIntegrationTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
        .withCommand("--innodb-buffer-pool-size=64M")
        .withStartupTimeout(Duration.ofMinutes(4));

    @DynamicPropertySource
    static void mysql(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> 4);
        registry.add("spring.datasource.hikari.minimum-idle", () -> 0);
        registry.add("spring.jpa.properties.hibernate.generate_statistics", () -> false);
    }
}

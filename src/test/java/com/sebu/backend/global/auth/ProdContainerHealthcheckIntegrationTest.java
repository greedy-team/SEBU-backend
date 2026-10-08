package com.sebu.backend.global.auth;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class ProdContainerHealthcheckIntegrationTest {
    private static final String MYSQL_ALIAS = "prod-healthcheck-mysql";
    private static final Network NETWORK = Network.newNetwork();

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
        .withNetwork(NETWORK)
        .withNetworkAliases(MYSQL_ALIAS);

    private static final ImageFromDockerfile BACKEND_IMAGE = new ImageFromDockerfile()
        .withFileFromPath("Dockerfile", Path.of("Dockerfile"))
        .withFileFromPath("gradlew", Path.of("gradlew"))
        .withFileFromPath("build.gradle", Path.of("build.gradle"))
        .withFileFromPath("settings.gradle", Path.of("settings.gradle"))
        .withFileFromPath("gradle", Path.of("gradle"))
        .withFileFromPath("src", Path.of("src"));

    @Container
    static final GenericContainer<?> BACKEND = new GenericContainer<>(BACKEND_IMAGE)
        .dependsOn(MYSQL)
        .withNetwork(NETWORK)
        .withEnv("SPRING_PROFILES_ACTIVE", "prod")
        .withEnv(
            "DB_URL",
            "jdbc:mysql://" + MYSQL_ALIAS + ":3306/" + MYSQL.getDatabaseName()
                + "?useSSL=false&allowPublicKeyRetrieval=true"
        )
        .withEnv("DB_USERNAME", MYSQL.getUsername())
        .withEnv("DB_PASSWORD", MYSQL.getPassword())
        .withEnv("JWT_SECRET_BASE64", "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")
        .waitingFor(Wait.forHealthcheck().withStartupTimeout(Duration.ofMinutes(3)));

    @Test
    void prodProfileContainerBecomesHealthyWithHttpsForwardingHealthcheck() {
        assertThat(BACKEND.isHealthy()).isTrue();
    }

    @AfterAll
    static void closeNetwork() {
        NETWORK.close();
    }
}

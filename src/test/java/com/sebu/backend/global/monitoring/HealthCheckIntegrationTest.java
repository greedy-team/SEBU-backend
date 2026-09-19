package com.sebu.backend.global.monitoring;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.HealthContributor;
import org.springframework.boot.actuate.health.HealthContributorRegistry;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "spring.config.additional-location=file:src/main/resources/application.yml",
    "JWT_SECRET_BASE64=MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=",
    "app.auth.transport.require-https=true"
})
@ActiveProfiles("local")
@AutoConfigureMockMvc
class HealthCheckIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired HealthContributorRegistry contributors;
    private final HealthIndicator databaseHealth = mock(HealthIndicator.class, CALLS_REAL_METHODS);
    private HealthContributor originalDatabaseHealth;

    @BeforeEach
    void replaceDatabaseHealth() {
        originalDatabaseHealth = contributors.unregisterContributor("db");
        contributors.registerContributor("db", databaseHealth);
    }

    @AfterEach
    void restoreDatabaseHealth() {
        contributors.unregisterContributor("db");
        contributors.registerContributor("db", originalDatabaseHealth);
    }

    @Test
    void worksWithoutMonitoringTokenAndHidesDatabaseDetails() throws Exception {
        when(databaseHealth.health()).thenReturn(Health.up().withDetail("database", "PRIVATE_DATABASE").build());
        String body = mockMvc.perform(get("/actuator/health/readiness").with(https()))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(body).isEqualTo("{\"status\":\"UP\"}");
        mockMvc.perform(get("/actuator/prometheus").with(https())).andExpect(status().is4xxClientError());
    }

    @Test
    void returnsServiceUnavailableWithoutLeakingDatabaseFailure() throws Exception {
        when(databaseHealth.health()).thenReturn(Health.down(new IllegalStateException("PRIVATE_CONNECTION"))
            .withDetail("database", "PRIVATE_DATABASE").build());
        String body = mockMvc.perform(get("/actuator/health/readiness").with(https()))
            .andExpect(status().isServiceUnavailable()).andReturn().getResponse().getContentAsString();
        assertThat(body).isEqualTo("{\"status\":\"DOWN\"}");
    }

    @Test
    void onlyAllowsTheReadinessGetAndMaintainsHttpsRequirement() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().is3xxRedirection());
        for (String path : new String[]{"/actuator/health", "/actuator/health/readiness/db", "/actuator/health/liveness"}) {
            mockMvc.perform(get(path).with(https())).andExpect(status().isForbidden());
        }
        mockMvc.perform(post("/actuator/health/readiness").with(https())).andExpect(status().isForbidden());
    }

    private static RequestPostProcessor https() {
        return request -> {
            request.setScheme("https");
            request.setSecure(true);
            request.setServerPort(443);
            return request;
        };
    }
}

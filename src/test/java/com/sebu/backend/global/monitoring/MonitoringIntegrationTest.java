package com.sebu.backend.global.monitoring;

import com.sebu.backend.auth.controller.AuthCookieFactory;
import com.sebu.backend.auth.token.JwtAccessTokenService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    // Test application.yml shadows the main file; load the actual deployment defaults too.
    "spring.config.additional-location=file:src/main/resources/application.yml",
    "JWT_SECRET_BASE64=MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=",
    "MONITORING_TOKEN=" + MonitoringIntegrationTest.TOKEN,
    "app.auth.transport.require-https=true"
})
@ActiveProfiles({"local", "monitoring"})
@AutoConfigureMockMvc
@AutoConfigureObservability
class MonitoringIntegrationTest {
    static final String TOKEN = "test-only-monitoring-secret-with-at-least-43-characters";

    @Autowired MockMvc mockMvc;
    @Autowired MeterRegistry registry;
    @Autowired JwtAccessTokenService accessTokenService;

    @Test
    void readinessProbeDoesNotPolluteApiMetricsAndExposesOnlyStatus() throws Exception {
        long before = registry.find("http.server.requests").timers().stream().mapToLong(Timer::count).sum();
        for (int i = 0; i < 3; i++) {
            String body = mockMvc.perform(get("/actuator/health/readiness").with(https()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(body).isEqualTo("{\"status\":\"UP\"}");
        }
        assertThat(registry.find("http.server.requests").timers().stream().mapToLong(Timer::count).sum())
            .isEqualTo(before);
        mockMvc.perform(get("/api/v1/laboratories").with(https())).andExpect(status().isOk());
        assertThat(registry.find("http.server.requests").timers().stream().mapToLong(Timer::count).sum())
            .isEqualTo(before + 1);
    }

    @Test
    void exportsSelectedMetricsOnlyWithMonitoringToken() throws Exception {
        mockMvc.perform(get("/api/v1/laboratories").with(https())).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/research-field-categories").with(https())).andExpect(status().isOk());
        long requestCount = registry.find("http.server.requests").timers().stream().mapToLong(Timer::count).sum();
        String body = mockMvc.perform(get("/actuator/prometheus").with(https())
                .header("Authorization", "Bearer " + TOKEN))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(body).contains("http_server_requests_seconds_count", "http_server_requests_seconds_bucket",
            "uri=\"/api/v1/laboratories\"", "uri=\"/api/v1/research-field-categories\"",
            "jvm_memory_used_bytes", "jvm_memory_max_bytes", "hikaricp_connections_active");
        assertThat(body).doesNotContain("jvm_threads_", "system_cpu_", "process_cpu_", "disk_",
            "uri=\"/actuator", "http_server_requests_active", "exception=", "error=", "outcome=");
        assertThat(registry.find("http.server.requests").timers().stream().mapToLong(Timer::count).sum())
            .isEqualTo(requestCount);
    }

    @Test
    void rejectsMissingWrongCookieAndQueryCredentials() throws Exception {
        mockMvc.perform(get("/actuator/prometheus").with(https())).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/prometheus").with(https())
            .header("Authorization", "Bearer wrong-token")).andExpect(status().isUnauthorized());
        String userToken = accessTokenService.issue(1L, 0L);
        mockMvc.perform(get("/actuator/prometheus").with(https())
            .cookie(new Cookie(AuthCookieFactory.ACCESS_COOKIE, userToken)))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/prometheus").with(https())
            .header("Authorization", "Bearer " + userToken)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/prometheus").with(https())
            .param("access_token", TOKEN)).andExpect(status().isUnauthorized());
    }

    @Test
    void monitoringCredentialsCannotAuthenticateApplicationRequests() throws Exception {
        mockMvc.perform(get("/api/v1/me").with(https())
            .header("Authorization", "Bearer " + TOKEN)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/laboratories").with(https())).andExpect(status().isOk());
    }

    @Test
    void forbidsOtherActuatorEndpointsAndWriteMethods() throws Exception {
        for (String path : new String[]{"/actuator", "/actuator/env", "/actuator/heapdump", "/actuator/health"}) {
            mockMvc.perform(get(path).with(https()).header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isForbidden());
        }
        mockMvc.perform(post("/actuator/prometheus").with(https())
            .header("Authorization", "Bearer " + TOKEN)).andExpect(status().isForbidden());
    }

    @Test
    void requiresHttpsWhenConfigured() throws Exception {
        mockMvc.perform(get("/actuator/prometheus").header("Authorization", "Bearer " + TOKEN))
            .andExpect(status().is3xxRedirection());
    }

    @Test
    void limitsBucketsToCoreReadApisAndIgnoresUnselectedMeters() {
        Timer search = timer("/api/v1/laboratories", "GET");
        Timer posts = timer("/api/v1/posts", "GET");
        Timer write = timer("/api/v1/posts", "POST");
        Timer other = timer("/api/v1/me", "GET");
        search.record(Duration.ofMillis(100));
        assertThat(search.takeSnapshot().histogramCounts()).hasSize(7);
        assertThat(posts.takeSnapshot().histogramCounts()).hasSize(7);
        assertThat(write.takeSnapshot().histogramCounts()).hasSize(1);
        assertThat(other.takeSnapshot().histogramCounts()).hasSize(1);
        registry.counter("sebu.unselected.metric").increment();
        assertThat(registry.find("sebu.unselected.metric").counter()).isNull();
        timer("/actuator/prometheus", "GET").record(Duration.ofMillis(1));
        assertThat(registry.find("http.server.requests").tag("uri", "/actuator/prometheus").timer()).isNull();
    }

    private Timer timer(String uri, String method) {
        return Timer.builder("http.server.requests")
            .tags("uri", uri, "method", method, "status", "200", "outcome", "SUCCESS", "exception", "none")
            .register(registry);
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

package com.sebu.backend.global.monitoring;

import com.sebu.backend.auth.config.AuthTransportProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.assertj.core.api.Assertions.assertThat;

class MonitoringSecurityConfigurationTest {
    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
        .withUserConfiguration(SecurityTestConfiguration.class, MonitoringSecurityConfiguration.class);

    @Test
    void refusesToStartMonitoringWithoutASeparateStrongToken() {
        for (String token : new String[]{"", "too-short", " ".repeat(43), "a".repeat(257),
                "a".repeat(43) + "한글", "a".repeat(43) + ":", "a".repeat(43) + "=suffix",
                "a".repeat(20) + "\r\n" + "b".repeat(23), "a".repeat(43) + ","}) {
            runner.withPropertyValues("spring.profiles.active=monitoring", "MONITORING_TOKEN=" + token)
                .run(context -> assertThat(context).hasFailed());
        }
    }

    @Test
    void configuresMonitoringWithValidToken() {
        for (String token : new String[]{MonitoringIntegrationTest.TOKEN, "a".repeat(43),
                "a".repeat(40) + "+/A=", "a".repeat(40) + "-_.~"}) {
            runner.withPropertyValues("spring.profiles.active=monitoring", "MONITORING_TOKEN=" + token)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasBean("monitoringSecurityFilterChain");
                });
        }
    }

    @Test
    void nonWebJobsDoNotRequireMonitoringCredentials() {
        new ApplicationContextRunner().withUserConfiguration(
                MonitoringSecurityConfiguration.class, HealthCheckSecurityConfiguration.class)
            .withPropertyValues("spring.profiles.active=monitoring")
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).doesNotHaveBean(SecurityFilterChain.class);
            });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebSecurity
    @EnableWebMvc
    @EnableConfigurationProperties(AuthTransportProperties.class)
    static class SecurityTestConfiguration {
    }
}

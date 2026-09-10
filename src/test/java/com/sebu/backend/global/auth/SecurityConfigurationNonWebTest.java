package com.sebu.backend.global.auth;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.web.SecurityFilterChain;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigurationNonWebTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
        .withUserConfiguration(SecurityConfiguration.class)
        .withPropertyValues(
            "app.auth.token.jwt-secret-base64="
                + "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
            "app.auth.token.access-token-expiration=30m",
            "app.auth.token.refresh-token-expiration=14d",
            "app.auth.token.absolute-session-expiration=30d",
            "app.auth.csrf.allowed-origins=https://sebu-frontend.vercel.app",
            "app.auth.cookie.secure=true"
        );

    @Test
    void nonWebContextDoesNotRequireSecurityFilterChain() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(SecurityFilterChain.class);
        });
    }
}

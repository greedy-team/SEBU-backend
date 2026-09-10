package com.sebu.backend.auth.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class AuthConfigurationValidationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
        .withUserConfiguration(PropertiesConfiguration.class)
        .withPropertyValues(
            "app.auth.token.jwt-secret-base64=MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=",
            "app.auth.token.access-token-expiration=30m",
            "app.auth.token.refresh-token-expiration=14d",
            "app.auth.token.absolute-session-expiration=30d",
            "app.auth.csrf.allowed-origins[0]=https://sebu-frontend.vercel.app"
        );

    @Test
    void acceptsCurrentDefaults() {
        runner.run(context -> assertThat(context).hasNotFailed());
    }

    @ParameterizedTest
    @CsvSource({
        "access-token-expiration,0m", "access-token-expiration,-1m",
        "refresh-token-expiration,0d", "refresh-token-expiration,-1d"
    })
    void rejectsNonPositiveDurationsDuringConfigurationBinding(String property, String value) {
        runner.withPropertyValues("app.auth.token." + property + "=" + value)
            .run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "*", "https://*.vercel.app", "https://example.com/path",
        "https://user@example.com", "https://example.com?query=value", "null"
    })
    void rejectsInvalidOriginsDuringConfigurationBinding(String origin) {
        runner.withPropertyValues("app.auth.csrf.allowed-origins[0]=" + origin)
            .run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({TokenProperties.class, AuthCsrfProperties.class})
    static class PropertiesConfiguration {
    }
}

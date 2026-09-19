package com.sebu.backend.global.monitoring;

import com.sebu.backend.auth.config.AuthTransportProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.Assert;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpMethod.GET;
import static org.springframework.security.config.Customizer.withDefaults;

@Configuration(proxyBeanMethods = false)
@Profile("monitoring")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class MonitoringSecurityConfiguration {
    @Bean
    @Order(1)
    SecurityFilterChain monitoringSecurityFilterChain(
        HttpSecurity http,
        AuthTransportProperties transportProperties,
        @Value("${MONITORING_TOKEN:}") String token
    ) throws Exception {
        Assert.isTrue(token.length() >= 43 && token.length() <= 256
                && token.matches("[A-Za-z0-9._~+/-]+=*"),
            "MONITORING_TOKEN must be 43-256 Bearer-compatible ASCII characters; generate it from 32 random bytes");
        byte[] expectedDigest = digest(token);

        if (transportProperties.requireHttps()) {
            http.redirectToHttps(withDefaults());
        }

        return http.securityMatcher("/actuator/**")
            // Only a read-only GET endpoint is permitted; ambient cookie authentication is not used.
            .csrf(AbstractHttpConfigurer::disable)
            .requestCache(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(GET, "/actuator/prometheus").hasAuthority("MONITORING")
                .anyRequest().denyAll())
            .oauth2ResourceServer(resourceServer -> resourceServer
                .bearerTokenResolver(new DefaultBearerTokenResolver())
                .opaqueToken(opaque -> opaque.introspector(candidate -> {
                    // Validate locally: no database, external identity provider, or password hashing work.
                    if (!MessageDigest.isEqual(expectedDigest, digest(candidate))) {
                        throw new BadOpaqueTokenException("Invalid monitoring token");
                    }
                    return new DefaultOAuth2AuthenticatedPrincipal(
                        "grafana-cloud", Map.of("sub", "grafana-cloud"),
                        List.of(new SimpleGrantedAuthority("MONITORING")));
                })))
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .build();
    }

    private static byte[] digest(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}

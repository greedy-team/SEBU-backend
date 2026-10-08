package com.sebu.backend.global.monitoring;

import com.sebu.backend.auth.config.AuthTransportProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

import static org.springframework.http.HttpMethod.GET;
import static org.springframework.security.config.Customizer.withDefaults;

/** Docker 상태 확인은 monitoring 프로필 및 수집 토큰 없이도 동작해야 한다. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class HealthCheckSecurityConfiguration {
    @Bean
    @Order(0)
    SecurityFilterChain healthCheckSecurityFilterChain(HttpSecurity http,
                                                     AuthTransportProperties transportProperties) throws Exception {
        if (transportProperties.requireHttps()) {
            http.redirectToHttps(withDefaults());
        }
        return http.securityMatcher("/actuator/health", "/actuator/health/**")
            .csrf(AbstractHttpConfigurer::disable)
            .requestCache(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(GET, "/actuator/health/readiness").permitAll()
                .anyRequest().denyAll())
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .build();
    }
}

package com.sebu.backend.global.auth;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.sebu.backend.auth.config.AuthCookieProperties;
import com.sebu.backend.auth.config.AuthTransportProperties;
import com.sebu.backend.auth.config.TokenProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.time.Instant;
import java.util.List;

import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.PATCH;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.security.config.Customizer.withDefaults;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
        TokenProperties.class,
        AuthCookieProperties.class,
        AuthTransportProperties.class
})
public class SecurityConfiguration {

    private static final String AUTH_API_PATH = "/api/v1/auth";

    private static final OAuth2Error EXPIRED_TOKEN_ERROR =
            new OAuth2Error(
                    "access_token_expired",
                    "The access token has expired",
                    null
            );

    private static final OAuth2Error INVALID_CLAIMS_ERROR =
            new OAuth2Error(
                    "access_token_invalid",
                    "The access token claims are invalid",
                    null
            );

    @Bean
    @ConditionalOnWebApplication(
            type = ConditionalOnWebApplication.Type.SERVLET
    )
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationEntryPoint authenticationEntryPoint,
            BearerTokenResolver bearerTokenResolver,
            AuthTransportProperties transportProperties
    ) throws Exception {

        if (transportProperties.requireHttps()) {
            http.redirectToHttps(withDefaults());
        }

        http
                .csrf(AbstractHttpConfigurer::disable)

                .sessionManagement(
                        session ->
                                session.sessionCreationPolicy(
                                        SessionCreationPolicy.STATELESS
                                )
                )

                .authorizeHttpRequests(authorize -> authorize

                        // 개발 환경 API 문서
                        .requestMatchers(
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs",
                                "/v3/api-docs/**",
                                "/v3/api-docs.yaml"
                        ).permitAll()

                        // 인증 API는 로그인하지 않은 사용자도 접근 가능
                        .requestMatchers(
                                POST,
                                "/api/v1/auth/sejong/login",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/logout"
                        ).permitAll()

                        // 공개 GET API
                        .requestMatchers(
                                GET,
                                "/api/v1/laboratories",
                                "/api/v1/laboratories/*/reviews",
                                "/api/v1/laboratories/*/review-summary",
                                "/api/v1/research-field-categories",
                                "/api/v1/posts",
                                "/api/v1/posts/**",
                                "/api/v1/colleges",
                                "/api/v1/users/*/community-profile"
                        ).permitAll()

                        .requestMatchers(
                                GET,
                                "/api/v1/me"
                        ).authenticated()

                        .requestMatchers(
                                PATCH,
                                "/api/v1/me/profile"
                        ).authenticated()

                        .anyRequest().authenticated()
                )

                .exceptionHandling(
                        exceptions ->
                                exceptions.authenticationEntryPoint(
                                        authenticationEntryPoint
                                )
                )

                .oauth2ResourceServer(
                        resourceServer -> resourceServer
                                .jwt(
                                        jwt ->
                                                jwt.jwtAuthenticationConverter(
                                                        jwtAuthenticationConverter()
                                                )
                                )
                                .bearerTokenResolver(
                                        bearerTokenResolver
                                )
                                .authenticationEntryPoint(
                                        authenticationEntryPoint
                                )
                )

                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable);

        return http.build();
    }

    @Bean
    BearerTokenResolver bearerTokenResolver() {
        DefaultBearerTokenResolver delegate =
                new DefaultBearerTokenResolver();

        return request ->
                isAuthApi(request)
                        ? null
                        : delegate.resolve(request);
    }

    private boolean isAuthApi(
            HttpServletRequest request
    ) {
        String requestPath =
                request.getRequestURI()
                        .substring(
                                request.getContextPath().length()
                        );

        return requestPath.equals(AUTH_API_PATH)
                || requestPath.startsWith(
                AUTH_API_PATH + "/"
        );
    }

    @Bean
    SecretKey jwtSecretKey(
            TokenProperties properties
    ) {
        return new SecretKeySpec(
                properties.jwtSecretBytes(),
                "HmacSHA256"
        );
    }

    @Bean
    JwtEncoder jwtEncoder(
            SecretKey jwtSecretKey
    ) {
        return new NimbusJwtEncoder(
                new ImmutableSecret<>(jwtSecretKey)
        );
    }

    @Bean
    JwtDecoder jwtDecoder(
            SecretKey jwtSecretKey
    ) {
        NimbusJwtDecoder decoder =
                NimbusJwtDecoder
                        .withSecretKey(jwtSecretKey)
                        .macAlgorithm(MacAlgorithm.HS256)
                        .build();

        OAuth2TokenValidator<Jwt> validator =
                new DelegatingOAuth2TokenValidator<>(
                        JwtValidators.createDefault(),
                        this::validateRequiredClaims
                );

        decoder.setJwtValidator(validator);

        return decoder;
    }

    private Converter<Jwt, JwtAuthenticationToken>
    jwtAuthenticationConverter() {

        return jwt ->
                new JwtAuthenticationToken(
                        jwt,
                        List.of(
                                new SimpleGrantedAuthority(
                                        "ROLE_"
                                                + jwt.getClaimAsString(
                                                "role"
                                        )
                                )
                        ),
                        jwt.getSubject()
                );
    }

    private OAuth2TokenValidatorResult
    validateRequiredClaims(
            Jwt jwt
    ) {

        if (jwt.getExpiresAt() == null
                || !Instant.now()
                .isBefore(jwt.getExpiresAt())) {

            return OAuth2TokenValidatorResult.failure(
                    EXPIRED_TOKEN_ERROR
            );
        }

        if (!"USER".equals(
                jwt.getClaimAsString("role")
        ) || !isLong(jwt.getSubject())) {

            return OAuth2TokenValidatorResult.failure(
                    INVALID_CLAIMS_ERROR
            );
        }

        return OAuth2TokenValidatorResult.success();
    }

    private boolean isLong(
            String value
    ) {
        if (value == null) {
            return false;
        }

        try {
            Long.parseLong(value);
            return true;
        } catch (NumberFormatException exception) {
            return false;
        }
    }
}

package com.sebu.backend.auth.token;

import com.sebu.backend.auth.config.TokenProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.JwtEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class JwtAccessTokenServiceTest {
    @Autowired
    JwtAccessTokenService accessTokenService;

    @Autowired
    JwtDecoder jwtDecoder;

    @Autowired
    JwtEncoder jwtEncoder;

    @Autowired
    TokenProperties properties;

    @Test
    void issuesHs256TokenWithOnlyRequiredClaims() {
        String token = accessTokenService.issue(17L, 3L);

        Jwt jwt = jwtDecoder.decode(token);

        assertThat(jwt.getHeaders().get("alg")).isEqualTo("HS256");
        assertThat(jwt.getClaims().keySet()).isEqualTo(Set.of("sub", "role", "authVersion", "iat", "exp"));
        assertThat(jwt.getSubject()).isEqualTo("17");
        assertThat(jwt.getClaimAsString("role")).isEqualTo("USER");
        assertThat(((Number) jwt.getClaim("authVersion")).longValue()).isEqualTo(3L);
        assertThat(accessTokenService.expiresInSeconds()).isEqualTo(1800);
    }

    @Test
    void rejectsExpiredAccessToken() {
        Instant expiredIssueTime = Instant.now().minus(properties.accessTokenExpiration()).minusSeconds(1);
        JwtAccessTokenService expiredIssuer = new JwtAccessTokenService(
            jwtEncoder,
            properties,
            Clock.fixed(expiredIssueTime, ZoneOffset.UTC)
        );

        assertThatThrownBy(() -> jwtDecoder.decode(expiredIssuer.issue(17L, 0L)))
            .isInstanceOf(JwtValidationException.class);
    }
}

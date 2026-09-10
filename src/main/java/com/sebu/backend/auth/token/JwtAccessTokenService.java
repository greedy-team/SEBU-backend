package com.sebu.backend.auth.token;

import com.sebu.backend.auth.config.TokenProperties;
import com.sebu.backend.auth.exception.AuthSessionExpiredException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

@Component
public class JwtAccessTokenService {
    private static final String ROLE = "USER";

    private final JwtEncoder jwtEncoder;
    private final TokenProperties properties;
    private final Clock clock;

    @Autowired
    public JwtAccessTokenService(JwtEncoder jwtEncoder, TokenProperties properties) {
        this(jwtEncoder, properties, Clock.systemUTC());
    }

    JwtAccessTokenService(JwtEncoder jwtEncoder, TokenProperties properties, Clock clock) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
        this.clock = clock;
    }

    public String issue(Long userId) {
        return issueUntil(userId, clock.instant().plus(properties.accessTokenExpiration())).value();
    }

    public IssuedAccessToken issueUntil(Long userId, Instant absoluteExpiresAt) {
        if (userId == null) {
            throw new IllegalArgumentException("ACCESS_TOKEN_USER_ID_REQUIRED");
        }
        Instant issuedAt = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        if (!absoluteExpiresAt.isAfter(issuedAt)) {
            throw new AuthSessionExpiredException();
        }
        Instant normalExpiry = issuedAt.plus(properties.accessTokenExpiration());
        Instant expiresAt = (normalExpiry.isBefore(absoluteExpiresAt) ? normalExpiry : absoluteExpiresAt)
            .truncatedTo(ChronoUnit.SECONDS);
        if (!expiresAt.isAfter(issuedAt)) {
            throw new IllegalArgumentException("ACCESS_TOKEN_EXPIRATION_INVALID");
        }
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .subject(userId.toString())
            .claim("role", ROLE)
            .issuedAt(issuedAt)
            .expiresAt(expiresAt)
            .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        return new IssuedAccessToken(jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue(),
            Duration.between(issuedAt, expiresAt).toSeconds());
    }

    public long expiresInSeconds() {
        return properties.accessTokenExpiration().toSeconds();
    }

    public record IssuedAccessToken(String value, long expiresIn) {
        @Override
        public String toString() {
            return "IssuedAccessToken[value=REDACTED, expiresIn=" + expiresIn + "]";
        }
    }
}

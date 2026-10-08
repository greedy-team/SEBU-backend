package com.sebu.backend.auth.token;

import com.sebu.backend.auth.config.TokenProperties;
import com.sebu.backend.auth.exception.AuthSessionExpiredException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtAccessTokenLifetimeTest {
    @Test
    void accessJwtAndExpiresInAreCappedByAbsoluteSessionExpiry() {
        Instant now = Instant.parse("2026-09-30T23:59:50Z");
        JwtEncoder encoder = mock(JwtEncoder.class);
        when(encoder.encode(any())).thenReturn(Jwt.withTokenValue("test-secret-token")
            .header("alg", "HS256").subject("1").build());
        TokenProperties properties = new TokenProperties("test-only", Duration.ofMinutes(30),
            Duration.ofDays(14), Duration.ofDays(30));
        var service = new JwtAccessTokenService(encoder, properties, Clock.fixed(now, ZoneOffset.UTC));
        var issued = service.issueUntil(1L, 0L, now.plusSeconds(10));
        ArgumentCaptor<JwtEncoderParameters> captured = ArgumentCaptor.forClass(JwtEncoderParameters.class);
        verify(encoder).encode(captured.capture());
        assertThat(captured.getValue().getClaims().getExpiresAt()).isEqualTo(now.plusSeconds(10));
        assertThat(issued.expiresIn()).isEqualTo(10);
        assertThat(issued.toString()).doesNotContain(issued.value());
        assertThatThrownBy(() -> service.issueUntil(1L, 0L, now)).isInstanceOf(AuthSessionExpiredException.class);
    }
}

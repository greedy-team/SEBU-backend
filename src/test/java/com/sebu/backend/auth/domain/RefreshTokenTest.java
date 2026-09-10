package com.sebu.backend.auth.domain;

import com.sebu.backend.user.domain.AppUser;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

class RefreshTokenTest {
    private final LocalDateTime loginAt = LocalDateTime.of(2026, 9, 1, 0, 0);
    private final Duration idle = Duration.ofDays(14);

    @Test
    void rotationExtendsIdleExpiryButNeverTheOriginalThirtyDayDeadline() {
        RefreshToken first = start();
        assertThat(first.getExpiresAt()).isEqualTo(loginAt.plusDays(14));
        RefreshToken second = first.rotate("b".repeat(64), loginAt.plusDays(13), idle);
        RefreshToken third = second.rotate("c".repeat(64), loginAt.plusDays(26), idle);
        assertThat(second.getExpiresAt()).isEqualTo(loginAt.plusDays(27));
        assertThat(third.getExpiresAt()).isEqualTo(loginAt.plusDays(30));
        assertThat(third.getAbsoluteExpiresAt()).isEqualTo(first.getAbsoluteExpiresAt());
        assertThat(third.getSessionId()).isEqualTo(first.getSessionId());
        assertThat(first.isUsableAt(loginAt.plusDays(13))).isFalse();
        assertThat(third.isUsableAt(loginAt.plusDays(30))).isFalse();
        assertThatThrownBy(() -> third.rotate("d".repeat(64), loginAt.plusDays(30), idle))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void idleExpiryAndRevocationAlsoBlockRotation() {
        RefreshToken first = start();
        assertThat(first.isUsableAt(loginAt.plusDays(14))).isFalse();
        assertThatThrownBy(() -> first.rotate("b".repeat(64), loginAt.plusDays(14), idle))
            .isInstanceOf(IllegalStateException.class);
        first.revoke(loginAt.plusDays(1));
        assertThat(first.isUsableAt(loginAt.plusDays(2))).isFalse();
        assertThat(start().getSessionId()).isNotEqualTo(first.getSessionId());
    }

    private RefreshToken start() {
        return RefreshToken.start(AppUser.sejong("test-user"), "a".repeat(64), loginAt, idle, Duration.ofDays(30));
    }
}

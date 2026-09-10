package com.sebu.backend.auth.service;

import com.sebu.backend.auth.config.AccountLifecycleProperties;
import com.sebu.backend.auth.config.TokenProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

@Component
public class AccountRecoveryPolicy {
    private final AccountLifecycleProperties properties;
    private final Duration effectiveCooldown;

    public AccountRecoveryPolicy(AccountLifecycleProperties properties, TokenProperties tokenProperties) {
        this.properties = properties;
        Duration tokenBound = tokenProperties.accessTokenExpiration().plus(properties.accessTokenSafetyMargin());
        this.effectiveCooldown = tokenBound.compareTo(properties.minimumRecoveryCooldown()) > 0
            ? tokenBound
            : properties.minimumRecoveryCooldown();
        if (effectiveCooldown.compareTo(properties.recoveryWindow()) >= 0) {
            throw new IllegalArgumentException("ACCOUNT_RECOVERY_COOLDOWN_MUST_BE_SHORTER_THAN_WINDOW");
        }
    }

    public RecoveryPhase phase(LocalDateTime deletedAt, LocalDateTime now) {
        Objects.requireNonNull(deletedAt, "DELETED_AT_REQUIRED");
        Objects.requireNonNull(now, "CURRENT_TIME_REQUIRED");
        if (!now.isBefore(recoverableUntil(deletedAt))) {
            return RecoveryPhase.EXPIRED;
        }
        if (now.isBefore(recoveryAvailableAt(deletedAt))) {
            return RecoveryPhase.COOLDOWN;
        }
        return RecoveryPhase.RECOVERABLE;
    }

    public LocalDateTime recoveryAvailableAt(LocalDateTime deletedAt) {
        return Objects.requireNonNull(deletedAt, "DELETED_AT_REQUIRED").plus(effectiveCooldown);
    }

    public LocalDateTime recoverableUntil(LocalDateTime deletedAt) {
        return Objects.requireNonNull(deletedAt, "DELETED_AT_REQUIRED").plus(properties.recoveryWindow());
    }

    public LocalDateTime recoveryTokenExpiresAt(LocalDateTime deletedAt, LocalDateTime now) {
        LocalDateTime normalExpiration = Objects.requireNonNull(now, "CURRENT_TIME_REQUIRED")
            .plus(properties.recoveryTokenExpiration());
        LocalDateTime recoveryDeadline = recoverableUntil(deletedAt);
        return normalExpiration.isBefore(recoveryDeadline) ? normalExpiration : recoveryDeadline;
    }

    public LocalDateTime expiredWithdrawalThreshold(LocalDateTime now) {
        return Objects.requireNonNull(now, "CURRENT_TIME_REQUIRED").minus(properties.recoveryWindow());
    }

    public Duration effectiveCooldown() {
        return effectiveCooldown;
    }

    public enum RecoveryPhase {
        COOLDOWN,
        RECOVERABLE,
        EXPIRED
    }
}

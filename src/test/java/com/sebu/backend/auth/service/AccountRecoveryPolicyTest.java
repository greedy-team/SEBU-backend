package com.sebu.backend.auth.service;

import com.sebu.backend.account.config.AccountLifecycleProperties;
import com.sebu.backend.account.service.AccountRecoveryPolicy;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountRecoveryPolicyTest {
    private static final AccountLifecycleProperties ACCOUNT_PROPERTIES = new AccountLifecycleProperties(
        Duration.ofDays(30), Duration.ofHours(1), Duration.ofMinutes(5), 500, 100
    );

    @Test
    void usesTheConfiguredCooldownIndependentlyOfAccessTokenLifetime() {
        AccountRecoveryPolicy policy = new AccountRecoveryPolicy(ACCOUNT_PROPERTIES);

        assertThat(policy.effectiveCooldown()).isEqualTo(Duration.ofHours(1));
    }

    @Test
    void appliesInclusiveCooldownAndRecoveryWindowBoundaries() {
        AccountRecoveryPolicy policy = new AccountRecoveryPolicy(ACCOUNT_PROPERTIES);
        LocalDateTime deletedAt = LocalDateTime.of(2026, 9, 10, 0, 0);

        assertThat(policy.phase(deletedAt, deletedAt.plusHours(1).minusSeconds(1)))
            .isEqualTo(AccountRecoveryPolicy.RecoveryPhase.COOLDOWN);
        assertThat(policy.phase(deletedAt, deletedAt.plusHours(1)))
            .isEqualTo(AccountRecoveryPolicy.RecoveryPhase.RECOVERABLE);
        assertThat(policy.phase(deletedAt, deletedAt.plusDays(30)))
            .isEqualTo(AccountRecoveryPolicy.RecoveryPhase.EXPIRED);
    }

    @Test
    void rejectsACooldownThatLeavesNoRecoveryWindow() {
        AccountLifecycleProperties invalid = new AccountLifecycleProperties(
            Duration.ofDays(30), Duration.ofDays(30), Duration.ofMinutes(5), 500, 100
        );

        assertThatThrownBy(() -> new AccountRecoveryPolicy(invalid))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("ACCOUNT_RECOVERY_COOLDOWN_MUST_BE_SHORTER_THAN_WINDOW");
    }
}

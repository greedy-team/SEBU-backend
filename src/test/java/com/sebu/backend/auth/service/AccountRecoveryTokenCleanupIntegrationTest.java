package com.sebu.backend.auth.service;

import com.sebu.backend.auth.domain.AccountRecoveryToken;
import com.sebu.backend.auth.repository.AccountRecoveryTokenRepository;
import com.sebu.backend.user.domain.AppUser;
import com.sebu.backend.user.repository.AppUserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "app.auth.account.batch-size=1")
@Transactional
class AccountRecoveryTokenCleanupIntegrationTest {
    @Autowired RecoveryTokenCleanupService cleanupService;
    @Autowired AccountRecoveryTokenRepository recoveryTokens;
    @Autowired AppUserRepository users;
    @Autowired EntityManager entityManager;

    @Test
    void deletesExpiredTokensInBoundedIdempotentBatches() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 1, 0, 0);
        saveToken("expired-recovery-user", "a".repeat(64), now.minusMinutes(1), now.minusMinutes(2));
        saveToken("boundary-recovery-user", "b".repeat(64), now, now.minusMinutes(1));
        saveToken("active-recovery-user", "c".repeat(64), now.plusMinutes(1), now);
        recoveryTokens.flush();

        assertThat(cleanupService.deleteExpiredBatch(now)).isOne();
        assertThat(cleanupService.deleteExpiredBatch(now)).isOne();
        assertThat(cleanupService.deleteExpiredBatch(now)).isZero();

        entityManager.clear();
        assertThat(recoveryTokens.count()).isOne();
        assertThat(users.count()).isEqualTo(3);
    }

    private void saveToken(
        String providerUserId,
        String tokenHash,
        LocalDateTime expiresAt,
        LocalDateTime createdAt
    ) {
        AppUser user = users.save(AppUser.sejong(providerUserId));
        recoveryTokens.save(new AccountRecoveryToken(user, tokenHash, expiresAt, createdAt));
    }
}

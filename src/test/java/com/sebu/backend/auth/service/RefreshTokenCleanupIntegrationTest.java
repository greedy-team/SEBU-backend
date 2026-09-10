package com.sebu.backend.auth.service;

import com.sebu.backend.auth.domain.RefreshToken;
import com.sebu.backend.auth.repository.RefreshTokenRepository;
import com.sebu.backend.user.domain.AppUser;
import com.sebu.backend.user.repository.AppUserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "app.auth.retention.batch-size=1")
@Transactional
class RefreshTokenCleanupIntegrationTest {
    @Autowired RefreshTokenCleanupService cleanup;
    @Autowired RefreshTokenRepository tokens;
    @Autowired AppUserRepository users;
    @Autowired EntityManager entityManager;

    @Test
    void deletesOnlyAbsolutelyExpiredTokensInBoundedIdempotentBatchesAndKeepsUser() {
        AppUser user = users.save(AppUser.sejong("cleanup-user"));
        LocalDateTime now = LocalDateTime.of(2026, 10, 1, 0, 0);
        tokens.save(new RefreshToken(user, "a".repeat(64), now.minusDays(1), now.minusDays(31)));
        tokens.save(new RefreshToken(user, "b".repeat(64), now, now.minusDays(30)));
        RefreshToken previous = tokens.save(RefreshToken.start(user, "c".repeat(64), now.minusDays(13),
            Duration.ofDays(14), Duration.ofDays(30)));
        tokens.save(previous.rotate("d".repeat(64), now, Duration.ofDays(14)));
        // Idle-expired tokens are retained until the immutable deadline, keeping ancestry for logout.
        tokens.save(RefreshToken.start(user, "e".repeat(64), now.minusDays(15), Duration.ofDays(14), Duration.ofDays(30)));
        tokens.flush();
        assertThat(cleanup.deleteExpiredBatch(now)).isOne();
        assertThat(cleanup.deleteExpiredBatch(now)).isOne();
        assertThat(cleanup.deleteExpiredBatch(now)).isZero();
        entityManager.clear();
        assertThat(tokens.count()).isEqualTo(3);
        assertThat(users.findById(user.getId())).isPresent();
    }
}

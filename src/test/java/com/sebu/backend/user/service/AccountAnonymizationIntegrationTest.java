package com.sebu.backend.user.service;

import com.sebu.backend.account.service.AccountLifecycleService;
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
class AccountAnonymizationIntegrationTest {
    @Autowired AccountLifecycleService accountLifecycleService;
    @Autowired AppUserRepository users;
    @Autowired EntityManager entityManager;

    @Test
    void anonymizesAtTheThirtyDayBoundaryButNotBeforeIt() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 10, 0, 0);
        AppUser expired = users.save(AppUser.sejong("expired-account"));
        expired.withdraw(now.minusDays(30));
        AppUser stillRecoverable = users.save(AppUser.sejong("recoverable-account"));
        stillRecoverable.withdraw(now.minusDays(30).plusSeconds(1));
        users.flush();

        assertThat(accountLifecycleService.anonymizeExpiredBatch(now)).isOne();
        assertThat(accountLifecycleService.anonymizeExpiredBatch(now)).isZero();

        entityManager.clear();
        AppUser anonymized = users.findById(expired.getId()).orElseThrow();
        AppUser retained = users.findById(stillRecoverable.getId()).orElseThrow();
        assertThat(anonymized.getAnonymizedAt()).isEqualTo(now);
        assertThat(anonymized.getProvider()).isNull();
        assertThat(anonymized.getProviderUserId()).isNull();
        assertThat(retained.getAnonymizedAt()).isNull();
        assertThat(retained.getProviderUserId()).isEqualTo("recoverable-account");
    }
}

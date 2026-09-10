package com.sebu.backend.auth.scheduler;

import com.sebu.backend.auth.config.AccountLifecycleProperties;
import com.sebu.backend.auth.service.RecoveryTokenCleanupService;
import com.sebu.backend.user.service.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

@Component
@EnableConfigurationProperties(AccountLifecycleProperties.class)
@RequiredArgsConstructor
public class AccountLifecycleScheduler {
    private final AccountService accountService;
    private final RecoveryTokenCleanupService recoveryTokenCleanupService;
    private final AccountLifecycleProperties properties;
    private final Clock clock;

    @Scheduled(cron = "${app.auth.account.anonymization-cron:0 20 3 * * *}", zone = "Asia/Seoul")
    public void maintainWithdrawnAccounts() {
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)
            .truncatedTo(ChronoUnit.SECONDS);
        deleteExpiredRecoveryTokens(now);
        anonymizeExpiredAccounts(now);
    }

    private void deleteExpiredRecoveryTokens(LocalDateTime now) {
        for (int batch = 0; batch < properties.maxBatches(); batch++) {
            if (recoveryTokenCleanupService.deleteExpiredBatch(now) < properties.batchSize()) {
                break;
            }
        }
    }

    private void anonymizeExpiredAccounts(LocalDateTime now) {
        for (int batch = 0; batch < properties.maxBatches(); batch++) {
            if (accountService.anonymizeExpiredBatch(now) < properties.batchSize()) {
                break;
            }
        }
    }
}

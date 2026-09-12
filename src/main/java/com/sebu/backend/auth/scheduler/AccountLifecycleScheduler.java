package com.sebu.backend.auth.scheduler;

import com.sebu.backend.account.config.AccountLifecycleProperties;
import com.sebu.backend.account.service.AccountLifecycleService;
import com.sebu.backend.auth.service.RecoveryTokenCleanupService;
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
    private final AccountLifecycleService accountLifecycleService;
    private final RecoveryTokenCleanupService recoveryTokenCleanupService;
    private final AccountLifecycleProperties properties;
    private final Clock clock;

    @Scheduled(cron = "${app.auth.account.recovery-token-cleanup-cron:0 15 3 * * *}", zone = "Asia/Seoul")
    public void deleteExpiredRecoveryTokens() {
        LocalDateTime now = now();
        for (int batch = 0; batch < properties.maxBatches(); batch++) {
            if (recoveryTokenCleanupService.deleteExpiredBatch(now) < properties.batchSize()) {
                break;
            }
        }
    }

    @Scheduled(cron = "${app.auth.account.anonymization-cron:0 20 3 * * *}", zone = "Asia/Seoul")
    public void anonymizeExpiredAccounts() {
        LocalDateTime now = now();
        for (int batch = 0; batch < properties.maxBatches(); batch++) {
            if (accountLifecycleService.anonymizeExpiredBatch(now) < properties.batchSize()) {
                break;
            }
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)
            .truncatedTo(ChronoUnit.SECONDS);
    }
}

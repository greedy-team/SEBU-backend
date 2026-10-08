package com.sebu.backend.auth.scheduler;

import com.sebu.backend.account.config.AccountLifecycleProperties;
import com.sebu.backend.account.service.AccountLifecycleService;
import com.sebu.backend.auth.service.RecoveryTokenCleanupService;
import com.sebu.backend.global.logging.BatchLog;
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
        BatchLog log = BatchLog.start("RECOVERY_TOKEN_CLEANUP");
        try {
            LocalDateTime now = now();
            for (int batch = 0; batch < properties.maxBatches(); batch++) {
                int count = recoveryTokenCleanupService.deleteExpiredBatch(now);
                log.processed(count);
                if (count < properties.batchSize()) {
                    log.complete(false);
                    return;
                }
            }
            log.complete(true);
            } catch (RuntimeException exception) {
            log.failed(exception);
            throw exception;
        }
    }

    @Scheduled(cron = "${app.auth.account.anonymization-cron:0 20 3 * * *}", zone = "Asia/Seoul")
    public void anonymizeExpiredAccounts() {
        BatchLog log = BatchLog.start("ACCOUNT_ANONYMIZATION");
        try {
            LocalDateTime now = now();
            for (int batch = 0; batch < properties.maxBatches(); batch++) {
                int count = accountLifecycleService.anonymizeExpiredBatch(now);
                log.processed(count);
                if (count < properties.batchSize()) {
                    log.complete(false);
                    return;
                }
            }
            log.complete(true);
            } catch (RuntimeException exception) {
            log.failed(exception);
            throw exception;
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)
            .truncatedTo(ChronoUnit.SECONDS);
    }
}

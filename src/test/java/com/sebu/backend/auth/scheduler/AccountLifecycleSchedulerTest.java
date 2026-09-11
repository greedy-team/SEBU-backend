package com.sebu.backend.auth.scheduler;

import com.sebu.backend.account.config.AccountLifecycleProperties;
import com.sebu.backend.account.service.AccountLifecycleService;
import com.sebu.backend.auth.service.RecoveryTokenCleanupService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountLifecycleSchedulerTest {
    private final AccountLifecycleService lifecycleService = mock(AccountLifecycleService.class);
    private final RecoveryTokenCleanupService tokenCleanupService = mock(RecoveryTokenCleanupService.class);
    private final AccountLifecycleProperties properties = new AccountLifecycleProperties(
        Duration.ofDays(30),
        Duration.ofHours(1),
        Duration.ofMinutes(5),
        500,
        100
    );
    private final AccountLifecycleScheduler scheduler = new AccountLifecycleScheduler(
        lifecycleService,
        tokenCleanupService,
        properties,
        Clock.fixed(Instant.parse("2026-10-01T03:00:00Z"), ZoneOffset.UTC)
    );

    @Test
    void recoveryTokenCleanupDoesNotRunAccountAnonymization() {
        when(tokenCleanupService.deleteExpiredBatch(any())).thenReturn(0);

        scheduler.deleteExpiredRecoveryTokens();

        verify(tokenCleanupService).deleteExpiredBatch(any());
        verify(lifecycleService, never()).anonymizeExpiredBatch(any());
    }

    @Test
    void accountAnonymizationDoesNotDependOnRecoveryTokenCleanup() {
        when(lifecycleService.anonymizeExpiredBatch(any())).thenReturn(0);

        scheduler.anonymizeExpiredAccounts();

        verify(lifecycleService).anonymizeExpiredBatch(any());
        verify(tokenCleanupService, never()).deleteExpiredBatch(any());
    }
}

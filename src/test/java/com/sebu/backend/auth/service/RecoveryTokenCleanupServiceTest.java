package com.sebu.backend.auth.service;

import com.sebu.backend.account.config.AccountLifecycleProperties;
import com.sebu.backend.auth.repository.AccountRecoveryTokenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecoveryTokenCleanupServiceTest {
    @Mock AccountRecoveryTokenRepository repository;

    @Test
    void reportsFetchedBatchSizeEvenWhenAnotherTransactionAlreadyDeletedSomeRows() {
        AccountLifecycleProperties properties = new AccountLifecycleProperties(
            Duration.ofDays(30),
            Duration.ofHours(1),
            Duration.ofMinutes(5),
            2,
            100
        );
        RecoveryTokenCleanupService service = new RecoveryTokenCleanupService(repository, properties);
        LocalDateTime now = LocalDateTime.of(2026, 10, 1, 0, 0);
        List<Long> fetchedIds = List.of(10L, 11L);
        when(repository.findExpiredIds(eq(now), any(Pageable.class))).thenReturn(fetchedIds);
        when(repository.deleteExpiredIds(fetchedIds, now)).thenReturn(1);

        assertThat(service.deleteExpiredBatch(now)).isEqualTo(2);
        verify(repository).deleteExpiredIds(fetchedIds, now);
    }
}

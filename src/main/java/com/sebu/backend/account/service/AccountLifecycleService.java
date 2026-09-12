package com.sebu.backend.account.service;

import com.sebu.backend.account.config.AccountLifecycleProperties;
import com.sebu.backend.account.port.ActivityCleanupPort;
import com.sebu.backend.account.port.CredentialCleanupPort;
import com.sebu.backend.user.domain.AppUser;
import com.sebu.backend.user.exception.UserNotFoundException;
import com.sebu.backend.user.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class AccountLifecycleService {
    private final AppUserRepository appUserRepository;
    private final CredentialCleanupPort credentialCleanup;
    private final ActivityCleanupPort activityCleanup;
    private final AccountRecoveryPolicy recoveryPolicy;
    private final AccountLifecycleProperties lifecycleProperties;
    private final Clock clock;

    @Transactional
    public void withdraw(Long userId) {
        AppUser user = appUserRepository.findByIdForUpdate(userId)
            .filter(candidate -> !candidate.isDeleted())
            .orElseThrow(UserNotFoundException::new);

        user.withdraw(now());
        credentialCleanup.deleteAllByUserId(userId);
    }

    @Transactional
    public boolean anonymizeExpired(Long userId, LocalDateTime now) {
        AppUser user = appUserRepository.findByIdForUpdate(userId).orElse(null);
        if (user == null || !isReadyForAnonymization(user, now)) {
            return false;
        }
        eraseRecoverableData(user.getId());
        user.anonymize(now);
        appUserRepository.flush();
        return true;
    }

    @Transactional
    public int anonymizeExpiredBatch(LocalDateTime now) {
        var ids = appUserRepository.findExpiredWithdrawalIds(
            recoveryPolicy.expiredWithdrawalThreshold(now),
            PageRequest.of(0, lifecycleProperties.batchSize())
        );
        for (Long userId : ids) {
            AppUser user = appUserRepository.findByIdForUpdate(userId).orElse(null);
            if (user != null && isReadyForAnonymization(user, now)) {
                eraseRecoverableData(userId);
                user.anonymize(now);
            }
        }
        appUserRepository.flush();
        return ids.size();
    }

    private boolean isReadyForAnonymization(AppUser user, LocalDateTime now) {
        return user.isDeleted()
            && !user.isAnonymized()
            && recoveryPolicy.phase(user.getDeletedAt(), now) == AccountRecoveryPolicy.RecoveryPhase.EXPIRED;
    }

    private void eraseRecoverableData(Long userId) {
        credentialCleanup.deleteAllByUserId(userId);
        activityCleanup.deleteAllByUserId(userId);
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS);
    }
}

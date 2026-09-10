package com.sebu.backend.user.service;

import com.sebu.backend.auth.config.AccountLifecycleProperties;
import com.sebu.backend.auth.repository.AccountRecoveryTokenRepository;
import com.sebu.backend.auth.repository.RefreshTokenRepository;
import com.sebu.backend.auth.service.AccountRecoveryPolicy;
import com.sebu.backend.bookmark.repository.BookmarkRepository;
import com.sebu.backend.community.bookmark.repository.CommunityPostBookmarkRepository;
import com.sebu.backend.community.like.repository.CommunityPostLikeRepository;
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
public class AccountService {

    private final AppUserRepository appUserRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AccountRecoveryTokenRepository recoveryTokenRepository;
    private final BookmarkRepository bookmarkRepository;
    private final CommunityPostBookmarkRepository postBookmarkRepository;
    private final CommunityPostLikeRepository postLikeRepository;
    private final AccountRecoveryPolicy recoveryPolicy;
    private final AccountLifecycleProperties lifecycleProperties;
    private final Clock clock;

    @Transactional
    public void withdraw(Long userId) {
        AppUser user = appUserRepository.findByIdForUpdate(userId)
                .filter(candidate -> !candidate.isDeleted())
                .orElseThrow(UserNotFoundException::new);

        user.withdraw(now());
        refreshTokenRepository.deleteAllByUserId(userId);
        recoveryTokenRepository.deleteAllByUserId(userId);
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
        // The scheduler uses the fetched size to decide whether another page may remain.
        // Returning the changed count could stop early when a candidate was recovered concurrently.
        return ids.size();
    }

    private boolean isReadyForAnonymization(AppUser user, LocalDateTime now) {
        return user.isDeleted()
            && !user.isAnonymized()
            && recoveryPolicy.phase(user.getDeletedAt(), now) == AccountRecoveryPolicy.RecoveryPhase.EXPIRED;
    }

    private void eraseRecoverableData(Long userId) {
        refreshTokenRepository.deleteAllByUserId(userId);
        recoveryTokenRepository.deleteAllByUserId(userId);
        bookmarkRepository.deleteAllByUserId(userId);
        postBookmarkRepository.deleteAllByUserId(userId);
        postLikeRepository.deleteAllByUserId(userId);
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS);
    }
}

package com.sebu.backend.auth.service;

import com.sebu.backend.account.service.AccountRecoveryPolicy;
import com.sebu.backend.auth.domain.AccountRecoveryToken;
import com.sebu.backend.auth.exception.RecoveryTokenInvalidException;
import com.sebu.backend.auth.repository.AccountRecoveryTokenRepository;
import com.sebu.backend.auth.token.RecoveryTokenGenerator;
import com.sebu.backend.user.domain.AppUser;
import com.sebu.backend.user.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class AccountRecoveryService {
    private final AppUserRepository appUserRepository;
    private final AccountRecoveryTokenRepository recoveryTokenRepository;
    private final RecoveryTokenGenerator recoveryTokenGenerator;
    private final AccountRecoveryPolicy recoveryPolicy;
    private final AuthSessionIssuer sessionIssuer;
    private final Clock clock;

    @Transactional
    public AuthSessionService.RecoveryChallenge issueChallenge(AppUser user, LocalDateTime now) {
        recoveryTokenRepository.deleteAllByUserId(user.getId());
        var material = recoveryTokenGenerator.generate();
        LocalDateTime expiresAt = recoveryPolicy.recoveryTokenExpiresAt(user.getDeletedAt(), now);
        recoveryTokenRepository.save(new AccountRecoveryToken(user, material.tokenHash(), expiresAt, now));
        return new AuthSessionService.RecoveryChallenge(
            material.rawToken(),
            Duration.between(now, expiresAt).toSeconds(),
            recoveryPolicy.recoverableUntil(user.getDeletedAt()).toInstant(ZoneOffset.UTC)
        );
    }

    @Transactional
    public AuthSessionService.LoginSession recover(String rawRecoveryToken) {
        if (!hasValidTokenShape(rawRecoveryToken)) {
            throw new RecoveryTokenInvalidException();
        }
        String tokenHash = recoveryTokenGenerator.hash(rawRecoveryToken);
        Long userId = recoveryTokenRepository.findUserIdByTokenHash(tokenHash)
            .orElseThrow(RecoveryTokenInvalidException::new);
        AppUser user = appUserRepository.findByIdForUpdate(userId)
            .filter(AppUser::isDeleted)
            .filter(candidate -> !candidate.isAnonymized())
            .orElseThrow(RecoveryTokenInvalidException::new);
        AccountRecoveryToken token = recoveryTokenRepository.findByTokenHashForUpdate(tokenHash)
            .orElseThrow(RecoveryTokenInvalidException::new);
        LocalDateTime now = now();
        if (!token.isUsableAt(now)
            || recoveryPolicy.phase(user.getDeletedAt(), now) != AccountRecoveryPolicy.RecoveryPhase.RECOVERABLE) {
            throw new RecoveryTokenInvalidException();
        }
        user.recover();
        recoveryTokenRepository.deleteAllByUserId(userId);
        return sessionIssuer.issue(user, false, now);
    }

    private boolean hasValidTokenShape(String token) {
        return token != null && token.matches("[A-Za-z0-9_-]{43}");
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS);
    }
}

package com.sebu.backend.auth.service;

import com.sebu.backend.account.service.AccountLifecycleService;
import com.sebu.backend.account.service.AccountRecoveryPolicy;
import com.sebu.backend.auth.config.TokenProperties;
import com.sebu.backend.auth.domain.RefreshToken;
import com.sebu.backend.auth.exception.AuthSessionExpiredException;
import com.sebu.backend.auth.exception.RefreshTokenInvalidException;
import com.sebu.backend.auth.port.SejongUserProfile;
import com.sebu.backend.auth.repository.RefreshTokenRepository;
import com.sebu.backend.auth.token.JwtAccessTokenService;
import com.sebu.backend.auth.token.RefreshTokenGenerator;
import com.sebu.backend.department.domain.Department;
import com.sebu.backend.user.domain.AppUser;
import com.sebu.backend.user.domain.AuthProvider;
import com.sebu.backend.user.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthSessionService {
    private final AppUserRepository appUserRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenGenerator refreshTokenGenerator;
    private final JwtAccessTokenService accessTokenService;
    private final TokenProperties properties;
    private final SejongDepartmentResolver departmentResolver;
    private final AccountRecoveryPolicy recoveryPolicy;
    private final AccountLifecycleService accountLifecycleService;
    private final AccountRecoveryService accountRecoveryService;
    private final AuthSessionIssuer sessionIssuer;
    private final Clock clock;

    @Transactional
    public LoginOutcome login(SejongUserProfile profile) {
        return startOutcome(profile, true);
    }

    private LoginOutcome startOutcome(SejongUserProfile profile, boolean createWhenMissing) {
        AppUser user = findUserForUpdate(profile.studentId()).orElse(null);
        LocalDateTime now = now();
        if (user == null) {
            return createWhenMissing ? createUserSession(profile, now) : null;
        }
        if (!user.isDeleted()) {
            syncSchoolProfile(user, profile, now);
            return sessionIssuer.issue(user, false, now);
        }

        return switch (recoveryPolicy.phase(user.getDeletedAt(), now)) {
            case COOLDOWN -> RecoveryCooldown.INSTANCE;
            case RECOVERABLE -> accountRecoveryService.issueChallenge(user, now);
            case EXPIRED -> {
                accountLifecycleService.anonymizeExpired(user.getId(), now);
                yield createUserSession(profile, now);
            }
        };
    }

    private LoginSession createUserSession(SejongUserProfile profile, LocalDateTime now) {
        Department department = departmentResolver.resolve(profile.departmentName());
        AppUser user = appUserRepository.save(AppUser.sejong(
            profile.studentId(),
            profile.name(),
            profile.departmentName(),
            department,
            now
        ));
        return sessionIssuer.issue(user, true, now);
    }

    private void syncSchoolProfile(AppUser user, SejongUserProfile profile, LocalDateTime now) {
        Department department = departmentResolver.resolve(profile.departmentName());
        user.applySejongProfile(
            profile.name(),
            profile.departmentName(),
            department,
            now
        );
    }

    @Transactional
    public Optional<LoginOutcome> loginExisting(SejongUserProfile profile) {
        return Optional.ofNullable(startOutcome(profile, false));
    }

    @Transactional
    public RefreshSession refresh(String rawRefreshToken) {
        if (!hasValidTokenShape(rawRefreshToken)) {
            throw new RefreshTokenInvalidException(RefreshTokenInvalidException.Reason.MALFORMED);
        }
        String tokenHash = refreshTokenGenerator.hash(rawRefreshToken);
        Long userId = refreshTokenRepository.findUserIdByTokenHash(tokenHash)
            .orElseThrow(() -> new RefreshTokenInvalidException(RefreshTokenInvalidException.Reason.NOT_FOUND));
        AppUser user = appUserRepository.findByIdForUpdate(userId)
            .filter(candidate -> !candidate.isDeleted())
            .orElseThrow(() -> new RefreshTokenInvalidException(RefreshTokenInvalidException.Reason.USER_UNAVAILABLE));
        RefreshToken currentToken = refreshTokenRepository.findByTokenHashForUpdate(tokenHash)
            .orElseThrow(() -> new RefreshTokenInvalidException(RefreshTokenInvalidException.Reason.NOT_FOUND));
        LocalDateTime now = now();
        if (!currentToken.isUsableAt(now)) {
            throw new RefreshTokenInvalidException(currentToken.getRevokedAt() != null
                ? RefreshTokenInvalidException.Reason.REVOKED
                : !now.isBefore(currentToken.getAbsoluteExpiresAt())
                    ? RefreshTokenInvalidException.Reason.SESSION_EXPIRED : RefreshTokenInvalidException.Reason.EXPIRED);
        }

        var material = refreshTokenGenerator.generate();
        RefreshToken next = currentToken.rotate(material.tokenHash(), now, properties.refreshTokenExpiration());
        refreshTokenRepository.save(next);
        JwtAccessTokenService.IssuedAccessToken access;
        try {
            access = accessTokenService.issueUntil(
                user.getId(),
                user.getAuthVersion(),
                next.getAbsoluteExpiresAt().toInstant(ZoneOffset.UTC)
            );
        } catch (AuthSessionExpiredException exception) {
            // Expiry can pass after the locked Refresh check. Roll back rotation and keep the 401 contract.
            throw new RefreshTokenInvalidException(RefreshTokenInvalidException.Reason.SESSION_EXPIRED);
        }
        return new RefreshSession(
            access.value(), access.expiresIn(), material.rawToken(),
            Duration.between(now, next.getExpiresAt()).toSeconds()
        );
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (!hasValidTokenShape(rawRefreshToken)) {
            return;
        }
        String tokenHash = refreshTokenGenerator.hash(rawRefreshToken);
        refreshTokenRepository.findUserIdByTokenHash(tokenHash)
            .flatMap(appUserRepository::findByIdForUpdate)
            .ifPresent(user -> refreshTokenRepository.findByTokenHashForUpdate(tokenHash)
                .ifPresent(token -> {
                    LocalDateTime now = now();
                    refreshTokenRepository.findUnrevokedSessionForUpdate(user.getId(), token.getSessionId())
                        .forEach(member -> member.revoke(now));
                }));
    }

    private Optional<AppUser> findUserForUpdate(String studentId) {
        return appUserRepository.findIdByProviderIdentity(AuthProvider.SEJONG, studentId)
            .flatMap(appUserRepository::findByIdForUpdate);
    }

    private boolean hasValidTokenShape(String token) {
        return token != null && token.matches("[A-Za-z0-9_-]{43}");
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS);
    }

    public sealed interface LoginOutcome permits LoginSession, RecoveryChallenge, RecoveryCooldown {
    }

    public static final class LoginSession implements LoginOutcome {
        private final String accessToken;
        private final long expiresIn;
        private final String refreshToken;
        private final long refreshExpiresIn;
        private final Long userId;
        private final boolean newUser;
        private final boolean profileCompleted;

        LoginSession(
            String accessToken,
            long expiresIn,
            String refreshToken,
            long refreshExpiresIn,
            Long userId,
            boolean newUser,
            boolean profileCompleted
        ) {
            this.accessToken = accessToken;
            this.expiresIn = expiresIn;
            this.refreshToken = refreshToken;
            this.refreshExpiresIn = refreshExpiresIn;
            this.userId = userId;
            this.newUser = newUser;
            this.profileCompleted = profileCompleted;
        }

        public String accessToken() {
            return accessToken;
        }

        public long expiresIn() {
            return expiresIn;
        }

        public String refreshToken() {
            return refreshToken;
        }

        public long refreshExpiresIn() {
            return refreshExpiresIn;
        }

        public Long userId() {
            return userId;
        }

        public boolean isNewUser() {
            return newUser;
        }

        public boolean isProfileCompleted() {
            return profileCompleted;
        }

        @Override
        public String toString() {
            return "LoginSession[userId=" + userId + ", newUser=" + newUser
                + ", profileCompleted=" + profileCompleted + ", tokens=REDACTED]";
        }
    }

    public static final class RecoveryChallenge implements LoginOutcome {
        private final String recoveryToken;
        private final long recoveryExpiresIn;
        private final java.time.Instant recoverableUntil;

        RecoveryChallenge(String recoveryToken, long recoveryExpiresIn, java.time.Instant recoverableUntil) {
            this.recoveryToken = recoveryToken;
            this.recoveryExpiresIn = recoveryExpiresIn;
            this.recoverableUntil = recoverableUntil;
        }

        public String recoveryToken() {
            return recoveryToken;
        }

        public long recoveryExpiresIn() {
            return recoveryExpiresIn;
        }

        public java.time.Instant recoverableUntil() {
            return recoverableUntil;
        }

        @Override
        public String toString() {
            return "RecoveryChallenge[recoveryExpiresIn=" + recoveryExpiresIn
                + ", recoverableUntil=" + recoverableUntil + ", token=REDACTED]";
        }
    }

    public enum RecoveryCooldown implements LoginOutcome {
        INSTANCE
    }

    public static final class RefreshSession {
        private final String accessToken;
        private final long expiresIn;
        private final String refreshToken;
        private final long refreshExpiresIn;

        private RefreshSession(String accessToken, long expiresIn, String refreshToken, long refreshExpiresIn) {
            this.accessToken = accessToken;
            this.expiresIn = expiresIn;
            this.refreshToken = refreshToken;
            this.refreshExpiresIn = refreshExpiresIn;
        }

        public String accessToken() {
            return accessToken;
        }

        public long expiresIn() {
            return expiresIn;
        }

        public String refreshToken() {
            return refreshToken;
        }

        public long refreshExpiresIn() {
            return refreshExpiresIn;
        }

        @Override
        public String toString() {
            return "RefreshSession[tokens=REDACTED]";
        }
    }
}

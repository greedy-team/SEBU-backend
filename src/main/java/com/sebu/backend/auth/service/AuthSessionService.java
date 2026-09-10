package com.sebu.backend.auth.service;

import com.sebu.backend.auth.config.TokenProperties;
import com.sebu.backend.auth.domain.AccountRecoveryToken;
import com.sebu.backend.auth.domain.RefreshToken;
import com.sebu.backend.auth.exception.AccessTokenInvalidException;
import com.sebu.backend.auth.exception.AuthSessionExpiredException;
import com.sebu.backend.auth.exception.RecoveryTokenInvalidException;
import com.sebu.backend.auth.exception.RefreshTokenInvalidException;
import com.sebu.backend.auth.port.SejongUserProfile;
import com.sebu.backend.auth.repository.AccountRecoveryTokenRepository;
import com.sebu.backend.auth.repository.RefreshTokenRepository;
import com.sebu.backend.auth.token.JwtAccessTokenService;
import com.sebu.backend.auth.token.RecoveryTokenGenerator;
import com.sebu.backend.auth.token.RefreshTokenGenerator;
import com.sebu.backend.department.domain.Department;
import com.sebu.backend.user.domain.AppUser;
import com.sebu.backend.user.domain.AuthProvider;
import com.sebu.backend.user.repository.AppUserRepository;
import com.sebu.backend.user.service.AccountService;
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
    private final AccountRecoveryTokenRepository recoveryTokenRepository;
    private final RecoveryTokenGenerator recoveryTokenGenerator;
    private final AccountRecoveryPolicy recoveryPolicy;
    private final AccountService accountService;
    private final Clock clock;

    @Transactional
    public LoginSession start(SejongUserProfile profile) {
        LoginOutcome outcome = startOutcome(profile, true);
        if (outcome instanceof LoginSession session) {
            return session;
        }
        throw new AccessTokenInvalidException();
    }

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
            return issueLoginSession(user, false, now);
        }

        return switch (recoveryPolicy.phase(user.getDeletedAt(), now)) {
            case COOLDOWN -> RecoveryCooldown.INSTANCE;
            case RECOVERABLE -> issueRecoveryChallenge(user, now);
            case EXPIRED -> {
                accountService.anonymizeExpired(user.getId(), now);
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
        return issueLoginSession(user, true, now);
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

    private RecoveryChallenge issueRecoveryChallenge(AppUser user, LocalDateTime now) {
        recoveryTokenRepository.deleteAllByUserId(user.getId());
        var material = recoveryTokenGenerator.generate();
        LocalDateTime expiresAt = recoveryPolicy.recoveryTokenExpiresAt(user.getDeletedAt(), now);
        recoveryTokenRepository.save(new AccountRecoveryToken(user, material.tokenHash(), expiresAt, now));
        return new RecoveryChallenge(
            material.rawToken(),
            Duration.between(now, expiresAt).toSeconds(),
            recoveryPolicy.recoverableUntil(user.getDeletedAt()).toInstant(ZoneOffset.UTC)
        );
    }

    @Transactional
    public LoginSession recover(String rawRecoveryToken) {
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
        return issueLoginSession(user, false, now);
    }

    @Transactional
    public Optional<LoginSession> startExisting(SejongUserProfile profile) {
        LoginOutcome outcome = startOutcome(profile, false);
        return outcome instanceof LoginSession session ? Optional.of(session) : Optional.empty();
    }

    @Transactional
    public Optional<LoginOutcome> loginExisting(SejongUserProfile profile) {
        return Optional.ofNullable(startOutcome(profile, false));
    }

    @Transactional
    public RefreshSession refresh(String rawRefreshToken) {
        if (!hasValidTokenShape(rawRefreshToken)) {
            throw new RefreshTokenInvalidException();
        }
        String tokenHash = refreshTokenGenerator.hash(rawRefreshToken);
        Long userId = refreshTokenRepository.findUserIdByTokenHash(tokenHash)
            .orElseThrow(RefreshTokenInvalidException::new);
        AppUser user = appUserRepository.findByIdForUpdate(userId)
            .filter(candidate -> !candidate.isDeleted())
            .orElseThrow(RefreshTokenInvalidException::new);
        RefreshToken currentToken = refreshTokenRepository.findByTokenHashForUpdate(tokenHash)
            .orElseThrow(RefreshTokenInvalidException::new);
        LocalDateTime now = now();
        if (!currentToken.isUsableAt(now)) {
            throw new RefreshTokenInvalidException();
        }

        var material = refreshTokenGenerator.generate();
        RefreshToken next = currentToken.rotate(material.tokenHash(), now, properties.refreshTokenExpiration());
        refreshTokenRepository.save(next);
        JwtAccessTokenService.IssuedAccessToken access;
        try {
            access = accessTokenService.issueUntil(user.getId(), next.getAbsoluteExpiresAt().toInstant(ZoneOffset.UTC));
        } catch (AuthSessionExpiredException exception) {
            // Expiry can pass after the locked Refresh check. Roll back rotation and keep the 401 contract.
            throw new RefreshTokenInvalidException();
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

    private LoginSession issueLoginSession(AppUser user, boolean newUser, LocalDateTime issuedAt) {
        var material = refreshTokenGenerator.generate();
        RefreshToken refreshToken = refreshTokenRepository.save(RefreshToken.start(user, material.tokenHash(),
            issuedAt, properties.refreshTokenExpiration(), properties.absoluteSessionExpiration()));
        var access = accessTokenService.issueUntil(user.getId(), refreshToken.getAbsoluteExpiresAt().toInstant(ZoneOffset.UTC));
        return new LoginSession(
            access.value(),
            access.expiresIn(),
            material.rawToken(),
            Duration.between(issuedAt, refreshToken.getExpiresAt()).toSeconds(),
            user.getId(),
            newUser,
            user.isProfileCompleted()
        );
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

        private LoginSession(
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

        private RecoveryChallenge(String recoveryToken, long recoveryExpiresIn, java.time.Instant recoverableUntil) {
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

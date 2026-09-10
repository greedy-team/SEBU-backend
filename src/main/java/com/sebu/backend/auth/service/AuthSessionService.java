package com.sebu.backend.auth.service;

import com.sebu.backend.auth.config.TokenProperties;
import com.sebu.backend.auth.domain.RefreshToken;
import com.sebu.backend.auth.exception.RefreshTokenInvalidException;
import com.sebu.backend.auth.exception.AuthSessionExpiredException;
import com.sebu.backend.auth.exception.AccessTokenInvalidException;
import com.sebu.backend.auth.port.SejongUserProfile;
import com.sebu.backend.auth.repository.RefreshTokenRepository;
import com.sebu.backend.auth.token.JwtAccessTokenService;
import com.sebu.backend.auth.token.RefreshTokenGenerator;
import com.sebu.backend.department.domain.Department;
import com.sebu.backend.user.domain.AppUser;
import com.sebu.backend.user.domain.AuthProvider;
import com.sebu.backend.user.repository.AppUserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Service
public class AuthSessionService {
    private final AppUserRepository appUserRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenGenerator refreshTokenGenerator;
    private final JwtAccessTokenService accessTokenService;
    private final TokenProperties properties;
    private final SejongDepartmentResolver departmentResolver;
    private final Clock clock;

    @Autowired
    public AuthSessionService(
        AppUserRepository appUserRepository,
        RefreshTokenRepository refreshTokenRepository,
        RefreshTokenGenerator refreshTokenGenerator,
        JwtAccessTokenService accessTokenService,
        TokenProperties properties,
        SejongDepartmentResolver departmentResolver
    ) {
        this(
            appUserRepository,
            refreshTokenRepository,
            refreshTokenGenerator,
            accessTokenService,
            properties,
            departmentResolver,
            Clock.systemUTC()
        );
    }

    AuthSessionService(
        AppUserRepository appUserRepository,
        RefreshTokenRepository refreshTokenRepository,
        RefreshTokenGenerator refreshTokenGenerator,
        JwtAccessTokenService accessTokenService,
        TokenProperties properties,
        SejongDepartmentResolver departmentResolver,
        Clock clock
    ) {
        this.appUserRepository = appUserRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenGenerator = refreshTokenGenerator;
        this.accessTokenService = accessTokenService;
        this.properties = properties;
        this.departmentResolver = departmentResolver;
        this.clock = clock;
    }

    @Transactional
    public LoginSession start(SejongUserProfile profile) {
        AppUser user = findUserForUpdate(profile.studentId()).orElse(null);
        boolean newUser = user == null;
        LocalDateTime now = now();
        Department department = departmentResolver.resolve(profile.departmentName());
        if (newUser) {
            user = appUserRepository.save(AppUser.sejong(
                profile.studentId(),
                profile.name(),
                profile.departmentName(),
                department,
                now
            ));
        } else {
            requireActiveUser(user);
            user.applySejongProfile(
                profile.name(),
                profile.departmentName(),
                department,
                now
            );
        }
        return issueLoginSession(user, newUser, now);
    }

    @Transactional
    public Optional<LoginSession> startExisting(SejongUserProfile profile) {
        LocalDateTime now = now();
        Department department = departmentResolver.resolve(profile.departmentName());
        return findUserForUpdate(profile.studentId())
            .map(user -> {
                requireActiveUser(user);
                user.applySejongProfile(
                    profile.name(),
                    profile.departmentName(),
                    department,
                    now
                );
                return issueLoginSession(user, false, now);
            });
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

    @Transactional
    public void revokeAllByUserId(Long userId) {
        LocalDateTime now = now();

        appUserRepository.findByIdForUpdate(userId).ifPresent(user ->
            refreshTokenRepository.findAllUnrevokedForUpdate(userId).forEach(token -> token.revoke(now)));
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

    private void requireActiveUser(AppUser user) {
        if (user.isDeleted()) {
            throw new AccessTokenInvalidException();
        }
    }

    private boolean hasValidTokenShape(String token) {
        return token != null && token.matches("[A-Za-z0-9_-]{43}");
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS);
    }

    public static final class LoginSession {
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

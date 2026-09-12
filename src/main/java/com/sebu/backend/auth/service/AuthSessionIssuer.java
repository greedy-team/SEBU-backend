package com.sebu.backend.auth.service;

import com.sebu.backend.auth.config.TokenProperties;
import com.sebu.backend.auth.domain.RefreshToken;
import com.sebu.backend.auth.repository.RefreshTokenRepository;
import com.sebu.backend.auth.token.JwtAccessTokenService;
import com.sebu.backend.auth.token.RefreshTokenGenerator;
import com.sebu.backend.user.domain.AppUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Component
@RequiredArgsConstructor
public class AuthSessionIssuer {
    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenGenerator refreshTokenGenerator;
    private final JwtAccessTokenService accessTokenService;
    private final TokenProperties properties;

    public AuthSessionService.LoginSession issue(AppUser user, boolean newUser, LocalDateTime issuedAt) {
        var material = refreshTokenGenerator.generate();
        RefreshToken refreshToken = refreshTokenRepository.save(RefreshToken.start(
            user,
            material.tokenHash(),
            issuedAt,
            properties.refreshTokenExpiration(),
            properties.absoluteSessionExpiration()
        ));
        var access = accessTokenService.issueUntil(
            user.getId(),
            user.getAuthVersion(),
            refreshToken.getAbsoluteExpiresAt().toInstant(ZoneOffset.UTC)
        );
        return new AuthSessionService.LoginSession(
            access.value(),
            access.expiresIn(),
            material.rawToken(),
            Duration.between(issuedAt, refreshToken.getExpiresAt()).toSeconds(),
            user.getId(),
            newUser,
            user.isProfileCompleted()
        );
    }
}

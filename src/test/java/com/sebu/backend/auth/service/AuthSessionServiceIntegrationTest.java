package com.sebu.backend.auth.service;

import com.sebu.backend.auth.domain.RefreshToken;
import com.sebu.backend.auth.exception.RefreshTokenInvalidException;
import com.sebu.backend.auth.repository.RefreshTokenRepository;
import com.sebu.backend.auth.token.RefreshTokenGenerator;
import com.sebu.backend.auth.port.SejongUserProfile;
import com.sebu.backend.user.domain.AuthProvider;
import com.sebu.backend.user.domain.AppUser;
import com.sebu.backend.user.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class AuthSessionServiceIntegrationTest {
    @Autowired
    AuthSessionService authSessionService;

    @Autowired
    AppUserRepository appUserRepository;

    @Autowired
    RefreshTokenRepository refreshTokenRepository;

    @Autowired
    RefreshTokenGenerator refreshTokenGenerator;

    @Test
    void createsUserOnceAndKeepsIndependentRefreshTokensForMultipleLogins() {
        AuthSessionService.LoginSession first = (AuthSessionService.LoginSession) authSessionService.login(profile("21012345"));
        AuthSessionService.LoginSession second = (AuthSessionService.LoginSession) authSessionService.login(profile("21012345"));

        assertThat(first.isNewUser()).isTrue();
        assertThat(second.isNewUser()).isFalse();
        assertThat(first.userId()).isEqualTo(second.userId());
        assertThat(first.refreshToken()).isNotEqualTo(second.refreshToken());
        assertThat(first.expiresIn()).isEqualTo(1800);
        assertThat(first.toString()).doesNotContain(first.accessToken(), first.refreshToken());
        assertThat(appUserRepository.findByProviderAndProviderUserId(AuthProvider.SEJONG, "21012345"))
            .isPresent();
        assertThat(refreshTokenRepository.countByUser_Id(first.userId())).isEqualTo(2);
        assertThat(refreshTokenRepository.findByTokenHash(refreshTokenGenerator.hash(first.refreshToken())))
            .isPresent();
        assertThat(refreshTokenRepository.findByTokenHash(first.refreshToken())).isEmpty();
    }

    @Test
    void rotatesRefreshTokenAndRejectsReusingThePreviousToken() {
        AuthSessionService.LoginSession login = (AuthSessionService.LoginSession) authSessionService.login(profile("rotation-user"));
        String previousHash = refreshTokenGenerator.hash(login.refreshToken());

        AuthSessionService.RefreshSession refreshed = authSessionService.refresh(login.refreshToken());

        RefreshToken previous = refreshTokenRepository.findByTokenHash(previousHash).orElseThrow();
        assertThat(previous.getRevokedAt()).isNotNull();
        assertThat(refreshed.refreshToken()).isNotEqualTo(login.refreshToken());
        assertThat(refreshTokenRepository.findByTokenHash(
            refreshTokenGenerator.hash(refreshed.refreshToken())
        )).isPresent();
        assertThatThrownBy(() -> authSessionService.refresh(login.refreshToken()))
            .isInstanceOf(RefreshTokenInvalidException.class);
    }

    @Test
    void rejectsExpiredAndRevokedRefreshTokens() {
        AppUser user = appUserRepository.save(AppUser.sejong("invalid-token-user"));
        LocalDateTime now = LocalDateTime.now();
        var expiredMaterial = refreshTokenGenerator.generate();
        refreshTokenRepository.saveAndFlush(new RefreshToken(
            user,
            expiredMaterial.tokenHash(),
            now.minusDays(1),
            now.minusDays(15)
        ));

        var revokedMaterial = refreshTokenGenerator.generate();
        RefreshToken revoked = refreshTokenRepository.saveAndFlush(new RefreshToken(
            user,
            revokedMaterial.tokenHash(),
            now.plusDays(14),
            now
        ));
        revoked.revoke(now.plusMinutes(1));
        refreshTokenRepository.flush();

        assertThatThrownBy(() -> authSessionService.refresh(expiredMaterial.rawToken()))
            .isInstanceOf(RefreshTokenInvalidException.class);
        assertThatThrownBy(() -> authSessionService.refresh(revokedMaterial.rawToken()))
            .isInstanceOf(RefreshTokenInvalidException.class);
    }

    private SejongUserProfile profile(String studentId) {
        return new SejongUserProfile(studentId, "홍길동", "컴퓨터공학과");
    }
}

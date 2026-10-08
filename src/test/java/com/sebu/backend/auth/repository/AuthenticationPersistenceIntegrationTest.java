package com.sebu.backend.auth.repository;

import com.sebu.backend.auth.domain.RefreshToken;
import com.sebu.backend.user.domain.AppUser;
import com.sebu.backend.user.domain.AuthProvider;
import com.sebu.backend.user.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class AuthenticationPersistenceIntegrationTest {
    @Autowired
    AppUserRepository appUserRepository;

    @Autowired
    RefreshTokenRepository refreshTokenRepository;

    @Test
    void preservesLegacyEmailUsersAndStoresSejongIdentity() {
        AppUser legacy = appUserRepository.saveAndFlush(new AppUser(" Legacy@Example.com "));
        AppUser sejong = appUserRepository.saveAndFlush(AppUser.sejong(" 21012345 "));

        assertThat(legacy.getEmail()).isEqualTo("legacy@example.com");
        assertThat(legacy.getProvider()).isNull();
        assertThat(sejong.getEmail()).isNull();
        assertThat(sejong.getProvider()).isEqualTo(AuthProvider.SEJONG);
        assertThat(sejong.getProviderUserId()).isEqualTo("21012345");
        assertThat(sejong.isProfileCompleted()).isFalse();
        assertThat(appUserRepository.findByProviderAndProviderUserId(AuthProvider.SEJONG, "21012345"))
            .contains(sejong);
    }

    @Test
    void rejectsDuplicateProviderIdentity() {
        appUserRepository.saveAndFlush(AppUser.sejong("21012345"));

        assertThatThrownBy(() -> appUserRepository.saveAndFlush(AppUser.sejong("21012345")))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void storesOnlyUniqueRefreshTokenHashesAndSupportsRevocation() {
        AppUser user = appUserRepository.saveAndFlush(AppUser.sejong("21012345"));
        LocalDateTime issuedAt = LocalDateTime.of(2026, 8, 18, 12, 0);
        String tokenHash = "a".repeat(64);
        RefreshToken token = refreshTokenRepository.saveAndFlush(
            new RefreshToken(user, tokenHash, issuedAt.plusDays(14), issuedAt)
        );

        assertThat(refreshTokenRepository.findByTokenHash(tokenHash)).contains(token);
        assertThat(token.isUsableAt(issuedAt.plusDays(1))).isTrue();

        token.revoke(issuedAt.plusDays(1));
        refreshTokenRepository.flush();

        assertThat(token.isUsableAt(issuedAt.plusDays(1))).isFalse();
        assertThatThrownBy(() -> refreshTokenRepository.saveAndFlush(
            new RefreshToken(user, tokenHash, issuedAt.plusDays(14), issuedAt)
        )).isInstanceOf(DataIntegrityViolationException.class);
    }
}

package com.sebu.backend.mypage.service;

import com.sebu.backend.auth.domain.AccountRecoveryToken;
import com.sebu.backend.auth.domain.RefreshToken;
import com.sebu.backend.auth.exception.RefreshTokenInvalidException;
import com.sebu.backend.auth.repository.AccountRecoveryTokenRepository;
import com.sebu.backend.auth.repository.RefreshTokenRepository;
import com.sebu.backend.auth.port.SejongUserProfile;
import com.sebu.backend.auth.service.AuthSessionService;
import com.sebu.backend.user.domain.AppUser;
import com.sebu.backend.user.repository.AppUserRepository;
import com.sebu.backend.user.service.AccountService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

@SpringBootTest
@Transactional
class AccountServiceTest {

    @Autowired
    AccountService accountService;

    @Autowired
    AppUserRepository appUserRepository;

    @Autowired
    RefreshTokenRepository refreshTokenRepository;

    @Autowired
    AccountRecoveryTokenRepository recoveryTokenRepository;

    @Autowired
    AuthSessionService authSessionService;

    @Test
    void 회원_탈퇴시_사용자의_모든_refreshToken이_즉시_삭제된다() {
        // given
        AppUser user = appUserRepository.save(
                new AppUser("withdraw-token@example.com")
        );

        LocalDateTime now = LocalDateTime.now();

        String hash1 = "a".repeat(64);
        String hash2 = "b".repeat(64);

        refreshTokenRepository.save(
                new RefreshToken(
                        user,
                        hash1,
                        now.plusDays(7),
                        now
                )
        );

        recoveryTokenRepository.save(new AccountRecoveryToken(
                user,
                "c".repeat(64),
                now.plusMinutes(5),
                now
        ));

        refreshTokenRepository.save(
                new RefreshToken(
                        user,
                        hash2,
                        now.plusDays(7),
                        now
                )
        );

        // when
        accountService.withdraw(user.getId());

        // then
        AppUser withdrawnUser = appUserRepository.findById(user.getId())
                .orElseThrow();

        assertThat(withdrawnUser.getDeletedAt()).isNotNull();

        assertThat(refreshTokenRepository.countByUser_Id(user.getId())).isZero();
        assertThat(recoveryTokenRepository.countByUser_Id(user.getId())).isZero();
    }

    @Test
    void 회원_탈퇴후_기존_refreshToken으로_재발급할_수_없다() {
        // given
        AuthSessionService.LoginSession loginSession =
                authSessionService.start(new SejongUserProfile(
                        "29000001",
                        "탈퇴테스트",
                        "테스트학과"
                ));

        Long userId = loginSession.userId();
        String refreshToken = loginSession.refreshToken();

        // 실제로 로그인 세션이 발급됐는지 확인
        assertThat(refreshToken).isNotBlank();

        // when
        accountService.withdraw(userId);

        // then
        assertThatThrownBy(() ->
                authSessionService.refresh(refreshToken)
        )
                .isInstanceOf(RefreshTokenInvalidException.class);
    }
}

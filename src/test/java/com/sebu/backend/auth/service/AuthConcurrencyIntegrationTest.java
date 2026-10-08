package com.sebu.backend.auth.service;

import com.sebu.backend.auth.exception.AuthSessionExpiredException;
import com.sebu.backend.auth.exception.RecoveryTokenInvalidException;
import com.sebu.backend.auth.exception.RefreshTokenInvalidException;
import com.sebu.backend.auth.port.SejongAuthenticator;
import com.sebu.backend.auth.port.SejongUserProfile;
import com.sebu.backend.auth.repository.RefreshTokenRepository;
import com.sebu.backend.auth.token.JwtAccessTokenService;
import com.sebu.backend.auth.token.RefreshTokenGenerator;
import com.sebu.backend.user.repository.AppUserRepository;
import com.sebu.backend.account.service.AccountLifecycleService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.sebu.backend.support.CookieApiRequests.post;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AuthConcurrencyIntegrationTest {
    @Autowired
    AuthService authService;

    @Autowired
    AuthSessionService authSessionService;

    @MockitoSpyBean
    AppUserRepository appUserRepository;

    @Autowired AccountLifecycleService accountLifecycleService;
    @Autowired AccountRecoveryService accountRecoveryService;
    @Autowired MockMvc mockMvc;
    @Autowired TransactionTemplate transactionTemplate;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired RefreshTokenGenerator tokenGenerator;
    @MockitoSpyBean JwtAccessTokenService accessTokenService;

    @Autowired
    RefreshTokenRepository refreshTokenRepository;

    @MockitoBean
    SejongAuthenticator sejongAuthenticator;

    @BeforeEach
    void cleanDatabase() {
        refreshTokenRepository.deleteAll();
        appUserRepository.deleteAll();
    }

    @AfterEach
    void cleanDatabaseAfterTest() {
        cleanDatabase();
    }

    @Test
    void concurrentFirstLoginsReuseTheUserAfterUniqueConflict() throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        when(sejongAuthenticator.authenticate(anyString(), anyString())).thenAnswer(invocation -> {
            barrier.await(5, TimeUnit.SECONDS);
            return new SejongUserProfile("21000007", "홍길동", "컴퓨터공학과");
        });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<AuthSessionService.LoginSession> first = executor.submit(
                () -> (AuthSessionService.LoginSession) authService.loginWithSejong("21000007", "password")
            );
            Future<AuthSessionService.LoginSession> second = executor.submit(
                () -> (AuthSessionService.LoginSession) authService.loginWithSejong("21000007", "password")
            );

            List<AuthSessionService.LoginSession> sessions = List.of(
                first.get(10, TimeUnit.SECONDS),
                second.get(10, TimeUnit.SECONDS)
            );

            assertThat(appUserRepository.count()).isOne();
            assertThat(refreshTokenRepository.count()).isEqualTo(2);
            assertThat(sessions).extracting(AuthSessionService.LoginSession::userId)
                .containsOnly(sessions.getFirst().userId());
            assertThat(sessions).extracting(AuthSessionService.LoginSession::isNewUser)
                .containsExactlyInAnyOrder(true, false);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void onlyOneConcurrentRefreshCanRotateTheSameToken() throws Exception {
        AuthSessionService.LoginSession login = (AuthSessionService.LoginSession) authSessionService.login(new SejongUserProfile(
            "concurrent-refresh-user", "홍길동", "컴퓨터공학과"
        ));
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Object> first = executor.submit(() -> refreshAfterBarrier(barrier, login.refreshToken()));
            Future<Object> second = executor.submit(() -> refreshAfterBarrier(barrier, login.refreshToken()));
            List<Object> results = List.of(
                first.get(10, TimeUnit.SECONDS),
                second.get(10, TimeUnit.SECONDS)
            );

            assertThat(results).filteredOn(AuthSessionService.RefreshSession.class::isInstance).hasSize(1);
            assertThat(results).filteredOn(RefreshTokenInvalidException.class::isInstance).hasSize(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private Object refreshAfterBarrier(CyclicBarrier barrier, String refreshToken) throws Exception {
        barrier.await(5, TimeUnit.SECONDS);
        try {
            return authSessionService.refresh(refreshToken);
        } catch (RefreshTokenInvalidException exception) {
            return exception;
        }
    }

    @Test
    void concurrentLogoutWithOriginalTokenAlsoRevokesAnyRotatedSuccessor() throws Exception {
        var profile = new SejongUserProfile("logout-race-user", "홍길동", "컴퓨터공학과");
        var login = (AuthSessionService.LoginSession) authSessionService.login(profile);
        var otherLogin = (AuthSessionService.LoginSession) authSessionService.login(profile);
        var barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Object> refresh = executor.submit(() -> refreshAfterBarrier(barrier, login.refreshToken()));
            Future<?> logout = executor.submit(() -> {
                barrier.await(30, TimeUnit.SECONDS);
                authSessionService.logout(login.refreshToken());
                return null;
            });
            Object result = refresh.get(60, TimeUnit.SECONDS);
            logout.get(60, TimeUnit.SECONDS);
            if (result instanceof AuthSessionService.RefreshSession issued) {
                org.assertj.core.api.Assertions.assertThatThrownBy(() -> authSessionService.refresh(issued.refreshToken()))
                    .isInstanceOf(RefreshTokenInvalidException.class);
            }
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> authSessionService.refresh(login.refreshToken()))
                .isInstanceOf(RefreshTokenInvalidException.class);
            assertThat(authSessionService.refresh(otherLogin.refreshToken())).isNotNull();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentWithdrawalLeavesNoUsableRefreshToken() throws Exception {
        var login = (AuthSessionService.LoginSession) authSessionService.login(new SejongUserProfile("withdraw-race-user", "홍길동", "컴퓨터공학과"));
        var barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Object> refresh = executor.submit(() -> refreshAfterBarrier(barrier, login.refreshToken()));
            Future<?> withdrawal = executor.submit(() -> {
                barrier.await(30, TimeUnit.SECONDS);
                accountLifecycleService.withdraw(login.userId());
                return null;
            });
            Object result = refresh.get(60, TimeUnit.SECONDS);
            withdrawal.get(60, TimeUnit.SECONDS);
            assertThat(appUserRepository.findById(login.userId()).orElseThrow().isDeleted()).isTrue();
            assertThat(refreshTokenRepository.findAllByUser_Id(login.userId())).isEmpty();
            if (result instanceof AuthSessionService.RefreshSession issued) {
                org.assertj.core.api.Assertions.assertThatThrownBy(() -> authSessionService.refresh(issued.refreshToken()))
                    .isInstanceOf(RefreshTokenInvalidException.class);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void rejectingAnAlreadyUsedTokenDoesNotRevokeItsActiveSuccessor() {
        var login = (AuthSessionService.LoginSession) authSessionService.login(new SejongUserProfile("replay-user", "홍길동", "컴퓨터공학과"));
        var rotated = authSessionService.refresh(login.refreshToken());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> authSessionService.refresh(login.refreshToken()))
            .isInstanceOf(RefreshTokenInvalidException.class);
        assertThat(authSessionService.refresh(rotated.refreshToken())).isNotNull();
    }

    @Test
    void revocationQueriesExcludeHistoryAndKeepIndependentLogins() {
        var profile = new SejongUserProfile("revocation-query-user", "홍길동", "컴퓨터공학과");
        var login = (AuthSessionService.LoginSession) authSessionService.login(profile);
        String latestToken = login.refreshToken();
        for (int i = 0; i < 8; i++) {
            latestToken = authSessionService.refresh(latestToken).refreshToken();
        }
        var independent = (AuthSessionService.LoginSession) authSessionService.login(profile);
        var original = refreshTokenRepository.findByTokenHash(tokenGenerator.hash(login.refreshToken())).orElseThrow();
        String latestHash = tokenGenerator.hash(latestToken);
        transactionTemplate.executeWithoutResult(status -> {
            appUserRepository.findByIdForUpdate(login.userId()).orElseThrow();
            assertThat(refreshTokenRepository.findUnrevokedSessionForUpdate(login.userId(), original.getSessionId()))
                .extracting(token -> token.getTokenHash()).containsExactly(latestHash);
            assertThat(refreshTokenRepository.findAllUnrevokedForUpdate(login.userId())).hasSize(2);
        });

        authSessionService.logout(login.refreshToken());
        assertThat(refreshTokenRepository.countByUser_Id(login.userId())).isEqualTo(10);
        assertThat(refreshTokenRepository.findByTokenHash(original.getTokenHash()).orElseThrow().getRevokedAt())
            .isEqualTo(original.getRevokedAt());
        assertThat(authSessionService.refresh(independent.refreshToken())).isNotNull();

        accountLifecycleService.withdraw(login.userId());
        assertThat(refreshTokenRepository.findAllByUser_Id(login.userId())).isEmpty();
    }

    @Test
    void onlyOneConcurrentRecoveryCanUseTheSameToken() throws Exception {
        var profile = new SejongUserProfile("recovery-race-user", "홍길동", "컴퓨터공학과");
        var login = (AuthSessionService.LoginSession) authSessionService.login(profile);
        accountLifecycleService.withdraw(login.userId());
        jdbcTemplate.update(
            "UPDATE app_user SET deleted_at = ? WHERE id = ?",
            LocalDateTime.now(ZoneOffset.UTC).minusHours(2),
            login.userId()
        );
        var challenge = (AuthSessionService.RecoveryChallenge) authSessionService.login(profile);

        var barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Object> first = executor.submit(() -> recoverAfterBarrier(barrier, challenge.recoveryToken()));
            Future<Object> second = executor.submit(() -> recoverAfterBarrier(barrier, challenge.recoveryToken()));
            List<Object> results = List.of(
                first.get(10, TimeUnit.SECONDS),
                second.get(10, TimeUnit.SECONDS)
            );

            assertThat(results).filteredOn(AuthSessionService.LoginSession.class::isInstance).hasSize(1);
            assertThat(results).filteredOn(RecoveryTokenInvalidException.class::isInstance).hasSize(1);
            assertThat(appUserRepository.findById(login.userId()).orElseThrow().isDeleted()).isFalse();
            assertThat(refreshTokenRepository.countByUser_Id(login.userId())).isOne();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void recoveryWinsWhenItLocksTheUserBeforeExpiredAccountAnonymization() throws Exception {
        var login = (AuthSessionService.LoginSession) authSessionService.login(new SejongUserProfile(
            "recovery-before-anonymization", "홍길동", "컴퓨터공학과"
        ));
        accountLifecycleService.withdraw(login.userId());
        LocalDateTime deletedAt = LocalDateTime.now(ZoneOffset.UTC).minusHours(2);
        jdbcTemplate.update("UPDATE app_user SET deleted_at = ? WHERE id = ?", deletedAt, login.userId());
        var challenge = (AuthSessionService.RecoveryChallenge) authSessionService.login(new SejongUserProfile(
            "recovery-before-anonymization", "홍길동", "컴퓨터공학과"
        ));

        CountDownLatch recoveryLockedUser = new CountDownLatch(1);
        CountDownLatch allowRecoveryCommit = new CountDownLatch(1);
        blockFirstUserLock(login.userId(), recoveryLockedUser, allowRecoveryCommit);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<AuthSessionService.LoginSession> recovery = executor.submit(
                () -> accountRecoveryService.recover(challenge.recoveryToken())
            );
            assertThat(recoveryLockedUser.await(5, TimeUnit.SECONDS)).isTrue();
            Future<Boolean> anonymization = executor.submit(
                () -> accountLifecycleService.anonymizeExpired(login.userId(), deletedAt.plusDays(31))
            );

            allowRecoveryCommit.countDown();
            assertThat(recovery.get(10, TimeUnit.SECONDS).userId()).isEqualTo(login.userId());
            assertThat(anonymization.get(10, TimeUnit.SECONDS)).isFalse();
        } finally {
            allowRecoveryCommit.countDown();
            executor.shutdownNow();
        }

        var recovered = appUserRepository.findById(login.userId()).orElseThrow();
        assertThat(recovered.isDeleted()).isFalse();
        assertThat(recovered.isAnonymized()).isFalse();
    }

    @Test
    void anonymizationWinsWhenItLocksTheUserBeforeRecovery() throws Exception {
        var login = (AuthSessionService.LoginSession) authSessionService.login(new SejongUserProfile(
            "anonymization-before-recovery", "홍길동", "컴퓨터공학과"
        ));
        accountLifecycleService.withdraw(login.userId());
        LocalDateTime deletedAt = LocalDateTime.now(ZoneOffset.UTC).minusHours(2);
        jdbcTemplate.update("UPDATE app_user SET deleted_at = ? WHERE id = ?", deletedAt, login.userId());
        var challenge = (AuthSessionService.RecoveryChallenge) authSessionService.login(new SejongUserProfile(
            "anonymization-before-recovery", "홍길동", "컴퓨터공학과"
        ));

        CountDownLatch anonymizationLockedUser = new CountDownLatch(1);
        CountDownLatch allowAnonymizationCommit = new CountDownLatch(1);
        blockFirstUserLock(login.userId(), anonymizationLockedUser, allowAnonymizationCommit);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> anonymization = executor.submit(
                () -> accountLifecycleService.anonymizeExpired(login.userId(), deletedAt.plusDays(31))
            );
            assertThat(anonymizationLockedUser.await(5, TimeUnit.SECONDS)).isTrue();
            Future<Object> recovery = executor.submit(() -> {
                try {
                    return accountRecoveryService.recover(challenge.recoveryToken());
                } catch (RecoveryTokenInvalidException exception) {
                    return exception;
                }
            });

            allowAnonymizationCommit.countDown();
            assertThat(anonymization.get(10, TimeUnit.SECONDS)).isTrue();
            assertThat(recovery.get(10, TimeUnit.SECONDS))
                .isInstanceOf(RecoveryTokenInvalidException.class);
        } finally {
            allowAnonymizationCommit.countDown();
            executor.shutdownNow();
        }

        var anonymized = appUserRepository.findById(login.userId()).orElseThrow();
        assertThat(anonymized.isDeleted()).isTrue();
        assertThat(anonymized.isAnonymized()).isTrue();
        assertThat(anonymized.getProviderUserId()).isNull();
    }

    private Object recoverAfterBarrier(CyclicBarrier barrier, String recoveryToken) throws Exception {
        barrier.await(5, TimeUnit.SECONDS);
        try {
            return accountRecoveryService.recover(recoveryToken);
        } catch (RecoveryTokenInvalidException exception) {
            return exception;
        }
    }

    private void blockFirstUserLock(
        Long userId,
        CountDownLatch lockAcquired,
        CountDownLatch allowCommit
    ) {
        AtomicBoolean blockOnce = new AtomicBoolean(true);
        var repositoryDelegate = mockingDetails(appUserRepository).getMockCreationSettings().getDefaultAnswer();
        doAnswer(invocation -> {
            Object result = repositoryDelegate.answer(invocation);
            if (blockOnce.compareAndSet(true, false)) {
                lockAcquired.countDown();
                if (!allowCommit.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("USER_LOCK_NOT_RELEASED");
                }
            }
            return result;
        }).when(appUserRepository).findByIdForUpdate(userId);
    }

    @Test
    void absoluteExpiryDuringJwtIssuanceReturns401AndRollsBackRotation() throws Exception {
        var login = (AuthSessionService.LoginSession) authSessionService.login(new SejongUserProfile("expiry-boundary-user", "홍길동", "컴퓨터공학과"));
        // Reproduce the deadline passing after the locked Refresh check, without sleeping.
        doThrow(new AuthSessionExpiredException()).when(accessTokenService)
            .issueUntil(eq(login.userId()), eq(0L), any(Instant.class));

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("refresh_token", login.refreshToken())))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("REFRESH_TOKEN_INVALID"))
            .andExpect(header().doesNotExist("Set-Cookie"));

        assertThat(refreshTokenRepository.countByUser_Id(login.userId())).isOne();
        assertThat(refreshTokenRepository.findByTokenHash(tokenGenerator.hash(login.refreshToken()))
            .orElseThrow().getRevokedAt()).isNull();
    }
}

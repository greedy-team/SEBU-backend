package com.sebu.backend.auth.controller;

import com.sebu.backend.auth.port.SejongAuthenticator;
import com.sebu.backend.auth.port.SejongUserProfile;
import com.sebu.backend.auth.repository.AccountRecoveryTokenRepository;
import com.sebu.backend.auth.repository.RefreshTokenRepository;
import com.sebu.backend.community.bookmark.domain.CommunityPostBookmark;
import com.sebu.backend.community.bookmark.repository.CommunityPostBookmarkRepository;
import com.sebu.backend.community.like.domain.CommunityPostLike;
import com.sebu.backend.community.like.repository.CommunityPostLikeRepository;
import com.sebu.backend.community.post.domain.CommunityPost;
import com.sebu.backend.community.post.domain.CommunityPostCategory;
import com.sebu.backend.community.post.repository.CommunityPostRepository;
import com.sebu.backend.user.domain.AppUser;
import com.sebu.backend.user.domain.AuthProvider;
import com.sebu.backend.user.repository.AppUserRepository;
import com.sebu.backend.account.service.AccountLifecycleService;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static com.sebu.backend.support.CookieApiRequests.post;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.rate-limit.login.max-requests=100")
@AutoConfigureMockMvc
@Transactional
class AccountRecoveryApiIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired AppUserRepository appUserRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired AccountRecoveryTokenRepository recoveryTokenRepository;
    @Autowired AccountLifecycleService accountLifecycleService;
    @Autowired CommunityPostRepository postRepository;
    @Autowired CommunityPostLikeRepository likeRepository;
    @Autowired CommunityPostBookmarkRepository bookmarkRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired EntityManager entityManager;
    @MockitoBean SejongAuthenticator sejongAuthenticator;

    @BeforeEach
    void setUpAuthenticator() {
        when(sejongAuthenticator.authenticate(anyString(), anyString()))
            .thenAnswer(invocation -> new SejongUserProfile(
                invocation.getArgument(0), "홍길동", "컴퓨터공학과"
            ));
    }

    @Test
    void recoveryRequiresCooldownAndExplicitSingleUseCookieThenKeepsTheExistingProfile() throws Exception {
        MvcResult firstLogin = login("21070001")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.loginStatus").value("AUTHENTICATED"))
            .andReturn();
        Cookie accessBeforeWithdrawal = firstLogin.getResponse().getCookie(AuthCookieFactory.ACCESS_COOKIE);
        AppUser original = user("21070001");
        Long originalId = original.getId();

        accountLifecycleService.withdraw(originalId);
        assertThat(refreshTokenRepository.countByUser_Id(originalId)).isZero();
        assertThat(appUserRepository.findById(originalId).orElseThrow().getAuthVersion()).isOne();

        login("21070001")
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("ACCOUNT_RECOVERY_COOLDOWN"))
            .andExpect(jsonPath("$.data.loginStatus").doesNotExist());
        assertThat(recoveryTokenRepository.countByUser_Id(originalId)).isZero();

        moveWithdrawalTo(originalId, LocalDateTime.now(ZoneOffset.UTC).minusHours(2));
        when(sejongAuthenticator.authenticate("21070001", "password"))
            .thenReturn(new SejongUserProfile("21070001", "변경된이름", "아직없는학과"));

        MvcResult challenge = login("21070001")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.loginStatus").value("RECOVERY_REQUIRED"))
            .andExpect(jsonPath("$.data.recoveryExpiresIn").value(300))
            .andExpect(jsonPath("$.data.recoverableUntil").exists())
            .andExpect(jsonPath("$.data.user").doesNotExist())
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.anyOf(
                    org.hamcrest.Matchers.containsString("21070001"),
                    org.hamcrest.Matchers.containsString("변경된이름")
                )
            )))
            .andReturn();

        Cookie recoveryCookie = challenge.getResponse().getCookie(AuthCookieFactory.RECOVERY_COOKIE);
        assertThat(recoveryCookie).isNotNull();
        assertThat(recoveryCookie.isHttpOnly()).isTrue();
        assertThat(recoveryCookie.getSecure()).isTrue();
        assertThat(recoveryCookie.getPath()).isEqualTo("/api/v1/auth/recovery");
        assertThat(recoveryCookie.getMaxAge()).isEqualTo(300);
        assertThat(challenge.getResponse().getCookie(AuthCookieFactory.ACCESS_COOKIE).getMaxAge()).isZero();
        assertThat(challenge.getResponse().getCookie(AuthCookieFactory.REFRESH_COOKIE).getMaxAge()).isZero();
        assertThat(recoveryTokenRepository.countByUser_Id(originalId)).isOne();

        MvcResult recovered = mockMvc.perform(post("/api/v1/auth/recovery")
                .cookie(recoveryCookie)
                .cookie(new Cookie(AuthCookieFactory.ACCESS_COOKIE, "stale-access-token")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.loginStatus").value("AUTHENTICATED"))
            .andExpect(jsonPath("$.data.user.id").value(originalId))
            .andExpect(jsonPath("$.data.user.isNewUser").value(false))
            .andReturn();

        Cookie accessAfterRecovery = recovered.getResponse().getCookie(AuthCookieFactory.ACCESS_COOKIE);

        assertThat(recovered.getResponse().getCookie(AuthCookieFactory.ACCESS_COOKIE).getMaxAge()).isPositive();
        assertThat(recovered.getResponse().getCookie(AuthCookieFactory.REFRESH_COOKIE).getMaxAge()).isPositive();
        assertThat(recovered.getResponse().getCookie(AuthCookieFactory.RECOVERY_COOKIE).getMaxAge()).isZero();
        assertThat(recoveryTokenRepository.countByUser_Id(originalId)).isZero();
        AppUser restored = appUserRepository.findById(originalId).orElseThrow();
        assertThat(restored.getDeletedAt()).isNull();
        assertThat(restored.getAuthVersion()).isOne();
        assertThat(restored.getName()).isEqualTo("홍길동");

        mockMvc.perform(get("/api/v1/me").cookie(accessBeforeWithdrawal))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("ACCESS_TOKEN_INVALID"));
        mockMvc.perform(get("/api/v1/me").cookie(accessAfterRecovery))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(originalId));

        mockMvc.perform(post("/api/v1/auth/recovery").cookie(recoveryCookie))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("RECOVERY_TOKEN_INVALID"))
            .andExpect(result -> assertThat(result.getResponse()
                .getCookie(AuthCookieFactory.RECOVERY_COOKIE).getMaxAge()).isZero());
        assertThat(firstLogin.getResponse().getCookie(AuthCookieFactory.ACCESS_COOKIE)).isNotNull();
    }

    @Test
    void loginAfterThirtyDaysAnonymizesTheOldAccountDeletesReactionsAndCreatesANewIdentity() throws Exception {
        login("21070002").andExpect(status().isOk());
        AppUser oldUser = user("21070002");
        Long oldUserId = oldUser.getId();
        CommunityPost post = postRepository.saveAndFlush(new CommunityPost(
            oldUser, CommunityPostCategory.FREE, "남겨둘 글", "남겨둘 본문"
        ));
        likeRepository.saveAndFlush(new CommunityPostLike(oldUser, post));
        bookmarkRepository.saveAndFlush(new CommunityPostBookmark(oldUser, post));

        accountLifecycleService.withdraw(oldUserId);
        moveWithdrawalTo(oldUserId, LocalDateTime.now(ZoneOffset.UTC).minusDays(31));

        MvcResult login = login("21070002")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.loginStatus").value("AUTHENTICATED"))
            .andExpect(jsonPath("$.data.user.isNewUser").value(true))
            .andReturn();

        Long newUserId = user("21070002").getId();
        assertThat(newUserId).isNotEqualTo(oldUserId);
        assertThat(appUserRepository.count()).isEqualTo(2);
        AppUser anonymized = appUserRepository.findById(oldUserId).orElseThrow();
        assertThat(anonymized.getDeletedAt()).isNotNull();
        assertThat(anonymized.getAnonymizedAt()).isNotNull();
        assertThat(anonymized.getProvider()).isNull();
        assertThat(anonymized.getProviderUserId()).isNull();
        assertThat(anonymized.getName()).isNull();
        assertThat(likeRepository.count()).isZero();
        assertThat(bookmarkRepository.count()).isZero();
        assertThat(postRepository.findById(post.getId()).orElseThrow().getAuthor().getId()).isEqualTo(oldUserId);

        mockMvc.perform(get("/api/v1/posts/{postId}", post.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.post.author.id").doesNotExist())
            .andExpect(jsonPath("$.data.post.author.nickname").doesNotExist())
            .andExpect(jsonPath("$.data.post.author.status").value("WITHDRAW"));

        assertThat(login.getResponse().getContentAsString()).doesNotContain("accessToken", "refreshToken");
    }

    private org.springframework.test.web.servlet.ResultActions login(String studentId) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/sejong/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"studentId\":\"" + studentId + "\",\"password\":\"password\"}"));
    }

    private AppUser user(String studentId) {
        return appUserRepository.findByProviderAndProviderUserId(AuthProvider.SEJONG, studentId).orElseThrow();
    }

    private void moveWithdrawalTo(Long userId, LocalDateTime deletedAt) {
        appUserRepository.flush();
        jdbcTemplate.update("UPDATE app_user SET deleted_at = ? WHERE id = ?", deletedAt, userId);
        entityManager.clear();
    }
}

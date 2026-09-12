package com.sebu.backend.auth.token;

import com.sebu.backend.auth.config.TokenProperties;
import com.sebu.backend.auth.controller.AuthCookieFactory;
import jakarta.servlet.http.Cookie;
import com.sebu.backend.user.domain.AppUser;
import com.sebu.backend.user.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class JwtSecurityIntegrationTest {
    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtAccessTokenService accessTokenService;

    @Autowired
    JwtEncoder jwtEncoder;

    @Autowired
    TokenProperties properties;

    @Autowired
    AppUserRepository appUserRepository;

    @Test
    void authenticatesCookieAndExposesCurrentUserId() throws Exception {
        AppUser user = appUserRepository.save(AppUser.sejong("jwt-user"));
        mockMvc.perform(get("/api/v1/me")
                .cookie(new Cookie(AuthCookieFactory.ACCESS_COOKIE,
                    accessTokenService.issue(user.getId(), user.getAuthVersion()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(user.getId()))
            .andExpect(jsonPath("$.data.nickname").doesNotExist())
            .andExpect(jsonPath("$.data.profileCompleted").value(false));
    }

    @Test
    void returnsCommonResponseForMissingAccessToken() throws Exception {
        mockMvc.perform(get("/api/v1/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("ACCESS_TOKEN_INVALID"));
    }

    @Test
    void keepsExistingLaboratoryApiPublic() throws Exception {
        mockMvc.perform(get("/api/v1/laboratories"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void returnsExpiredErrorForExpiredAccessToken() throws Exception {
        AppUser user = appUserRepository.save(AppUser.sejong("expired-jwt-user"));
        Instant issuedAt = Instant.now().minus(properties.accessTokenExpiration()).minusSeconds(1);
        JwtAccessTokenService expiredIssuer = new JwtAccessTokenService(
            jwtEncoder,
            properties,
            Clock.fixed(issuedAt, ZoneOffset.UTC)
        );

        mockMvc.perform(get("/api/v1/me")
                .cookie(new Cookie(AuthCookieFactory.ACCESS_COOKIE,
                    expiredIssuer.issue(user.getId(), user.getAuthVersion()))))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("ACCESS_TOKEN_EXPIRED"));
    }

    @Test
    void returnsInvalidErrorForMalformedAccessToken() throws Exception {
        mockMvc.perform(get("/api/v1/me")
                .cookie(new Cookie(AuthCookieFactory.ACCESS_COOKIE, "malformed-token")))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("ACCESS_TOKEN_INVALID"));
    }

    @Test
    void accessTokenIssuedBeforeWithdrawalStaysInvalidAfterRecovery() throws Exception {
        AppUser user = appUserRepository.save(AppUser.sejong("withdrawn-jwt-user"));
        String accessBeforeWithdrawal = accessTokenService.issue(user.getId(), user.getAuthVersion());

        user.withdraw(LocalDateTime.now());
        user.recover();
        appUserRepository.flush();

        mockMvc.perform(get("/api/v1/me")
                .cookie(new Cookie(AuthCookieFactory.ACCESS_COOKIE, accessBeforeWithdrawal)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("ACCESS_TOKEN_INVALID"));

        mockMvc.perform(get("/api/v1/me")
                .cookie(new Cookie(AuthCookieFactory.ACCESS_COOKIE,
                    accessTokenService.issue(user.getId(), user.getAuthVersion()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(user.getId()));
    }

    @Test
    void doesNotAcceptBearerHeaderOrQueryParameterAsAuthentication() throws Exception {
        AppUser user = appUserRepository.save(AppUser.sejong("header-user"));
        String token = accessTokenService.issue(user.getId(), user.getAuthVersion());
        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/me").param("access_token", token))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsDuplicateAndOversizedAccessCookiesWithoutEchoingTheirValues() throws Exception {
        AppUser user = appUserRepository.save(AppUser.sejong("ambiguous-cookie-user"));
        String validToken = accessTokenService.issue(user.getId(), user.getAuthVersion());
        mockMvc.perform(get("/api/v1/me").cookie(
                new Cookie(AuthCookieFactory.ACCESS_COOKIE, validToken),
                new Cookie(AuthCookieFactory.ACCESS_COOKIE, "untrusted-duplicate")))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("ACCESS_TOKEN_INVALID"));
        mockMvc.perform(get("/api/v1/me")
                .cookie(new Cookie(AuthCookieFactory.ACCESS_COOKIE, "x".repeat(2049))))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.message").value("유효하지 않은 인증 토큰입니다."));
    }
}

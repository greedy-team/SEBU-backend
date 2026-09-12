package com.sebu.backend.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.sebu.backend.auth.config.TokenProperties;
import com.sebu.backend.auth.port.SejongAuthenticationException;
import com.sebu.backend.auth.port.SejongAuthenticator;
import com.sebu.backend.auth.port.SejongUserProfile;
import com.sebu.backend.auth.repository.RefreshTokenRepository;
import com.sebu.backend.auth.token.RefreshTokenGenerator;
import com.sebu.backend.user.domain.AuthProvider;
import com.sebu.backend.user.repository.AppUserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.LoggerFactory;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static com.sebu.backend.support.CookieApiRequests.post;
import static com.sebu.backend.support.CookieApiRequests.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.rate-limit.login.max-requests=100")
@AutoConfigureMockMvc
@Transactional
class AuthApiIntegrationTest {
    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    AppUserRepository appUserRepository;

    @Autowired
    RefreshTokenRepository refreshTokenRepository;

    @Autowired
    RefreshTokenGenerator refreshTokenGenerator;

    @Autowired
    JwtEncoder jwtEncoder;

    @Autowired
    TokenProperties tokenProperties;

    @MockitoBean
    SejongAuthenticator sejongAuthenticator;

    @BeforeEach
    void setUpAuthenticator() {
        when(sejongAuthenticator.authenticate(anyString(), anyString()))
            .thenAnswer(invocation -> new SejongUserProfile(
                invocation.getArgument(0), "홍길동", "컴퓨터공학과"
            ));
    }

    @Test
    void logsInNewSejongUserAndSetsSecureRefreshCookie() throws Exception {
        mockMvc.perform(loginRequest("21012345", "password"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.accessToken").doesNotExist())
            .andExpect(jsonPath("$.data.tokenType").doesNotExist())
            .andExpect(jsonPath("$.data.loginStatus").value("AUTHENTICATED"))
            .andExpect(jsonPath("$.data.expiresIn").value(1800))
            .andExpect(jsonPath("$.data.user.isNewUser").value(true))
            .andExpect(jsonPath("$.data.user.profileCompleted").value(false))
            .andExpect(header().stringValues(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.hasItem(allOf(
                containsString("refresh_token="),
                containsString("Path=/api/v1/auth"),
                containsString("Max-Age=1209600"),
                containsString("Secure"),
                containsString("HttpOnly"),
                containsString("SameSite=Lax")
            ))));

        assertThat(appUserRepository.findByProviderAndProviderUserId(AuthProvider.SEJONG, "21012345"))
            .isPresent();
        assertThat(refreshTokenRepository.count()).isOne();
    }

    @Test
    void reusesExistingSejongUserWithoutRevokingOtherLogin() throws Exception {
        MvcResult first = mockMvc.perform(loginRequest("21012345", "password"))
            .andExpect(status().isOk())
            .andReturn();

        mockMvc.perform(loginRequest("21012345", "password"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.user.isNewUser").value(false));

        assertThat(appUserRepository.count()).isOne();
        Long userId = appUserRepository.findByProviderAndProviderUserId(AuthProvider.SEJONG, "21012345")
            .orElseThrow()
            .getId();
        assertThat(refreshTokenRepository.countByUser_Id(userId)).isEqualTo(2);
        assertThat(refreshTokenRepository.findByTokenHash(
            refreshTokenGenerator.hash(refreshTokenFrom(first))
        )).get().extracting(token -> token.getRevokedAt()).isNull();
    }

    @Test
    void mapsInvalidRequestAndSejongFailuresToApiContract() throws Exception {
        mockMvc.perform(post("/api/v1/auth/sejong/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("INVALID_LOGIN_REQUEST"));

        when(sejongAuthenticator.authenticate("21000001", "bad-password"))
            .thenThrow(SejongAuthenticationException.authenticationFailed());
        mockMvc.perform(loginRequest("21000001", "bad-password"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("SEJONG_AUTH_FAILED"));

        when(sejongAuthenticator.authenticate("21000002", "password"))
            .thenThrow(SejongAuthenticationException.systemUnavailable());
        mockMvc.perform(loginRequest("21000002", "password"))
            .andExpect(status().isBadGateway())
            .andExpect(jsonPath("$.error.code").value("SEJONG_SYSTEM_UNAVAILABLE"));
    }

    @Test
    void rejectsInvalidCredentialFormatsWithoutCallingSchool() throws Exception {
        clearInvocations(sejongAuthenticator);

        mockMvc.perform(loginRequest("2101234", "password"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("INVALID_LOGIN_REQUEST"));
        mockMvc.perform(loginRequest("abcdefgh", "password"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("INVALID_LOGIN_REQUEST"));
        mockMvc.perform(loginRequest("21012345", "short"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("INVALID_LOGIN_REQUEST"));
        mockMvc.perform(loginRequest("21012345", "x".repeat(65)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("INVALID_LOGIN_REQUEST"));

        verifyNoInteractions(sejongAuthenticator);
    }

    @Test
    void rotatesRefreshTokenAndRejectsPreviousTokenWithoutCallingSejong() throws Exception {
        MvcResult login = mockMvc.perform(loginRequest("21000003", "password"))
            .andExpect(status().isOk())
            .andReturn();
        String previousToken = refreshTokenFrom(login);
        clearInvocations(sejongAuthenticator);

        MvcResult refresh = mockMvc.perform(post("/api/v1/auth/refresh")
                .cookie(new Cookie(AuthCookieFactory.REFRESH_COOKIE, previousToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.accessToken").doesNotExist())
            .andExpect(jsonPath("$.data.tokenType").doesNotExist())
            .andExpect(jsonPath("$.data.expiresIn").value(1800))
            .andReturn();

        String rotatedToken = refreshTokenFrom(refresh);
        assertThat(rotatedToken).isNotEqualTo(previousToken);
        verifyNoInteractions(sejongAuthenticator);

        mockMvc.perform(post("/api/v1/auth/refresh")
                .cookie(new Cookie(AuthCookieFactory.REFRESH_COOKIE, previousToken)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("REFRESH_TOKEN_INVALID"));
    }

    @Test
    void refreshesWithValidCookieEvenWhenExpiredAccessCookieIsPresent() throws Exception {
        MvcResult login = mockMvc.perform(loginRequest("21000004", "password"))
            .andExpect(status().isOk())
            .andReturn();

        mockMvc.perform(post("/api/v1/auth/refresh")
                .cookie(new Cookie(AuthCookieFactory.ACCESS_COOKIE, expiredAccessToken()))
                .cookie(new Cookie(AuthCookieFactory.REFRESH_COOKIE, refreshTokenFrom(login))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.accessToken").doesNotExist());
    }

    @Test
    void refreshesWithValidCookieEvenWhenInvalidAccessCookieIsPresent() throws Exception {
        MvcResult login = mockMvc.perform(loginRequest("21000005", "password"))
            .andExpect(status().isOk())
            .andReturn();

        mockMvc.perform(post("/api/v1/auth/refresh")
                .cookie(new Cookie(AuthCookieFactory.ACCESS_COOKIE, "invalid-access-token"))
                .cookie(new Cookie(AuthCookieFactory.REFRESH_COOKIE, refreshTokenFrom(login))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.accessToken").doesNotExist());
    }

    @Test
    void rejectsRefreshWithoutCookie() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("REFRESH_TOKEN_INVALID"));
    }

    @Test
    void logsOutOnlyCurrentTokenAndClearsCookie() throws Exception {
        MvcResult firstLogin = mockMvc.perform(loginRequest("21000006", "password"))
            .andExpect(status().isOk())
            .andReturn();
        MvcResult secondLogin = mockMvc.perform(loginRequest("21000006", "password"))
            .andExpect(status().isOk())
            .andReturn();
        String firstToken = refreshTokenFrom(firstLogin);
        String secondToken = refreshTokenFrom(secondLogin);

        mockMvc.perform(post("/api/v1/auth/logout")
                .cookie(new Cookie(AuthCookieFactory.REFRESH_COOKIE, firstToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.message").value("로그아웃되었습니다."))
            .andExpect(header().stringValues(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.hasItem(allOf(
                containsString("refresh_token="),
                containsString("Max-Age=0"),
                containsString("Path=/api/v1/auth"),
                containsString("Secure"),
                containsString("HttpOnly"),
                containsString("SameSite=Lax")
            ))));

        mockMvc.perform(post("/api/v1/auth/refresh")
                .cookie(new Cookie(AuthCookieFactory.REFRESH_COOKIE, firstToken)))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/logout")
                .cookie(new Cookie(AuthCookieFactory.REFRESH_COOKIE, firstToken)))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/auth/refresh")
                .cookie(new Cookie(AuthCookieFactory.REFRESH_COOKIE, secondToken)))
            .andExpect(status().isOk());
    }

    @Test
    void logoutWithoutCookieIsIdempotent() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.message").value("로그아웃되었습니다."))
            .andExpect(header().stringValues(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.hasItem(containsString("Max-Age=0"))));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5})
    void authenticatedUserChoosesGradeAndReloginDoesNotOverwriteIt(int grade) throws Exception {
        MvcResult login = mockMvc.perform(loginRequest("21012345", "known-fake-password-for-log-test"))
            .andExpect(status().isOk())
            .andReturn();
        String accessToken = login.getResponse().getCookie(AuthCookieFactory.ACCESS_COOKIE).getValue();

        mockMvc.perform(patch("/api/v1/me/profile")
                .cookie(new Cookie(AuthCookieFactory.ACCESS_COOKIE, accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"grade\":%d}".formatted(grade)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.studentId").value("21012345"))
            .andExpect(jsonPath("$.data.name").value("홍길동"))
            .andExpect(jsonPath("$.data.nickname").doesNotExist())
            .andExpect(jsonPath("$.data.department.name").value("컴퓨터공학과"))
            .andExpect(jsonPath("$.data.department.code").doesNotExist())
            .andExpect(jsonPath("$.data.grade").value(grade))
            .andExpect(jsonPath("$.data.profileCompleted").value(true));

        mockMvc.perform(loginRequest("21012345", "known-fake-password-for-log-test"))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/me")
                .cookie(new Cookie(AuthCookieFactory.ACCESS_COOKIE, accessToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.grade").value(grade));

        mockMvc.perform(patch("/api/v1/me/profile")
                .cookie(new Cookie(AuthCookieFactory.ACCESS_COOKIE, accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"grade\":6}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("INVALID_GRADE"));

        mockMvc.perform(patch("/api/v1/me/profile")
                .cookie(new Cookie(AuthCookieFactory.ACCESS_COOKIE, accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("INVALID_GRADE"));

        mockMvc.perform(patch("/api/v1/me/profile")
                .cookie(new Cookie(AuthCookieFactory.ACCESS_COOKIE, accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("not-json"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("INVALID_GRADE"));
    }

    @Test
    void loginCredentialsAreNotWrittenToApplicationLogs() throws Exception {
        String fakePassword = "known-fake-password-must-never-appear";
        Logger rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        rootLogger.addAppender(appender);
        try {
            mockMvc.perform(loginRequest("21012345", fakePassword))
                .andExpect(status().isOk());
        } finally {
            rootLogger.detachAppender(appender);
            appender.stop();
        }

        assertThat(appender.list)
            .extracting(ILoggingEvent::getFormattedMessage)
            .allSatisfy(message -> assertThat(message)
                .doesNotContain("21012345", fakePassword, "SSOTOKEN", "JSESSIONID", "Set-Cookie"));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder loginRequest(
        String studentId,
        String password
    ) throws Exception {
        return post("/api/v1/auth/sejong/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new LoginRequestFixture(studentId, password)));
    }

    private String refreshTokenFrom(MvcResult result) {
        String setCookie = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
            .filter(value -> value.startsWith(AuthCookieFactory.REFRESH_COOKIE + "=")).findFirst().orElseThrow();
        String prefix = AuthCookieFactory.REFRESH_COOKIE + "=";
        int start = setCookie.indexOf(prefix) + prefix.length();
        int end = setCookie.indexOf(';', start);
        return setCookie.substring(start, end);
    }

    private String expiredAccessToken() {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .subject("1")
            .claim("role", "USER")
            .issuedAt(now.minus(tokenProperties.accessTokenExpiration()).minusSeconds(120))
            .expiresAt(now.minusSeconds(120))
            .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private record LoginRequestFixture(String studentId, String password) {
    }
}

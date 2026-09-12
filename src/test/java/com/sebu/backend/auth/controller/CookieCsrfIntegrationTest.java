package com.sebu.backend.auth.controller;

import com.sebu.backend.auth.port.SejongAuthenticator;
import com.sebu.backend.auth.port.SejongUserProfile;
import com.sebu.backend.auth.repository.RefreshTokenRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import static com.sebu.backend.support.CookieApiRequests.ORIGIN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "app.rate-limit.login.max-requests=100")
@AutoConfigureMockMvc
@Transactional
class CookieCsrfIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired RefreshTokenRepository tokens;
    @MockitoBean SejongAuthenticator sejongAuthenticator;

    @BeforeEach
    void school() {
        when(sejongAuthenticator.authenticate(anyString(), anyString()))
            .thenAnswer(call -> new SejongUserProfile(call.getArgument(0), "홍길동", "컴퓨터공학과"));
    }

    @Test
    void bootstrapsReadableCsrfCookieWithoutCreatingHttpSessionOrAuthTokens() throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/auth/csrf")
                .cookie(new Cookie("access_token", "expired-or-invalid")))
            .andExpect(status().isNoContent()).andExpect(header().string("Cache-Control", "no-store")).andReturn();
        Cookie csrf = result.getResponse().getCookie("XSRF-TOKEN");
        assertThat(csrf).isNotNull();
        assertThat(csrf.isHttpOnly()).isFalse();
        assertThat(csrf.getSecure()).isTrue();
        assertThat(csrf.getPath()).isEqualTo("/");
        assertThat(csrf.getAttribute("SameSite")).isEqualTo("Lax");
        assertThat(result.getResponse().getCookie("access_token")).isNull();
        assertThat(result.getRequest().getSession(false)).isNull();
        assertThat(tokens.count()).isZero();
        verifyNoInteractions(sejongAuthenticator);
    }

    @Test
    void loginRefreshAndLogoutRequireCsrfEvenThoughTheyPermitAnonymousAuthentication() throws Exception {
        for (String path : new String[]{"/api/v1/auth/sejong/login", "/api/v1/auth/refresh", "/api/v1/auth/logout"}) {
            mvc.perform(post(path).header("Origin", ORIGIN).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("CSRF_TOKEN_INVALID"));
        }
        verifyNoInteractions(sejongAuthenticator);
    }

    @Test
    void rejectsMismatchedCsrfAndDoesNotAcceptTokenOnlyInQuery() throws Exception {
        Cookie csrf = csrf();
        mvc.perform(post("/api/v1/auth/logout").header("Origin", ORIGIN).cookie(csrf)
                .header("X-XSRF-TOKEN", "wrong"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/auth/logout").header("Origin", ORIGIN).cookie(csrf)
                .param("_csrf", csrf.getValue()))
            .andExpect(status().isForbidden());
    }

    @Test
    void rejectsForeignNullMissingAndLookalikeOriginsEvenWithValidCsrf() throws Exception {
        Cookie csrf = csrf();
        for (String origin : new String[]{"https://evil.example", "null", ORIGIN + ".evil.example", "https://other-preview.vercel.app"}) {
            mvc.perform(post("/api/v1/auth/logout").header("Origin", origin)
                    .cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/v1/auth/logout").cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue()))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/auth/logout").cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())
                .header("Referer", ORIGIN + "/community"))
            .andExpect(status().isOk());
    }

    @Test
    void issuesTwoHttpOnlyCookiesAndRenewsCsrfThenKeepsMeReadOnly() throws Exception {
        Cookie initial = csrf();
        MvcResult login = login(initial);
        var response = login.getResponse();
        assertThat(response.getHeaders("Set-Cookie")).hasSize(4);
        Cookie access = response.getCookie("access_token");
        Cookie refresh = response.getCookie("refresh_token");
        assertThat(access.isHttpOnly()).isTrue();
        assertThat(refresh.isHttpOnly()).isTrue();
        assertThat(access.getPath()).isEqualTo("/api/v1");
        assertThat(refresh.getPath()).isEqualTo("/api/v1/auth");
        assertThat(access.getSecure()).isTrue();
        assertThat(refresh.getSecure()).isTrue();
        assertThat(access.getDomain()).isNull();
        assertThat(refresh.getMaxAge()).isEqualTo(14 * 24 * 3600);
        assertThat(response.getCookie("recovery_token").getMaxAge()).isZero();
        assertThat(response.getCookie("XSRF-TOKEN").getValue()).isNotEqualTo(initial.getValue());
        assertThat(response.getCookie("XSRF-TOKEN").getAttribute("SameSite")).isEqualTo("Lax");
        assertThat(response.getContentAsString()).doesNotContain(access.getValue(), refresh.getValue(), "accessToken", "refreshToken", "tokenType");
        for (int i = 0; i < 3; i++) {
            mvc.perform(get("/api/v1/me").cookie(access, response.getCookie("XSRF-TOKEN")))
                .andExpect(status().isOk()).andExpect(header().doesNotExist("Set-Cookie"));
        }
        assertThat(tokens.count()).isOne();
    }

    @Test
    void replayFailureDoesNotClearCookiesOrRevokeSuccessfulRotation() throws Exception {
        MvcResult login = login(csrf());
        Cookie csrf = login.getResponse().getCookie("XSRF-TOKEN");
        Cookie old = login.getResponse().getCookie("refresh_token");
        MvcResult rotated = mvc.perform(protect(post("/api/v1/auth/refresh"), csrf).cookie(old))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.accessToken").doesNotExist()).andReturn();
        mvc.perform(protect(post("/api/v1/auth/refresh"), csrf).cookie(old))
            .andExpect(status().isUnauthorized()).andExpect(header().doesNotExist("Set-Cookie"));
        mvc.perform(protect(post("/api/v1/auth/refresh"), csrf)
                .cookie(rotated.getResponse().getCookie("refresh_token")))
            .andExpect(status().isOk());
    }

    @Test
    void logoutWithRotatedAncestorRevokesDescendantAndDeletesBothAuthCookies() throws Exception {
        MvcResult login = login(csrf());
        Cookie csrf = login.getResponse().getCookie("XSRF-TOKEN");
        Cookie old = login.getResponse().getCookie("refresh_token");
        MvcResult refresh = mvc.perform(protect(post("/api/v1/auth/refresh"), csrf).cookie(old))
            .andExpect(status().isOk()).andReturn();
        MvcResult logout = mvc.perform(protect(post("/api/v1/auth/logout"), csrf).cookie(old))
            .andExpect(status().isOk()).andReturn();
        assertThat(logout.getResponse().getCookie("access_token").getMaxAge()).isZero();
        assertThat(logout.getResponse().getCookie("refresh_token").getMaxAge()).isZero();
        Cookie renewed = logout.getResponse().getCookie("XSRF-TOKEN");
        assertThat(renewed.getValue()).isNotEqualTo(csrf.getValue());
        mvc.perform(protect(post("/api/v1/auth/refresh"), renewed)
                .cookie(refresh.getResponse().getCookie("refresh_token")))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void withdrawalRequiresCsrfAndClearsCookiesAndRefreshGrants() throws Exception {
        MvcResult login = login(csrf());
        Cookie access = login.getResponse().getCookie("access_token");
        Cookie csrf = login.getResponse().getCookie("XSRF-TOKEN");
        mvc.perform(delete("/api/v1/users/me").header("Origin", ORIGIN).cookie(access))
            .andExpect(status().isForbidden());
        MvcResult deleted = mvc.perform(protect(delete("/api/v1/users/me"), csrf).cookie(access))
            .andExpect(status().isNoContent()).andReturn();
        assertThat(deleted.getResponse().getCookie("access_token").getMaxAge()).isZero();
        assertThat(deleted.getResponse().getCookie("refresh_token").getMaxAge()).isZero();
        mvc.perform(protect(post("/api/v1/auth/refresh"), deleted.getResponse().getCookie("XSRF-TOKEN"))
                .cookie(login.getResponse().getCookie("refresh_token")))
            .andExpect(status().isUnauthorized());
    }

    private Cookie csrf() throws Exception {
        return mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isNoContent())
            .andReturn().getResponse().getCookie("XSRF-TOKEN");
    }

    private MvcResult login(Cookie csrf) throws Exception {
        return mvc.perform(protect(post("/api/v1/auth/sejong/login"), csrf)
                .contentType(MediaType.APPLICATION_JSON).content("{\"studentId\":\"21012345\",\"password\":\"test-only-password\"}"))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store")).andReturn();
    }

    private MockHttpServletRequestBuilder protect(MockHttpServletRequestBuilder request, Cookie csrf) {
        return request.header("Origin", ORIGIN).cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue());
    }
}

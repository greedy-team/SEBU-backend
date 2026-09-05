package com.sebu.backend.global.ratelimit.web;

import com.sebu.backend.global.auth.CurrentUserProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "app.rate-limit.max-requests=2",
    "app.rate-limit.search-max-requests=2",
    "app.rate-limit.anonymous-ip-multiplier=2",
    "app.rate-limit.window=1m"
})
@AutoConfigureMockMvc
class RateLimitApiIntegrationTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean CurrentUserProvider currentUserProvider;

    @Test
    void returns429WithRetryAfterWhenAnonymousIpExceedsLimit() throws Exception {
        when(currentUserProvider.currentUserId()).thenReturn(Optional.empty());
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(requestFrom("192.0.2.10").session(session)).andExpect(status().isOk());
        mockMvc.perform(requestFrom("192.0.2.10").session(session)).andExpect(status().isOk());
        mockMvc.perform(requestFrom("192.0.2.10").session(session))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().string("Retry-After", "30"))
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.data").doesNotExist())
            .andExpect(jsonPath("$.error.code").value("RATE_LIMIT_EXCEEDED"))
            .andExpect(jsonPath("$.error.message").value("요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."));

        mockMvc.perform(requestFrom("192.0.2.11")).andExpect(status().isOk());
    }

    @Test
    void anonymousSessionsAreIndependentButShareTheHigherIpSafetyLimit() throws Exception {
        when(currentUserProvider.currentUserId()).thenReturn(Optional.empty());

        for (int index = 0; index < 4; index++) {
            mockMvc.perform(requestFrom("192.0.2.30").session(new MockHttpSession()))
                .andExpect(status().isOk());
        }
        mockMvc.perform(requestFrom("192.0.2.30").session(new MockHttpSession()))
            .andExpect(status().isTooManyRequests());
    }

    @Test
    void anonymousRequestsWithoutSessionUseOnlyIpLimitAndDoNotCreateSession() throws Exception {
        when(currentUserProvider.currentUserId()).thenReturn(Optional.empty());

        for (int index = 0; index < 4; index++) {
            var result = mockMvc.perform(requestFrom("192.0.2.40"))
                .andExpect(status().isOk())
                .andReturn();

            assertThat(result.getRequest().getSession(false)).isNull();
        }

        var rejected = mockMvc.perform(requestFrom("192.0.2.40"))
            .andExpect(status().isTooManyRequests())
            .andReturn();
        assertThat(rejected.getRequest().getSession(false)).isNull();
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authenticatedRequestFrom(
        String ip,
        String subject
    ) {
        return requestFrom(ip).with(jwt().jwt(token -> token.subject(subject).claim("role", "USER")));
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder requestFrom(String ip) {
        return get("/api/v1/laboratories").with(request -> {
            request.setRemoteAddr(ip);
            return request;
        });
    }

    @Test
    void authenticatedUsersHaveIndependentLimitsRegardlessOfIp() throws Exception {
        when(currentUserProvider.currentUserId()).thenReturn(Optional.of(100L));
        mockMvc.perform(authenticatedRequestFrom("192.0.2.20", "100")).andExpect(status().isOk());
        mockMvc.perform(authenticatedRequestFrom("192.0.2.20", "100")).andExpect(status().isOk());
        mockMvc.perform(authenticatedRequestFrom("192.0.2.20", "100")).andExpect(status().isTooManyRequests());

        when(currentUserProvider.currentUserId()).thenReturn(Optional.of(200L));
        mockMvc.perform(authenticatedRequestFrom("192.0.2.20", "200")).andExpect(status().isOk());
    }
}

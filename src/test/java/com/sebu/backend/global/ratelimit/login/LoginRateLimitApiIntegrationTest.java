package com.sebu.backend.global.ratelimit.login;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.rate-limit.login.max-requests=2")
@AutoConfigureMockMvc
class LoginRateLimitApiIntegrationTest {
    @Autowired
    MockMvc mockMvc;

    @Test
    void limitsLoginPostRequestsByIpWithLoginSpecificError() throws Exception {
        mockMvc.perform(loginRequestFrom("192.0.2.10")).andExpect(status().isBadRequest());
        mockMvc.perform(loginRequestFrom("192.0.2.10")).andExpect(status().isBadRequest());
        mockMvc.perform(loginRequestFrom("192.0.2.10"))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().string("Retry-After", "30"))
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("LOGIN_RATE_LIMITED"));

        mockMvc.perform(loginRequestFrom("192.0.2.11")).andExpect(status().isBadRequest());
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder loginRequestFrom(String ip) {
        return post("/api/v1/auth/sejong/login")
            .contentType("application/json")
            .content("{}")
            .with(request -> {
                request.setRemoteAddr(ip);
                return request;
            });
    }
}

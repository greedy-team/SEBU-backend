package com.sebu.backend.global.logging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sebu.backend.global.ratelimit.dto.RateLimitDecision;
import com.sebu.backend.global.ratelimit.login.LoginRateLimitInterceptor;
import com.sebu.backend.global.ratelimit.login.LoginRateLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RateLimitLoggingTest {
    @Test
    void rejectedLoginLogsSafePolicyAndTraceWithoutIp() throws Exception {
        var limiter = mock(LoginRateLimiter.class);
        when(limiter.tryAcquire("192.0.2.100")).thenReturn(new RateLimitDecision(false, 45));
        var mapper = new ObjectMapper();
        var interceptor = new LoginRateLimitInterceptor(limiter, mapper);
        var request = new MockHttpServletRequest("POST", "/api/v1/auth/sejong/login");
        request.setRemoteAddr("192.0.2.100");
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/v1/auth/sejong/login");
        var response = new MockHttpServletResponse();
        try (var capture = new LogCapture()) {
            new RequestLoggingFilter().doFilter(request, response,
                (req, res) -> assertThat(interceptor.preHandle(request, response, new Object())).isFalse());
            assertThat(response.getStatus()).isEqualTo(429);
            String traceId = response.getHeader(RequestTrace.HEADER);
            assertThat(mapper.readTree(response.getContentAsString()).path("error").path("traceId").asText())
                .isEqualTo(traceId);
            var event = capture.events("security.rate_limit.rejected").getFirst();
            assertThat(event.path("traceId").asText()).isEqualTo(traceId);
            assertThat(event.path("retrySeconds").asInt()).isEqualTo(45);
            assertThat(event.path("limitKind").asText()).isEqualTo("LOGIN");
            assertThat(String.join("", capture.lines)).doesNotContain("192.0.2.100");
        }
    }
}

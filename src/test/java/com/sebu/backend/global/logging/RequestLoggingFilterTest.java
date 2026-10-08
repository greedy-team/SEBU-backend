package com.sebu.backend.global.logging;

import jakarta.servlet.DispatcherType;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestLoggingFilterTest {
    @Test
    void unhandledFailureCleansTraceAndErrorDispatchDoesNotLogTwice() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/FAKE_PRIVATE_PATH");
        var response = new MockHttpServletResponse();
        var filter = new RequestLoggingFilter();
        try (var capture = new LogCapture()) {
            assertThatThrownBy(() -> filter.doFilter(request, response, (req, res) -> {
                throw new IllegalStateException("FAKE_PRIVATE_MESSAGE");
            })).isInstanceOf(IllegalStateException.class);
            assertThat(MDC.get("traceId")).isNull();
            assertThat(capture.events("api.unexpected_failure")).hasSize(1);
            assertThat(capture.events("http.request.completed")).hasSize(1);
            assertThat(capture.events("http.request.completed").getFirst().path("status").asInt()).isEqualTo(500);
            request.setDispatcherType(DispatcherType.ERROR);
            request.setAttribute("jakarta.servlet.error.request_uri", request.getRequestURI());
            filter.doFilter(request, response, (req, res) -> { });
            assertThat(capture.events("http.request.completed")).hasSize(1);
            assertThat(String.join("", capture.lines)).doesNotContain("FAKE_PRIVATE");
        }
    }

    @Test
    void successfulNonBusinessProbeIsExcluded() throws Exception {
        try (var capture = new LogCapture()) {
            new RequestLoggingFilter().doFilter(new MockHttpServletRequest("GET", "/actuator/prometheus"),
                new MockHttpServletResponse(), (req, res) -> { });
            assertThat(capture.events("http.request.completed")).isEmpty();
        }
    }
}

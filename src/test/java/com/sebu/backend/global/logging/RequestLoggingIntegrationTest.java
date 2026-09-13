package com.sebu.backend.global.logging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sebu.backend.auth.port.SejongAuthenticationException;
import com.sebu.backend.auth.port.SejongAuthenticator;
import com.sebu.backend.auth.port.SejongUserProfile;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static com.sebu.backend.support.CookieApiRequests.post;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.rate-limit.login.max-requests=100")
@AutoConfigureMockMvc
@Transactional
class RequestLoggingIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper mapper;
    @MockitoBean SejongAuthenticator sejongAuthenticator;

    @Test
    void securityRejectionsShareServerGeneratedTraceAndDoNotLeakBetweenRequests() throws Exception {
        try (var capture = new LogCapture()) {
            var unauthorized = mockMvc.perform(get("/api/v1/me")
                    .header(RequestTrace.HEADER, "client-controlled-secret").queryParam("token", "FAKE_QUERY_SECRET"))
                .andExpect(status().isUnauthorized()).andReturn();
            String first = assertErrorTrace(unauthorized);
            var forbidden = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .post("/api/v1/auth/logout"))
                .andExpect(status().isForbidden()).andReturn();
            String second = assertErrorTrace(forbidden);
            assertThat(first).isNotEqualTo(second);
            assertThat(RequestTrace.currentId()).isNull();
            assertThat(MDC.get("traceId")).isNull();
            assertThat(capture.events("http.request.completed")).hasSize(2);
            assertThat(capture.events("security.access.rejected")).hasSize(2);
            assertThat(capture.events("http.request.completed"))
                .allSatisfy(event -> assertThat(event.path("route").asText()).isEqualTo("UNMATCHED"));
            assertThat(String.join("", capture.lines)).doesNotContain("client-controlled-secret", "FAKE_QUERY_SECRET");
        }
    }

    @Test
    void matchedRouteUsesTemplateAndUnknownPathNeverAppearsInLog() throws Exception {
        try (var capture = new LogCapture()) {
            mockMvc.perform(get("/api/v1/posts/987654321").queryParam("query", "FAKE_SEARCH_SECRET"))
                .andExpect(status().isNotFound());
            mockMvc.perform(get("/api/v1/posts/FAKE_PATH_SECRET/unknown"));
            var events = capture.events("http.request.completed");
            assertThat(events).hasSize(2);
            assertThat(events.getFirst().path("route").asText()).isEqualTo("/api/v1/posts/{postId}");
            assertThat(events.getLast().path("route").asText()).isEqualTo("UNMATCHED");
            assertThat(String.join("", capture.lines)).doesNotContain("987654321", "FAKE_SEARCH_SECRET", "FAKE_PATH_SECRET");
        }
    }

    @Test
    void finalUpstreamFailureHasOneSafeDetailAndOneRequestSummary() throws Exception {
        String secret = "FAKE_PASSWORD_TOKEN_EMAIL@example.test";
        var cause = new IllegalStateException(secret, new RuntimeException(secret));
        cause.addSuppressed(new RuntimeException(secret));
        when(sejongAuthenticator.authenticate(anyString(), anyString()))
            .thenThrow(SejongAuthenticationException.systemUnavailable(cause)
                .atStage("user-info", SejongAuthenticationException.FailureKind.IO_FAILURE));
        try (var capture = new LogCapture()) {
            var result = mockMvc.perform(post("/api/v1/auth/sejong/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"studentId\":\"21012345\",\"password\":\"FAKE_PASSWORD_123\"}"))
                .andExpect(status().isBadGateway()).andReturn();
            String traceId = assertErrorTrace(result);
            var failures = capture.events("auth.login.completed");
            assertThat(failures).hasSize(1);
            assertThat(failures.getFirst().path("level").asText()).isEqualTo("ERROR");
            assertThat(failures.getFirst().path("stage").asText()).isEqualTo("USER_INFO");
            assertThat(failures.getFirst().path("traceId").asText()).isEqualTo(traceId);
            assertThat(capture.events("http.request.completed")).hasSize(1);
            assertThat(capture.events("api.unexpected_failure")).isEmpty();
            assertThat(String.join("", capture.lines)).doesNotContain("21012345", "FAKE_PASSWORD", secret);
        }
    }

    @Test
    void successAndMalformedRefreshHaveExplicitOutcomesWithoutCredentials() throws Exception {
        when(sejongAuthenticator.authenticate(anyString(), anyString()))
            .thenReturn(new SejongUserProfile("21012345", "FAKE_PRIVATE_NAME", "컴퓨터공학과"));
        try (var capture = new LogCapture()) {
            mockMvc.perform(post("/api/v1/auth/sejong/login").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"studentId\":\"21012345\",\"password\":\"FAKE_PASSWORD_123\"}"))
                .andExpect(status().isOk());
            var result = mockMvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized()).andReturn();
            assertErrorTrace(result);
            var login = capture.events("auth.login.completed");
            assertThat(login).hasSize(1);
            assertThat(login.getFirst().path("outcome").asText()).isEqualTo("SUCCESS");
            assertThat(login.getFirst().has("newUser")).isTrue();
            assertThat(capture.events("auth.refresh.completed").getFirst().path("reason").asText())
                .isEqualTo("MALFORMED");
            assertThat(String.join("", capture.lines)).doesNotContain("21012345", "FAKE_PASSWORD", "FAKE_PRIVATE_NAME");
        }
    }

    private String assertErrorTrace(MvcResult result) throws Exception {
        String id = result.getResponse().getHeader(RequestTrace.HEADER);
        assertThat(id).matches("[a-f0-9]{32}");
        assertThat(mapper.readTree(result.getResponse().getContentAsString()).path("error").path("traceId").asText())
            .isEqualTo(id);
        return id;
    }
}

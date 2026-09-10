package com.sebu.backend.global.ratelimit.login;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "server.forward-headers-strategy=native",
        "app.auth.transport.require-https=true",
        "app.rate-limit.login.max-requests=1"
    }
)
class ForwardedHeadersIntegrationTest {
    @LocalServerPort
    int port;

    @Test
    void honorsTrustedProxyProtocolAndClientIpHeaders() {
        assertThat(loginStatus("192.0.2.10")).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(loginStatus("192.0.2.10")).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(loginStatus("192.0.2.11")).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void acceptsDockerHealthTargetWhenForwardedProtocolIsHttps() {
        HttpStatusCode status = RestClient.create("http://127.0.0.1:" + port)
            .get()
            .uri("/api/v1/laboratories")
            .header("X-Forwarded-Proto", "https")
            .exchange((request, response) -> response.getStatusCode());

        assertThat(status).isEqualTo(HttpStatus.OK);
    }

    private HttpStatusCode loginStatus(String clientIp) {
        return RestClient.create("http://127.0.0.1:" + port)
            .post()
            .uri("/api/v1/auth/sejong/login")
            .header("X-Forwarded-For", clientIp)
            .header("X-Forwarded-Proto", "https")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Origin", "https://sebu-frontend.vercel.app")
            .header("Cookie", "XSRF-TOKEN=forwarded-header-test-csrf")
            .header("X-XSRF-TOKEN", "forwarded-header-test-csrf")
            .body("{}")
            .exchange((request, response) -> response.getStatusCode());
    }
}

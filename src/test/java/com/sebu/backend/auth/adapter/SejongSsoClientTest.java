package com.sebu.backend.auth.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sebu.backend.auth.config.SejongClientProperties;
import com.sebu.backend.auth.port.SejongAuthenticationException;
import com.sebu.backend.auth.port.SejongUserProfile;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import okhttp3.Protocol;
import org.conscrypt.Conscrypt;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SejongSsoClientTest {
    private HttpServer server;
    private HttpServer foreignServer;
    private final AtomicInteger foreignRequestCount = new AtomicInteger();
    private final AtomicInteger portalRedirectTargetRequests = new AtomicInteger();
    private String portalRequestBody;
    private String portalRequestReferer;
    private String portalRequestCookies;
    private String portalRequestUserAgent;
    private String portalRequestOrigin;
    private boolean portalSsoReached;
    private boolean loginPageRedirectReached;
    private boolean portalSsoRedirectReached;
    private boolean ssoRedirectReached;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
        if (foreignServer != null) {
            foreignServer.stop(0);
        }
    }

    @Test
    void usesOneCookieStoreThroughoutAFlowAndParsesProfile() throws Exception {
        startServer(Scenario.SUCCESS);
        SejongSsoClient client = client(Duration.ofSeconds(1));

        SejongUserProfile profile = client.authenticate(" 21012345 ", "portal-password");

        assertThat(profile.studentId()).isEqualTo("21012345");
        assertThat(profile.name()).isEqualTo("홍길동");
        assertThat(profile.departmentName()).isEqualTo("컴퓨터공학과");
        assertThat(portalFormValue("mainLogin")).isEqualTo("Y");
        assertThat(portalFormValue("id")).isEqualTo("21012345");
        assertThat(portalFormValue("password")).isEqualTo("portal-password");
        assertThat(portalFormValue("rtUrl"))
            .isEqualTo("portal.sejong.ac.kr/comm/member/user/ssoLoginProc.do");
        assertThat(portalRequestReferer).endsWith("/login-page");
        assertThat(portalRequestUserAgent).startsWith("Mozilla/5.0");
        assertThat(portalRequestOrigin).isEqualTo("http://localhost:" + server.getAddress().getPort());
        assertThat(portalRequestCookies)
            .contains("WMONID=portal-monitor", "PO_JSESSIONID=portal-session");
        assertThat(portalSsoReached).isTrue();
    }

    @Test
    void sharesConnectionsButSeparatesCookieStoresForEveryLoginRequest() throws Exception {
        startServer(Scenario.SUCCESS);
        SejongClientProperties properties = properties(Duration.ofSeconds(1));
        SejongHttpClientFactory factory = new SejongHttpClientFactory(properties);

        SejongHttpClientFactory.Session first = factory.create();
        SejongHttpClientFactory.Session second = factory.create();

        assertThat(first.httpClient()).isNotSameAs(second.httpClient());
        assertThat(first.cookieManager()).isNotSameAs(second.cookieManager());
        assertThat(first.cookieManager().getCookieStore())
            .isNotSameAs(second.cookieManager().getCookieStore());
        assertThat(first.httpClient().connectionPool()).isSameAs(second.httpClient().connectionPool());
        assertThat(first.httpClient().dispatcher()).isSameAs(second.httpClient().dispatcher());
        assertThat(first.httpClient().protocols()).containsExactly(Protocol.HTTP_1_1);
        assertThat(first.httpClient().connectionSpecs()).anySatisfy(specification -> {
            assertThat(specification.tlsVersions())
                .containsExactly(SejongHttpClientFactory.SEJONG_TLS_VERSION);
            assertThat(specification.cipherSuites())
                .containsExactly(SejongHttpClientFactory.SEJONG_LEGACY_CIPHER_SUITE);
        });
        assertThat(Conscrypt.isConscrypt(first.httpClient().sslSocketFactory())).isTrue();
    }

    @Test
    void clearsSchoolCookiesWhenAuthenticationRequestEnds() throws Exception {
        startServer(Scenario.SUCCESS);
        SejongClientProperties properties = properties(Duration.ofSeconds(1));
        SejongHttpClientFactory.Session session = new SejongHttpClientFactory(properties).create();
        SejongHttpClientFactory factory = mock(SejongHttpClientFactory.class);
        when(factory.create()).thenReturn(session);
        SejongSsoClient client = new SejongSsoClient(
            factory,
            properties,
            new SejongUserInfoParser(new ObjectMapper())
        );

        client.authenticate("21012345", "known-fake-password");

        assertThat(session.cookieManager().getCookieStore().getCookies()).isEmpty();
    }

    @Test
    void treatsMissingPortalSessionAsSystemUnavailableWithoutLeakingCredentials() throws Exception {
        startServer(Scenario.NO_PORTAL_SESSION);
        SejongSsoClient client = client(Duration.ofSeconds(1));

        assertThatThrownBy(() -> client.authenticate("21012345", "sensitive-password"))
            .isInstanceOfSatisfying(SejongAuthenticationException.class, exception -> {
                assertThat(exception.getReason())
                    .isEqualTo(SejongAuthenticationException.Reason.SYSTEM_UNAVAILABLE);
                assertThat(exception.getMessage()).doesNotContain("21012345", "sensitive-password");
            });
    }

    @Test
    void treatsPortalLoginResultFailureAsAuthenticationFailure() throws Exception {
        startServer(Scenario.AUTHENTICATION_FAILED);
        SejongSsoClient client = client(Duration.ofSeconds(1));

        assertThatThrownBy(() -> client.authenticate("21012345", "wrong-password"))
            .isInstanceOfSatisfying(SejongAuthenticationException.class, exception ->
                assertThat(exception.getReason())
                    .isEqualTo(SejongAuthenticationException.Reason.AUTHENTICATION_FAILED));
    }

    @Test
    void treatsUnknownPortalLoginResultAsSystemUnavailable() throws Exception {
        startServer(Scenario.UNKNOWN_LOGIN_RESULT);
        SejongSsoClient client = client(Duration.ofSeconds(1));

        assertThatThrownBy(() -> client.authenticate("21012345", "password"))
            .isInstanceOfSatisfying(SejongAuthenticationException.class, exception ->
                assertThat(exception.getReason())
                    .isEqualTo(SejongAuthenticationException.Reason.SYSTEM_UNAVAILABLE));
        assertThat(portalSsoReached).isFalse();
    }

    @Test
    void neverFollowsCredentialBearingRedirectToAnotherHost() throws Exception {
        startForeignServer();
        startServer(Scenario.FOREIGN_REDIRECT);
        SejongSsoClient client = client(Duration.ofSeconds(1));

        assertThatThrownBy(() -> client.authenticate("21012345", "sensitive-password"))
            .isInstanceOfSatisfying(SejongAuthenticationException.class, exception ->
                assertThat(exception.getReason())
                    .isEqualTo(SejongAuthenticationException.Reason.SYSTEM_UNAVAILABLE));
        assertThat(foreignRequestCount).hasValue(0);
    }

    @Test
    void acceptsPortal302WithSessionCookieWithoutFollowingCredentialRedirect() throws Exception {
        startServer(Scenario.PORTAL_REDIRECT_WITH_SESSION);
        SejongSsoClient client = client(Duration.ofSeconds(1));

        SejongUserProfile profile = client.authenticate("21012345", "portal-password");

        assertThat(profile.studentId()).isEqualTo("21012345");
        assertThat(portalRedirectTargetRequests).hasValue(0);
    }

    @Test
    void neverRepostsCredentialsFor307PortalRedirect() throws Exception {
        startServer(Scenario.PORTAL_307_REDIRECT_WITH_SESSION);
        SejongSsoClient client = client(Duration.ofSeconds(1));

        SejongUserProfile profile = client.authenticate("21012345", "sensitive-password");

        assertThat(profile.studentId()).isEqualTo("21012345");
        assertThat(portalRedirectTargetRequests).hasValue(0);
    }

    @Test
    void followsAllowedRedirectsForNonCredentialGetSteps() throws Exception {
        startServer(Scenario.ALLOWED_GET_REDIRECTS);
        SejongSsoClient client = client(Duration.ofSeconds(1));

        SejongUserProfile profile = client.authenticate("21012345", "password");

        assertThat(profile.studentId()).isEqualTo("21012345");
        assertThat(loginPageRedirectReached).isTrue();
        assertThat(portalSsoRedirectReached).isTrue();
        assertThat(ssoRedirectReached).isTrue();
    }

    @Test
    void doesNotBufferLargeChunkedBodiesFromIntermediateSsoSteps() throws Exception {
        startServer(Scenario.OVERSIZED_INTERMEDIATE_SSO_RESPONSES);
        SejongSsoClient client = client(Duration.ofSeconds(1));

        SejongUserProfile profile = client.authenticate("21012345", "password");

        assertThat(profile.studentId()).isEqualTo("21012345");
    }

    @Test
    void rejectsEmptyPortalSessionCookie() throws Exception {
        startServer(Scenario.EMPTY_PORTAL_SESSION);
        SejongSsoClient client = client(Duration.ofSeconds(1));

        assertThatThrownBy(() -> client.authenticate("21012345", "password"))
            .isInstanceOfSatisfying(SejongAuthenticationException.class, exception ->
                assertThat(exception.getReason())
                    .isEqualTo(SejongAuthenticationException.Reason.SYSTEM_UNAVAILABLE));
    }

    @Test
    void refusesSsoRedirectsOutsideConfiguredSchoolOrigins() throws Exception {
        startServer(Scenario.CROSS_ORIGIN_SSO_REDIRECT);
        SejongSsoClient client = client(Duration.ofSeconds(1));

        assertThatThrownBy(() -> client.authenticate("21012345", "sensitive-password"))
            .isInstanceOfSatisfying(SejongAuthenticationException.class, exception -> {
                assertThat(exception.getReason())
                    .isEqualTo(SejongAuthenticationException.Reason.SYSTEM_UNAVAILABLE);
                assertThat(exception.getMessage()).doesNotContain("sensitive-password");
            });
    }

    @Test
    void treatsUnclassifiedPortalForbiddenResponseAsSystemUnavailable() throws Exception {
        startServer(Scenario.PORTAL_FORBIDDEN);
        SejongSsoClient client = client(Duration.ofSeconds(1));

        assertThatThrownBy(() -> client.authenticate("21012345", "password"))
            .isInstanceOfSatisfying(SejongAuthenticationException.class, exception ->
                assertThat(exception.getReason())
                    .isEqualTo(SejongAuthenticationException.Reason.SYSTEM_UNAVAILABLE));
    }

    @Test
    void treatsMissingIntegratedUserNumberAsInvalidResponse() throws Exception {
        startServer(Scenario.MISSING_USER_ID);
        SejongSsoClient client = client(Duration.ofSeconds(1));

        assertThatThrownBy(() -> client.authenticate("21012345", "password"))
            .isInstanceOfSatisfying(SejongAuthenticationException.class, exception ->
                assertThat(exception.getReason())
                    .isEqualTo(SejongAuthenticationException.Reason.RESPONSE_INVALID));
    }

    @Test
    void treatsMalformedUserInfoAsInvalidResponse() throws Exception {
        startServer(Scenario.MALFORMED_USER_INFO);
        SejongSsoClient client = client(Duration.ofSeconds(1));

        assertThatThrownBy(() -> client.authenticate("21012345", "password"))
            .isInstanceOfSatisfying(SejongAuthenticationException.class, exception ->
                assertThat(exception.getReason())
                    .isEqualTo(SejongAuthenticationException.Reason.RESPONSE_INVALID));
    }

    @Test
    void rejectsUserInfoResponseLargerThan64KiB() throws Exception {
        startServer(Scenario.OVERSIZED_USER_INFO);
        SejongSsoClient client = client(Duration.ofSeconds(1));

        assertThatThrownBy(() -> client.authenticate("21012345", "password"))
            .isInstanceOfSatisfying(SejongAuthenticationException.class, exception ->
                assertThat(exception.getReason())
                    .isEqualTo(SejongAuthenticationException.Reason.RESPONSE_INVALID));
    }

    @Test
    void convertsTimeoutToSystemUnavailable() throws Exception {
        startServer(Scenario.SLOW_PORTAL);
        SejongSsoClient client = client(Duration.ofMillis(50));

        assertThatThrownBy(() -> client.authenticate("21012345", "password"))
            .isInstanceOfSatisfying(SejongAuthenticationException.class, exception ->
                assertThat(exception.getReason())
                    .isEqualTo(SejongAuthenticationException.Reason.SYSTEM_UNAVAILABLE));
    }

    private SejongSsoClient client(Duration requestTimeout) {
        SejongClientProperties properties = properties(requestTimeout);
        return new SejongSsoClient(
            new SejongHttpClientFactory(properties),
            properties,
            new SejongUserInfoParser(new ObjectMapper())
        );
    }

    private SejongClientProperties properties(Duration requestTimeout) {
        String baseUrl = "http://localhost:" + server.getAddress().getPort();
        return new SejongClientProperties(
            URI.create(baseUrl + "/portal"),
            URI.create(baseUrl + "/login-page"),
            "portal.sejong.ac.kr/comm/member/user/ssoLoginProc.do",
            URI.create(baseUrl + "/portal-sso"),
            URI.create(baseUrl + "/sso"),
            URI.create(baseUrl + "/user-info"),
            Duration.ofSeconds(1),
            requestTimeout
        );
    }

    private void startServer(Scenario scenario) throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/login-page", exchange -> handlePortalLoginPage(exchange, scenario));
        server.createContext("/login-page-final", exchange -> {
            loginPageRedirectReached = true;
            respond(exchange, 200, "login page");
        });
        server.createContext("/portal", exchange -> handlePortal(exchange, scenario));
        server.createContext("/portal-sso", exchange -> handlePortalSso(exchange, scenario));
        server.createContext("/portal-sso-final", exchange -> {
            portalSsoRedirectReached = true;
            respond(exchange, 200, "portal sso");
        });
        server.createContext("/sso", exchange -> handleSso(exchange, scenario));
        server.createContext("/sso-final", exchange -> {
            ssoRedirectReached = true;
            respond(exchange, 200, "sso");
        });
        server.createContext("/user-info", exchange -> handleUserInfo(exchange, scenario));
        server.createContext("/portal-redirect-target", exchange -> {
            portalRedirectTargetRequests.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            respond(exchange, 500, "portal credentials must not be reposted");
        });
        server.start();
    }

    private void handlePortalLoginPage(HttpExchange exchange, Scenario scenario) throws IOException {
        exchange.getResponseHeaders().add("Set-Cookie", "WMONID=portal-monitor; Path=/");
        exchange.getResponseHeaders().add("Set-Cookie", "PO_JSESSIONID=portal-session; Path=/; HttpOnly");
        if (scenario == Scenario.ALLOWED_GET_REDIRECTS) {
            exchange.getResponseHeaders().add("Location", "/login-page-final");
            respond(exchange, 302, "redirect");
            return;
        }
        respond(exchange, 200, "login page");
    }

    private void handlePortal(HttpExchange exchange, Scenario scenario) throws IOException {
        portalRequestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        portalRequestReferer = exchange.getRequestHeaders().getFirst("Referer");
        portalRequestCookies = exchange.getRequestHeaders().getFirst("Cookie");
        portalRequestUserAgent = exchange.getRequestHeaders().getFirst("User-Agent");
        portalRequestOrigin = exchange.getRequestHeaders().getFirst("Origin");

        if (scenario == Scenario.SLOW_PORTAL) {
            try {
                Thread.sleep(200);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
        if (scenario == Scenario.AUTHENTICATION_FAILED) {
            respond(exchange, 200, "var result = 'erridpwd';");
            return;
        }
        if (scenario == Scenario.UNKNOWN_LOGIN_RESULT) {
            respond(exchange, 200, "var result = 'maintenance';");
            return;
        }
        if (scenario == Scenario.FOREIGN_REDIRECT) {
            exchange.getResponseHeaders().add(
                "Location",
                "http://localhost:" + foreignServer.getAddress().getPort() + "/capture"
            );
            respond(exchange, 307, "redirect");
            return;
        }
        if (scenario == Scenario.PORTAL_REDIRECT_WITH_SESSION
            || scenario == Scenario.PORTAL_307_REDIRECT_WITH_SESSION) {
            exchange.getResponseHeaders().add("Set-Cookie", "SSOTOKEN=portal-session; Path=/; HttpOnly");
            exchange.getResponseHeaders().add("Location", "/portal-redirect-target");
            int status = scenario == Scenario.PORTAL_REDIRECT_WITH_SESSION ? 302 : 307;
            respond(exchange, status, "redirect");
            return;
        }
        if (scenario == Scenario.PORTAL_FORBIDDEN) {
            respond(exchange, 403, "access denied");
            return;
        }
        respond(exchange, 200, "var result = 'OK';");
    }

    private void startForeignServer() throws IOException {
        foreignServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        foreignServer.createContext("/capture", exchange -> {
            foreignRequestCount.incrementAndGet();
            respond(exchange, 200, "captured");
        });
        foreignServer.start();
    }

    private void handlePortalSso(HttpExchange exchange, Scenario scenario) throws IOException {
        portalSsoReached = true;
        String cookies = exchange.getRequestHeaders().getFirst("Cookie");
        if (cookies == null || !cookies.contains("PO_JSESSIONID=portal-session")) {
            respond(exchange, 401, "missing portal login session");
            return;
        }
        if (scenario != Scenario.NO_PORTAL_SESSION) {
            String value = scenario == Scenario.EMPTY_PORTAL_SESSION ? "" : "portal-session";
            exchange.getResponseHeaders().add("Set-Cookie", "SSOTOKEN=" + value + "; Path=/; HttpOnly");
        }
        if (scenario == Scenario.ALLOWED_GET_REDIRECTS) {
            exchange.getResponseHeaders().add("Location", "/portal-sso-final");
            respond(exchange, 302, "redirect");
            return;
        }
        if (scenario == Scenario.OVERSIZED_INTERMEDIATE_SSO_RESPONSES) {
            respondChunked(exchange, 200, "x".repeat(64 * 1024 + 1));
            return;
        }
        respond(exchange, 200, "portal sso");
    }

    private void handleSso(HttpExchange exchange, Scenario scenario) throws IOException {
        String cookies = exchange.getRequestHeaders().getFirst("Cookie");
        if (cookies == null || !cookies.contains("SSOTOKEN=portal-session")) {
            respond(exchange, 401, "missing portal session");
            return;
        }
        if (scenario == Scenario.CROSS_ORIGIN_SSO_REDIRECT) {
            exchange.getResponseHeaders().add("Location", "http://127.0.0.1:1/session-sink");
            respond(exchange, 307, "redirect");
            return;
        }
        exchange.getResponseHeaders().add("Set-Cookie", "JSESSIONID=sjpt-session; Path=/; HttpOnly");
        if (scenario == Scenario.ALLOWED_GET_REDIRECTS) {
            exchange.getResponseHeaders().add("Location", "/sso-final");
            respond(exchange, 303, "redirect");
            return;
        }
        if (scenario == Scenario.OVERSIZED_INTERMEDIATE_SSO_RESPONSES) {
            respondChunked(exchange, 200, "x".repeat(64 * 1024 + 1));
            return;
        }
        respond(exchange, 200, "sso");
    }

    private void handleUserInfo(HttpExchange exchange, Scenario scenario) throws IOException {
        String cookies = exchange.getRequestHeaders().getFirst("Cookie");
        if (cookies == null
            || !cookies.contains("SSOTOKEN=portal-session")
            || !cookies.contains("JSESSIONID=sjpt-session")) {
            respond(exchange, 401, "missing session");
            return;
        }
        if (scenario == Scenario.MALFORMED_USER_INFO) {
            respond(exchange, 200, "not-json");
            return;
        }
        if (scenario == Scenario.OVERSIZED_USER_INFO) {
            respond(exchange, 200, "x".repeat(64 * 1024 + 1));
            return;
        }
        String userIdField = scenario == Scenario.MISSING_USER_ID
            ? ""
            : "\"INTG_USR_NO\":\"21012345\",";
        respond(exchange, 200, """
            {
              "dm_UserInfo": {%s "INTG_KOR_NM":"홍길동", "RUNNING_SEJONG":"RUNNING"},
              "dm_UserInfoGam": {"LOGIN_DT":"20260818120000", "DEPT_NM":"컴퓨터공학과"},
              "dm_UserInfoSch": {"ORGN_CLSF_CD":"STUDENT"}
            }
            """.formatted(userIdField));
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private void respondChunked(HttpExchange exchange, int status, String body) throws IOException {
        exchange.sendResponseHeaders(status, 0);
        try (var output = exchange.getResponseBody()) {
            output.write(body.getBytes(StandardCharsets.UTF_8));
        }
    }

    private String portalFormValue(String expectedName) {
        for (String entry : portalRequestBody.split("&")) {
            String[] nameAndValue = entry.split("=", 2);
            String name = URLDecoder.decode(nameAndValue[0], StandardCharsets.UTF_8);
            if (expectedName.equals(name)) {
                return nameAndValue.length == 2
                    ? URLDecoder.decode(nameAndValue[1], StandardCharsets.UTF_8)
                    : "";
            }
        }
        return null;
    }

    private enum Scenario {
        SUCCESS,
        AUTHENTICATION_FAILED,
        UNKNOWN_LOGIN_RESULT,
        FOREIGN_REDIRECT,
        PORTAL_REDIRECT_WITH_SESSION,
        PORTAL_307_REDIRECT_WITH_SESSION,
        ALLOWED_GET_REDIRECTS,
        OVERSIZED_INTERMEDIATE_SSO_RESPONSES,
        PORTAL_FORBIDDEN,
        NO_PORTAL_SESSION,
        EMPTY_PORTAL_SESSION,
        CROSS_ORIGIN_SSO_REDIRECT,
        MISSING_USER_ID,
        MALFORMED_USER_INFO,
        OVERSIZED_USER_INFO,
        SLOW_PORTAL
    }
}

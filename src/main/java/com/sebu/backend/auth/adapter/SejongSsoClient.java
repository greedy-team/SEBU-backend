package com.sebu.backend.auth.adapter;

import com.sebu.backend.auth.config.SejongClientProperties;
import com.sebu.backend.auth.port.SejongAuthenticationException;
import com.sebu.backend.auth.port.SejongAuthenticator;
import com.sebu.backend.auth.port.SejongUserProfile;
import lombok.RequiredArgsConstructor;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class SejongSsoClient implements SejongAuthenticator {
    private static final String PORTAL_SESSION_COOKIE = "SSOTOKEN";
    private static final String SJPT_SESSION_COOKIE = "JSESSIONID";
    private static final int MAX_RESPONSE_BYTES = 64 * 1024;
    private static final int MAX_REDIRECTS = 5;
    private static final Set<Integer> REDIRECT_STATUS_CODES = Set.of(301, 302, 303, 307, 308);
    private static final Pattern PORTAL_LOGIN_RESULT_PATTERN = Pattern.compile(
        "\\bvar\\s+result\\s*=\\s*['\"]([^'\"]+)['\"]",
        Pattern.CASE_INSENSITIVE
    );
    private static final Set<String> PORTAL_REJECTION_RESULTS = Set.of(
        "erridpwd",
        "error",
        "pwdneedchg",
        "invaliddt",
        "invalid"
    );
    private static final String BROWSER_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36";
    private static final String HTML_ACCEPT =
        "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8";
    private static final String ACCEPT_LANGUAGE = "ko-KR,ko;q=0.9,en;q=0.8";
    private static final MediaType JSON = MediaType.get("application/json; charset=UTF-8");

    private final SejongHttpClientFactory httpClientFactory;
    private final SejongClientProperties properties;
    private final SejongUserInfoParser userInfoParser;

    @Override
    public SejongUserProfile authenticate(String studentId, String password) {
        SejongHttpClientFactory.Session session = httpClientFactory.create();
        try {
            ClientResponse portalPageResponse = sendFollowingAllowedGetRedirects(
                session,
                portalLoginPageRequest(),
                "portal-login-page"
            );
            requireSuccessful("portal-login-page", portalPageResponse.statusCode());

            ClientResponse portalResponse = send(
                session,
                portalLoginRequest(studentId, password),
                "portal-login"
            );
            requirePortalAuthentication(portalResponse, session);

            ClientResponse portalSsoResponse = sendFollowingAllowedGetRedirects(
                session,
                portalSsoLoginRequest(),
                "portal-sso-login"
            );
            requireSuccessful("portal-sso-login", portalSsoResponse.statusCode());
            requireCookie(session, PORTAL_SESSION_COOKIE, "portal-session-cookie");

            ClientResponse ssoResponse = sendFollowingAllowedGetRedirects(
                session,
                ssoLoginRequest(),
                "sso-login"
            );
            requireSuccessful("sso-login", ssoResponse.statusCode());
            requireCookie(session, SJPT_SESSION_COOKIE, "sso-session-cookie");

            ClientResponse userInfoResponse = send(session, userInfoRequest(), "user-info");
            requireSuccessful("user-info", userInfoResponse.statusCode());
            return userInfoParser.parse(userInfoResponse.body());
        } finally {
            session.cookieManager().getCookieStore().removeAll();
        }
    }

    private Request portalLoginPageRequest() {
        return browserRequestBuilder(properties.portalLoginPageUrl())
            .header("Accept", HTML_ACCEPT)
            .header("Sec-Fetch-Dest", "document")
            .header("Sec-Fetch-Mode", "navigate")
            .header("Sec-Fetch-Site", "none")
            .header("Sec-Fetch-User", "?1")
            .header("Upgrade-Insecure-Requests", "1")
            .get()
            .build();
    }

    private Request portalLoginRequest(String studentId, String password) {
        FormBody form = new FormBody.Builder()
            .add("mainLogin", "Y")
            .add("rtUrl", properties.portalReturnUrl())
            .add("id", studentId.trim())
            .add("password", password)
            .build();
        return browserRequestBuilder(properties.portalLoginUrl())
            .header("Accept", HTML_ACCEPT)
            .header("Origin", origin(properties.portalLoginUrl()))
            .header("Referer", properties.portalLoginPageUrl().toString())
            .header("Sec-Fetch-Dest", "document")
            .header("Sec-Fetch-Mode", "navigate")
            .header("Sec-Fetch-Site", "same-origin")
            .header("Sec-Fetch-User", "?1")
            .header("Upgrade-Insecure-Requests", "1")
            .post(form)
            .build();
    }

    private Request portalSsoLoginRequest() {
        return browserRequestBuilder(properties.portalSsoLoginUrl())
            .header("Accept", HTML_ACCEPT)
            .header("Referer", properties.portalLoginUrl().toString())
            .header("Sec-Fetch-Dest", "document")
            .header("Sec-Fetch-Mode", "navigate")
            .header("Sec-Fetch-Site", "same-origin")
            .get()
            .build();
    }

    private Request ssoLoginRequest() {
        return browserRequestBuilder(properties.ssoLoginUrl())
            .header("Accept", HTML_ACCEPT)
            .header("Referer", origin(properties.portalLoginUrl()) + "/")
            .get()
            .build();
    }

    private Request userInfoRequest() {
        URI requestUri = URI.create(
            properties.userInfoUrl() + "?addParam=" + encodedEmptyExecutionContext()
        );
        String sjptOrigin = origin(properties.ssoLoginUrl());
        return browserRequestBuilder(requestUri)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("Origin", sjptOrigin)
            .header("Referer", sjptOrigin + "/")
            .header("submissionid", "mf___subMainUserInfoInit")
            .post(RequestBody.create("", JSON))
            .build();
    }

    private ClientResponse sendFollowingAllowedGetRedirects(
        SejongHttpClientFactory.Session session,
        Request initialRequest,
        String stage
    ) {
        Request request = initialRequest;
        for (int redirectCount = 0; redirectCount <= MAX_REDIRECTS; redirectCount++) {
            ClientResponse response = sendWithoutBody(session, request, stage);
            if (!isRedirect(response.statusCode())) {
                return response;
            }
            if (redirectCount == MAX_REDIRECTS) {
                rejectRedirect(stage, response.statusCode(), "limit-exceeded");
            }

            HttpUrl redirectUrl = resolveRedirectUrl(response.requestUrl(), response.location());
            if (!isAllowedRedirect(response.requestUrl(), redirectUrl)) {
                rejectRedirect(stage, response.statusCode(), "destination-not-allowed");
            }
            request = redirectedGetRequest(request, redirectUrl);
        }
        throw SejongAuthenticationException.systemUnavailable();
    }

    private ClientResponse send(
        SejongHttpClientFactory.Session session,
        Request request,
        String stage
    ) {
        return send(session, request, stage, true);
    }

    private ClientResponse sendWithoutBody(
        SejongHttpClientFactory.Session session,
        Request request,
        String stage
    ) {
        return send(session, request, stage, false);
    }

    private ClientResponse send(
        SejongHttpClientFactory.Session session,
        Request request,
        String stage,
        boolean includeBody
    ) {
        try (Response response = session.httpClient().newCall(request).execute()) {
            return new ClientResponse(
                response.code(),
                includeBody ? readLimitedBody(response.body()) : "",
                response.header("Location"),
                request.url()
            );
        } catch (SejongAuthenticationException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw SejongAuthenticationException.systemUnavailable(exception)
                .atStage(stage, SejongAuthenticationException.FailureKind.IO_FAILURE);
        }
    }

    private String readLimitedBody(ResponseBody responseBody) throws IOException {
        if (responseBody == null) {
            return "";
        }
        if (responseBody.contentLength() > MAX_RESPONSE_BYTES) {
            throw SejongAuthenticationException.responseInvalid();
        }
        byte[] body = responseBody.byteStream().readNBytes(MAX_RESPONSE_BYTES + 1);
        if (body.length > MAX_RESPONSE_BYTES) {
            throw SejongAuthenticationException.responseInvalid();
        }
        return new String(body, StandardCharsets.UTF_8);
    }

    private void requirePortalAuthentication(
        ClientResponse response,
        SejongHttpClientFactory.Session session
    ) {
        String portalLoginResult = portalLoginResult(response.body());
        if (isPortalAuthenticationFailure(response.statusCode(), portalLoginResult)) {
            throw SejongAuthenticationException.authenticationFailed();
        }
        if (portalLoginResult != null && !"OK".equalsIgnoreCase(portalLoginResult)) {
            throw SejongAuthenticationException.systemUnavailable();
        }
        if (isSuccessful(response.statusCode())) {
            return;
        }
        if (isRedirect(response.statusCode()) && hasCookie(session, PORTAL_SESSION_COOKIE)) {
            return;
        }
        throw SejongAuthenticationException.systemUnavailable();
    }

    private boolean isPortalAuthenticationFailure(int statusCode, String portalLoginResult) {
        if (statusCode == 401) {
            return true;
        }
        boolean resultCanDescribeAuthentication = statusCode == 403 || isSuccessful(statusCode);
        return resultCanDescribeAuthentication
            && portalLoginResult != null
            && PORTAL_REJECTION_RESULTS.contains(portalLoginResult.toLowerCase(Locale.ROOT));
    }

    private String portalLoginResult(String responseBody) {
        Matcher resultMatcher = PORTAL_LOGIN_RESULT_PATTERN.matcher(responseBody);
        return resultMatcher.find() ? resultMatcher.group(1) : null;
    }

    private void requireSuccessful(String stage, int statusCode) {
        if (!isSuccessful(statusCode)) {
            throw SejongAuthenticationException.systemUnavailable()
                .atStage(stage, SejongAuthenticationException.FailureKind.HTTP_STATUS);
        }
    }

    private void requireCookie(
        SejongHttpClientFactory.Session session,
        String expectedName,
        String stage
    ) {
        if (!hasCookie(session, expectedName)) {
            throw SejongAuthenticationException.systemUnavailable()
                .atStage(stage, SejongAuthenticationException.FailureKind.COOKIE_MISSING);
        }
    }

    private boolean hasCookie(SejongHttpClientFactory.Session session, String expectedName) {
        return session.cookieManager().getCookieStore().getCookies().stream()
            .anyMatch(cookie -> expectedName.equalsIgnoreCase(cookie.getName())
                && cookie.getValue() != null
                && !cookie.getValue().isBlank());
    }

    private HttpUrl resolveRedirectUrl(HttpUrl requestUrl, String location) {
        if (location == null || location.isBlank()) {
            return null;
        }
        HttpUrl resolved = requestUrl.resolve(location);
        return resolved == null ? null : resolved.newBuilder().fragment(null).build();
    }

    private boolean isAllowedRedirect(HttpUrl requestUrl, HttpUrl redirectUrl) {
        if (redirectUrl == null || redirectUrl.username().length() > 0 || redirectUrl.password().length() > 0) {
            return false;
        }
        if (requestUrl.isHttps() && !redirectUrl.isHttps()) {
            return false;
        }
        return allowedRedirectOrigins().contains(normalizedOrigin(redirectUrl));
    }

    private Set<String> allowedRedirectOrigins() {
        Set<String> origins = new HashSet<>();
        origins.add(normalizedOrigin(properties.portalLoginUrl()));
        origins.add(normalizedOrigin(properties.portalLoginPageUrl()));
        origins.add(normalizedOrigin(properties.portalSsoLoginUrl()));
        origins.add(normalizedOrigin(properties.ssoLoginUrl()));
        origins.add(normalizedOrigin(properties.userInfoUrl()));
        return origins;
    }

    private String normalizedOrigin(URI uri) {
        int port = uri.getPort() >= 0
            ? uri.getPort()
            : "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
        return uri.getScheme().toLowerCase(Locale.ROOT)
            + "://"
            + uri.getHost().toLowerCase(Locale.ROOT)
            + ":"
            + port;
    }

    private String normalizedOrigin(HttpUrl url) {
        return url.scheme() + "://" + url.host() + ":" + url.port();
    }

    private Request redirectedGetRequest(Request previousRequest, HttpUrl redirectUrl) {
        return previousRequest.newBuilder()
            .url(redirectUrl)
            .removeHeader("Content-Type")
            .removeHeader("Content-Length")
            .get()
            .build();
    }

    private void rejectRedirect(String stage, int status, String reason) {
        throw SejongAuthenticationException.systemUnavailable()
            .atStage(stage, SejongAuthenticationException.FailureKind.REDIRECT_BLOCKED);
    }

    private boolean isSuccessful(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }

    private boolean isRedirect(int statusCode) {
        return REDIRECT_STATUS_CODES.contains(statusCode);
    }

    private String encodedEmptyExecutionContext() {
        String json = "{\"_runIntgUsrNo\":\"\",\"_runPgLoginDt\":\"\",\"_runningSejong\":\"\"}";
        String urlEncoded = URLEncoder.encode(json, StandardCharsets.UTF_8);
        return Base64.getEncoder().encodeToString(urlEncoded.getBytes(StandardCharsets.UTF_8));
    }

    private Request.Builder browserRequestBuilder(URI uri) {
        return new Request.Builder()
            .url(uri.toString())
            .header("User-Agent", BROWSER_USER_AGENT)
            .header("Accept-Language", ACCEPT_LANGUAGE);
    }

    private String origin(URI uri) {
        return uri.getScheme() + "://" + uri.getAuthority();
    }

    private record ClientResponse(
        int statusCode,
        String body,
        String location,
        HttpUrl requestUrl
    ) {
    }
}

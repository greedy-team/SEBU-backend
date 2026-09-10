package com.sebu.backend.global.auth;

import com.sebu.backend.auth.controller.AuthCookieFactory;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.resource.BearerTokenErrors;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;

import java.util.Set;

public final class CookieAccessTokenResolver implements BearerTokenResolver {
    private static final Set<String> ANONYMOUS_AUTH_POSTS = Set.of(
        "/api/v1/auth/sejong/login", "/api/v1/auth/refresh", "/api/v1/auth/logout");

    @Override
    public String resolve(HttpServletRequest request) {
        String path = request.getServletPath();
        if (path.isEmpty()) {
            path = request.getRequestURI().substring(request.getContextPath().length());
        }
        if (("POST".equals(request.getMethod()) && ANONYMOUS_AUTH_POSTS.contains(path))
            || ("GET".equals(request.getMethod()) && "/api/v1/auth/csrf".equals(path))) {
            return null; // An expired access cookie must not prevent refresh, login or logout.
        }
        Cookie[] cookies = request.getCookies();
        String token = null;
        boolean found = false;
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (AuthCookieFactory.ACCESS_COOKIE.equals(cookie.getName())) {
                    if (found || cookie.getValue().length() > 2048) {
                        throw new OAuth2AuthenticationException(BearerTokenErrors.invalidRequest("Invalid access cookie"));
                    }
                    found = true;
                    token = cookie.getValue();
                }
            }
        }
        return token == null || token.isBlank() ? null : token;
    }
}

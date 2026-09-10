package com.sebu.backend.auth.controller;

import com.sebu.backend.auth.config.AuthCookieProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class AuthCookieFactory {
    public static final String ACCESS_COOKIE = "access_token";
    public static final String REFRESH_COOKIE = "refresh_token";
    private static final String ACCESS_PATH = "/api/v1";
    private static final String REFRESH_PATH = "/api/v1/auth";

    private final AuthCookieProperties cookieProperties;

    public ResponseCookie access(String value, long expiresIn) {
        return base(ACCESS_COOKIE, ACCESS_PATH, value).maxAge(Duration.ofSeconds(expiresIn)).build();
    }

    public ResponseCookie refresh(String value, long expiresIn) {
        return base(REFRESH_COOKIE, REFRESH_PATH, value).maxAge(Duration.ofSeconds(expiresIn)).build();
    }

    public ResponseCookie deleteAccess() {
        return access("", 0);
    }

    public ResponseCookie deleteRefresh() {
        return refresh("", 0);
    }

    private ResponseCookie.ResponseCookieBuilder base(String name, String path, String value) {
        return ResponseCookie.from(name, value)
            .httpOnly(true)
            .secure(cookieProperties.secure())
            .sameSite("Lax")
            .path(path);
    }
}

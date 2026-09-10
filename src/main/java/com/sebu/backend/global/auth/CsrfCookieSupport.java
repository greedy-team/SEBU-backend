package com.sebu.backend.global.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CsrfCookieSupport {
    private final CookieCsrfTokenRepository repository;

    public void renew(HttpServletRequest request, HttpServletResponse response) {
        // Custom JSON login/logout controllers do not invoke Spring's form-login strategies.
        repository.saveToken(repository.generateToken(request), request, response);
    }
}

package com.sebu.backend.global.auth;

import com.sebu.backend.auth.config.AuthCsrfProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.util.Collections;

// Installed only in the Security filter chain, not registered as a servlet filter bean.
final class TrustedOriginFilter extends OncePerRequestFilter {
    private final AuthCsrfProperties properties;
    private final ApiAccessDeniedHandler deniedHandler;

    TrustedOriginFilter(AuthCsrfProperties properties, ApiAccessDeniedHandler deniedHandler) {
        this.properties = properties;
        this.deniedHandler = deniedHandler;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !path.startsWith("/api/v1/") || !CsrfFilter.DEFAULT_CSRF_MATCHER.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        String origin = sourceOrigin(request);
        if (origin == null || !properties.allowedOrigins().contains(origin)) {
            deniedHandler.handle(request, response, new CsrfException("Untrusted request origin"));
            return;
        }
        chain.doFilter(request, response);
    }

    private String sourceOrigin(HttpServletRequest request) {
        var origins = Collections.list(request.getHeaders("Origin"));
        if (!origins.isEmpty()) {
            return origins.size() == 1 ? origins.getFirst() : null;
        }
        var referers = Collections.list(request.getHeaders("Referer"));
        if (referers.size() != 1) {
            return null;
        }
        try {
            URI uri = URI.create(referers.getFirst());
            if (uri.getHost() == null || uri.getUserInfo() != null) {
                return null;
            }
            return uri.getScheme() + "://" + uri.getRawAuthority();
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}

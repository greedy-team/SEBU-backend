package com.sebu.backend.global.ratelimit.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class RateLimitKeyResolver {

    public ResolvedKeys resolve(HttpServletRequest request) {
        return authenticatedSubject()
            .map(subject -> new ResolvedKeys(List.of("USER:" + subject), true))
            .orElseGet(() -> anonymousKeys(request));
    }

    private Optional<String> authenticatedSubject() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        if (!(authentication.getPrincipal() instanceof Jwt jwt)) {
            return Optional.empty();
        }
        return Optional.ofNullable(jwt.getSubject());
    }

    private ResolvedKeys anonymousKeys(HttpServletRequest request) {
        String ipKey = "IP:" + request.getRemoteAddr();
        HttpSession session = request.getSession(false);
        if (session == null) {
            return new ResolvedKeys(List.of(ipKey), false);
        }
        return new ResolvedKeys(List.of(ipKey, "SESSION:" + session.getId()), false);
    }

    public record ResolvedKeys(List<String> values, boolean authenticated) {
    }
}

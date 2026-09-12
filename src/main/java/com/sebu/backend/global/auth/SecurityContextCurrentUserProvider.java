package com.sebu.backend.global.auth;

import com.sebu.backend.user.repository.AppUserRepository;
import com.sebu.backend.auth.token.JwtAccessTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class SecurityContextCurrentUserProvider implements CurrentUserProvider {

    private final AppUserRepository appUserRepository;

    @Override
    public Optional<Long> currentUserId() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }

        if (!(authentication.getPrincipal() instanceof Jwt jwt)) {
            return Optional.empty();
        }

        Long userId;

        try {
            userId = Long.parseLong(jwt.getSubject());
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }

        Long tokenAuthVersion = authVersion(jwt);
        if (tokenAuthVersion == null) {
            return Optional.empty();
        }

        return appUserRepository.findById(userId)
                .filter(user -> !user.isDeleted() && user.getAuthVersion() == tokenAuthVersion)
                .map(user -> user.getId());
    }

    private Long authVersion(Jwt jwt) {
        Object claim = jwt.getClaim(JwtAccessTokenService.AUTH_VERSION_CLAIM);
        if (claim == null) {
            return 0L;
        }
        if (!(claim instanceof Number number)) {
            return null;
        }
        long value = number.longValue();
        return value >= 0 ? value : null;
    }
}

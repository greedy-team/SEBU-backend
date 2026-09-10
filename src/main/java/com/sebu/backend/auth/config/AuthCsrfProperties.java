package com.sebu.backend.auth.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.util.Set;

@Validated
@ConfigurationProperties(prefix = "app.auth.csrf")
public record AuthCsrfProperties(@NotEmpty Set<String> allowedOrigins) {
    @AssertTrue(message = "allowed origins must be exact HTTP(S) origins without paths or wildcards")
    public boolean isOriginConfigurationValid() {
        return allowedOrigins != null && allowedOrigins.stream().allMatch(origin -> {
            if (origin == null) {
                return false;
            }
            try {
                URI uri = URI.create(origin);
                return ("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))
                    && uri.getHost() != null && uri.getUserInfo() == null
                    && (uri.getPath() == null || uri.getPath().isEmpty())
                    && uri.getQuery() == null && uri.getFragment() == null;
            } catch (IllegalArgumentException exception) {
                return false;
            }
        });
    }
}

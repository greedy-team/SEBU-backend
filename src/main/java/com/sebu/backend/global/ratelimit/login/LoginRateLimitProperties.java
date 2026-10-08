package com.sebu.backend.global.ratelimit.login;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.rate-limit.login")
public record LoginRateLimitProperties(
    @Min(1) int maxRequests,
    @NotNull Duration window
) {
    @AssertTrue(message = "login rate limit window must be positive")
    public boolean isWindowPositive() {
        return window != null && !window.isZero() && !window.isNegative();
    }
}

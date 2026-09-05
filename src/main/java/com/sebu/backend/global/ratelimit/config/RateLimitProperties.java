package com.sebu.backend.global.ratelimit.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(
    @Min(1) int maxRequests,
    @Min(1) int searchMaxRequests,
    @Min(1) int bookmarkMaxRequests,
    @Min(1) int contentWriteMaxRequests,
    @Min(1) int anonymousIpMultiplier,
    @NotNull Duration window
) {
    @AssertTrue(message = "rate limit window must be positive")
    public boolean isWindowPositive() {
        return window != null && !window.isZero() && !window.isNegative();
    }
}

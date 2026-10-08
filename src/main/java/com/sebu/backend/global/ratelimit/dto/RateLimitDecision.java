package com.sebu.backend.global.ratelimit.dto;

public record RateLimitDecision(boolean allowed, long retryAfterSeconds) {
    public static RateLimitDecision permit() {
        return new RateLimitDecision(true, 0);
    }

    public static RateLimitDecision reject(long retryAfterSeconds) {
        return new RateLimitDecision(false, Math.max(1, retryAfterSeconds));
    }
}

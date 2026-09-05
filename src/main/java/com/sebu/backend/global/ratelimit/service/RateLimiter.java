package com.sebu.backend.global.ratelimit.service;

import com.sebu.backend.global.ratelimit.dto.RateLimitDecision;

import java.util.List;

public interface RateLimiter {
    default RateLimitDecision tryAcquire(String key, RateLimitPolicy policy) {
        return tryAcquireAll(List.of(new RateLimitEntry(key, policy)));
    }

    RateLimitDecision tryAcquireAll(List<RateLimitEntry> entries);

    record RateLimitEntry(String key, RateLimitPolicy policy) {
    }
}

package com.sebu.backend.global.ratelimit.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.sebu.backend.global.ratelimit.dto.RateLimitDecision;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class InMemoryRateLimiter implements RateLimiter {
    private static final long MAXIMUM_KEYS = 100_000;

    private final Clock clock;
    private final Cache<String, TokenBucket> buckets;

    public InMemoryRateLimiter() {
        this(Clock.systemUTC());
    }

    InMemoryRateLimiter(Clock clock) {
        this.clock = clock;
        this.buckets = Caffeine.newBuilder()
            .maximumSize(MAXIMUM_KEYS)
            .expireAfterAccess(Duration.ofHours(1))
            .build();
    }

    @Override
    public synchronized RateLimitDecision tryAcquireAll(List<RateLimitEntry> entries) {
        Instant now = clock.instant();
        List<PendingAcquisition> pending = new ArrayList<>(entries.size());

        for (RateLimitEntry entry : entries) {
            RateLimitPolicy policy = entry.policy();
            String bucketKey = policy.name() + ":" + entry.key();
            TokenBucket bucket = buckets.get(
                bucketKey,
                ignored -> new TokenBucket(policy.capacity(), now)
            );
            bucket.refill(now, policy);
            pending.add(new PendingAcquisition(bucket, policy));
        }

        for (PendingAcquisition acquisition : pending) {
            RateLimitDecision decision = acquisition.bucket().decision(acquisition.policy());
            if (!decision.allowed()) {
                return decision;
            }
        }
        pending.forEach(acquisition -> acquisition.bucket().consume());
        return RateLimitDecision.permit();
    }

    private record PendingAcquisition(TokenBucket bucket, RateLimitPolicy policy) {
    }

    private static final class TokenBucket {
        private double tokens;
        private Instant lastRefillAt;

        private TokenBucket(int capacity, Instant createdAt) {
            tokens = capacity;
            lastRefillAt = createdAt;
        }

        private void refill(Instant now, RateLimitPolicy policy) {
            double elapsedNanos = Duration.between(lastRefillAt, now).toNanos();
            double refillPerNano = (double) policy.capacity() / policy.refillPeriod().toNanos();
            tokens = Math.min(policy.capacity(), tokens + elapsedNanos * refillPerNano);
            lastRefillAt = now;
        }

        private RateLimitDecision decision(RateLimitPolicy policy) {
            if (tokens >= 1) {
                return RateLimitDecision.permit();
            }

            double refillPerNano = (double) policy.capacity() / policy.refillPeriod().toNanos();
            long retryAfterNanos = (long) Math.ceil((1 - tokens) / refillPerNano);
            long retryAfterSeconds = (retryAfterNanos + 999_999_999L) / 1_000_000_000L;
            return RateLimitDecision.reject(retryAfterSeconds);
        }

        private void consume() {
            tokens -= 1;
        }
    }
}

package com.sebu.backend.global.ratelimit.service;

import com.sebu.backend.global.ratelimit.dto.RateLimitDecision;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryRateLimiterTest {
    @Test
    void rejectsRequestsOverCapacityAndRefillsTokensOverTime() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-09T00:00:00Z"));
        InMemoryRateLimiter limiter = new InMemoryRateLimiter(clock);
        RateLimitPolicy policy = new RateLimitPolicy("TEST", 2, Duration.ofMinutes(1));

        assertThat(limiter.tryAcquire("IP:127.0.0.1", policy).allowed()).isTrue();
        assertThat(limiter.tryAcquire("IP:127.0.0.1", policy).allowed()).isTrue();
        RateLimitDecision rejected = limiter.tryAcquire("IP:127.0.0.1", policy);
        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.retryAfterSeconds()).isEqualTo(30);

        clock.advance(Duration.ofSeconds(30));
        assertThat(limiter.tryAcquire("IP:127.0.0.1", policy).allowed()).isTrue();
    }

    @Test
    void maintainsIndependentLimitsForDifferentKeys() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-09T00:00:00Z"));
        InMemoryRateLimiter limiter = new InMemoryRateLimiter(clock);
        RateLimitPolicy policy = new RateLimitPolicy("TEST", 1, Duration.ofMinutes(1));

        assertThat(limiter.tryAcquire("USER:1", policy).allowed()).isTrue();
        assertThat(limiter.tryAcquire("USER:1", policy).allowed()).isFalse();
        assertThat(limiter.tryAcquire("USER:2", policy).allowed()).isTrue();
    }

    @Test
    void doesNotConsumeAnyTokenWhenOneOfMultipleKeysIsRejected() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-09T00:00:00Z"));
        InMemoryRateLimiter limiter = new InMemoryRateLimiter(clock);
        RateLimitPolicy sessionPolicy = new RateLimitPolicy("TEST", 1, Duration.ofMinutes(1));
        RateLimitPolicy ipPolicy = new RateLimitPolicy("TEST_IP", 1, Duration.ofMinutes(1));
        limiter.tryAcquire("IP:127.0.0.1", ipPolicy);

        RateLimitDecision rejected = limiter.tryAcquireAll(List.of(
            new RateLimiter.RateLimitEntry("SESSION:1", sessionPolicy),
            new RateLimiter.RateLimitEntry("IP:127.0.0.1", ipPolicy)
        ));

        assertThat(rejected.allowed()).isFalse();
        assertThat(limiter.tryAcquire("SESSION:1", sessionPolicy).allowed()).isTrue();
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}

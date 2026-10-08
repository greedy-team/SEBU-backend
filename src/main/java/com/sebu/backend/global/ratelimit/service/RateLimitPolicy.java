package com.sebu.backend.global.ratelimit.service;

import java.time.Duration;

public record RateLimitPolicy(String name, int capacity, Duration refillPeriod) {
}

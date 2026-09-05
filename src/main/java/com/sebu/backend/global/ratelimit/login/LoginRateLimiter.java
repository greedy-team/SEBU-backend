package com.sebu.backend.global.ratelimit.login;

import com.sebu.backend.global.ratelimit.dto.RateLimitDecision;
import com.sebu.backend.global.ratelimit.service.InMemoryRateLimiter;
import com.sebu.backend.global.ratelimit.service.RateLimitPolicy;
import org.springframework.stereotype.Component;

@Component
public class LoginRateLimiter {
    private final InMemoryRateLimiter delegate;

    private final RateLimitPolicy policy;

    public LoginRateLimiter(LoginRateLimitProperties properties) {
        this.delegate = new InMemoryRateLimiter();
        this.policy = new RateLimitPolicy("LOGIN", properties.maxRequests(), properties.window());
    }

    public RateLimitDecision tryAcquire(String clientIp) {
        return delegate.tryAcquire("IP:" + clientIp, policy);
    }
}

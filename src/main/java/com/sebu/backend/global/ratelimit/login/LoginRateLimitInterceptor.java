package com.sebu.backend.global.ratelimit.login;

import com.sebu.backend.global.logging.OperationalLog;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sebu.backend.global.ratelimit.dto.RateLimitDecision;
import com.sebu.backend.global.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class LoginRateLimitInterceptor implements HandlerInterceptor {
    private static final String ERROR_CODE = "LOGIN_RATE_LIMITED";
    private static final String ERROR_MESSAGE = "로그인 요청이 너무 많습니다. 잠시 후 다시 시도해주세요.";

    private final LoginRateLimiter rateLimiter;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        if (!HttpMethod.POST.matches(request.getMethod())) {
            return true;
        }

        RateLimitDecision decision = rateLimiter.tryAcquire(request.getRemoteAddr());
        if (decision.allowed()) {
            return true;
        }

        OperationalLog.rateLimited(request, "LOGIN", decision.retryAfterSeconds());
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(decision.retryAfterSeconds()));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ApiResponse.failure(ERROR_CODE, ERROR_MESSAGE));
        return false;
    }
}

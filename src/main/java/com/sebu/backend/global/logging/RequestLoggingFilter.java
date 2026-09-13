package com.sebu.backend.global.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestLoggingFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String traceId = UUID.randomUUID().toString().replace("-", "");
        MDC.put(RequestTrace.MDC_KEY, traceId);
        response.setHeader(RequestTrace.HEADER, traceId);
        long started = System.nanoTime();
        boolean failed = false;
        try {
            chain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException exception) {
            failed = true;
            OperationalLog.unexpected(exception);
            throw exception;
        } finally {
            try {
                if (request.getRequestURI().startsWith("/api/") || failed || response.getStatus() >= 400) {
                    OperationalLog.request(request, failed ? 500 : response.getStatus(),
                        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
                }
            } finally {
                MDC.remove(RequestTrace.MDC_KEY);
            }
        }
    }
}

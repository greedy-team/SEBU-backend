package com.sebu.backend.global.ratelimit.web;

import com.sebu.backend.global.ratelimit.config.RateLimitProperties;
import com.sebu.backend.global.ratelimit.service.RateLimitPolicy;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RateLimitRequestPolicyResolver {
    private final RateLimitProperties properties;

    public RateLimitPolicy resolve(HttpServletRequest request) {
        if (isBookmarkMutation(request)) {
            return policy("BOOKMARK", properties.bookmarkMaxRequests());
        }
        if (isContentWrite(request)) {
            return policy("CONTENT_WRITE", properties.contentWriteMaxRequests());
        }
        if (isSearch(request)) {
            return policy("SEARCH", properties.searchMaxRequests());
        }
        return policy("GENERAL", properties.maxRequests());
    }

    public RateLimitPolicy anonymousIpPolicy(RateLimitPolicy primary) {
        return new RateLimitPolicy(
            primary.name() + "_ANONYMOUS_IP",
            Math.multiplyExact(primary.capacity(), properties.anonymousIpMultiplier()),
            primary.refillPeriod()
        );
    }

    private RateLimitPolicy policy(String name, int capacity) {
        return new RateLimitPolicy(name, capacity, properties.window());
    }

    private boolean isSearch(HttpServletRequest request) {
        if (!HttpMethod.GET.matches(request.getMethod())) {
            return false;
        }
        String uri = request.getRequestURI();
        return "/api/v1/laboratories".equals(uri)
            || ("/api/v1/posts".equals(uri) && hasText(request.getParameter("keyword")));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean isBookmarkMutation(HttpServletRequest request) {
        if (!(HttpMethod.PUT.matches(request.getMethod()) || HttpMethod.DELETE.matches(request.getMethod()))) {
            return false;
        }
        String uri = request.getRequestURI();
        return uri.matches("/api/v1/laboratories/[^/]+/bookmark")
            || uri.matches("/api/v1/posts/[^/]+/bookmarks");
    }

    private boolean isContentWrite(HttpServletRequest request) {
        String method = request.getMethod();
        String uri = request.getRequestURI();

        boolean postWrite = (HttpMethod.POST.matches(method) && "/api/v1/posts".equals(uri))
            || ((HttpMethod.PUT.matches(method) || HttpMethod.DELETE.matches(method))
            && uri.matches("/api/v1/posts/[^/]+"));
        boolean commentWrite = (HttpMethod.POST.matches(method)
            && uri.matches("/api/v1/posts/[^/]+/comments"))
            || ((HttpMethod.PATCH.matches(method) || HttpMethod.DELETE.matches(method))
            && uri.matches("/api/v1/posts/[^/]+/comments/[^/]+"));

        return postWrite || commentWrite;
    }
}

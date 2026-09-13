package com.sebu.backend.global.logging;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.springframework.web.servlet.HandlerMapping;

import java.util.LinkedHashMap;
import java.util.Map;

/** 허용된 필드만 받는다. 요청/DTO/사용자 정보나 예외 메시지를 인자로 추가하지 않는다. */
public final class OperationalLog {
    private static final Logger LOG = LoggerFactory.getLogger("sebu.events");

    public enum Auth { LOGIN, REFRESH, LOGOUT, RECOVERY }
    public enum Outcome { SUCCESS, REJECTED, FAILURE, RECOVERY_REQUIRED, COMPLETED }

    private OperationalLog() { }

    public static void request(HttpServletRequest request, int status, long durationMs) {
        emit(Level.INFO, "http.request.completed", Map.of(
            "method", method(request), "route", route(request),
            "status", status, "durationMs", durationMs), null);
    }

    public static void auth(Auth auth, Outcome outcome, String reason, Boolean newUser,
                            Level level, Throwable failure) {
        auth(auth, outcome, reason, newUser, level, failure, null);
    }

    public static void auth(Auth auth, Outcome outcome, String reason, Boolean newUser,
                            Level level, Throwable failure, String stage) {
        var fields = new LinkedHashMap<String, Object>();
        fields.put("outcome", outcome.name());
        fields.put("reason", code(reason));
        if (newUser != null) fields.put("newUser", newUser);
        if (stage != null) fields.put("stage", code(stage));
        emit(level, "auth." + auth.name().toLowerCase(java.util.Locale.ROOT) + ".completed", fields, failure);
    }

    public static void accessRejected(String reason) {
        emit(Level.INFO, "security.access.rejected", Map.of("reason", code(reason)), null);
    }

    public static void rateLimited(HttpServletRequest request, String policy, long retrySeconds) {
        emit(Level.INFO, "security.rate_limit.rejected", Map.of(
            "route", route(request), "limitKind", code(policy), "retrySeconds", retrySeconds), null);
    }

    public static void departmentUnresolved(int matchCount) {
        emit(Level.WARN, "auth.department.unresolved", Map.of("matchCount", matchCount), null);
    }

    public static void unexpected(Throwable failure) {
        emit(Level.ERROR, "api.unexpected_failure", Map.of("errorCode", "INTERNAL_SERVER_ERROR"), failure);
    }

    static void batch(String jobName, String runId, long count, Long failed, long durationMs,
                      boolean limitReached, Throwable failure) {
        boolean unsuccessful = failure != null || failed != null && failed > 0;
        var fields = new LinkedHashMap<String, Object>();
        fields.put("jobName", code(jobName));
        fields.put("runId", runId);
        fields.put("processedCount", count);
        if (failed != null) fields.put("failedCount", failed);
        fields.put("durationMs", durationMs);
        fields.put("limitReached", limitReached);
        emit(unsuccessful ? Level.ERROR : Level.INFO,
            unsuccessful ? "batch.failed" : "batch.completed", fields, failure);
    }

    public static void moderationUnavailable(Throwable failure) {
        emit(Level.ERROR, "external.request.failed",
            Map.of("dependency", "CONTENT_MODERATION", "reason", "UNAVAILABLE"), failure);
    }

    public static String route(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        // 리소스 catch-all, 알 수 없는 경로와 원문 URI는 출력하지 않는다.
        if (!(pattern instanceof String route) || !route.startsWith("/api/")
            || route.contains("**") || route.length() > 200) return "UNMATCHED";
        return route;
    }

    private static String method(HttpServletRequest request) {
        return switch (request.getMethod()) {
            case "GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS", "TRACE" -> request.getMethod();
            default -> "OTHER";
        };
    }

    // 이 검사는 길이/형식 방어다. 호출부에서는 반드시 고정 코드만 전달한다.
    private static String code(String value) {
        return value != null && value.matches("[A-Z][A-Z0-9_]{0,79}") ? value : "UNSPECIFIED";
    }

    private static void emit(Level level, String event, Map<String, Object> fields, Throwable failure) {
        LOG.atLevel(level).addKeyValue("eventData", new EventData(event, fields))
            .setCause(failure).log(event);
    }

    static final class EventData {
        final String event;
        final Map<String, Object> fields;
        private EventData(String event, Map<String, Object> fields) {
            this.event = event;
            this.fields = Map.copyOf(fields);
        }
    }
}

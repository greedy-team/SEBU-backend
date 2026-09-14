package com.sebu.backend.global.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.LayoutBase;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** 자유 형식 메시지, 임의 MDC, 예외 메시지와 suppressed 내용은 직렬화하지 않는다. */
public class SafeJsonLayout extends LayoutBase<ILoggingEvent> {
    private final ObjectMapper mapper = new ObjectMapper();
    private String environment = "local";

    public void setEnvironment(String environment) {
        this.environment = environment != null && environment.matches("[a-zA-Z0-9,_-]{1,64}")
            ? environment : "unknown";
    }

    @Override
    public String doLayout(ILoggingEvent event) {
        var json = base(event.getTimeStamp(), event.getLevel().toString());
        OperationalLog.EventData data = null;
        if ("sebu.events".equals(event.getLoggerName()) && event.getKeyValuePairs() != null) {
            for (var pair : event.getKeyValuePairs()) {
                if (pair.value instanceof OperationalLog.EventData candidate) data = candidate;
            }
        }
        json.put("event", data == null ? "framework.diagnostic" : data.event);
        if (data != null) json.putAll(data.fields);
        else {
            json.put("loggerGroup", loggerGroup(event.getLoggerName()));
            json.put("reason", "UNSTRUCTURED_MESSAGE_OMITTED");
        }
        String traceId = event.getMDCPropertyMap().get(RequestTrace.MDC_KEY);
        if (traceId != null && traceId.matches("[a-f0-9]{32}")) json.put("traceId", traceId);
        if (event.getThrowableProxy() != null) {
            json.put("exceptionType", event.getThrowableProxy().getClassName());
            if (data != null) json.put("exception", safeStack(event.getThrowableProxy()));
        }
        return encode(json);
    }

    String dropped(long count) {
        var json = base(System.currentTimeMillis(), "WARN");
        json.put("event", "logging.events.dropped");
        json.put("count", count);
        json.put("reason", "OUTPUT_QUEUE_FULL");
        return encode(json);
    }

    private String loggerGroup(String logger) {
        if (logger == null) return "OTHER";
        if (logger.startsWith("org.hibernate.")) return "HIBERNATE";
        if (logger.startsWith("org.springframework.")) return "SPRING";
        if (logger.startsWith("com.zaxxer.hikari.")) return "CONNECTION_POOL";
        if (logger.startsWith("org.apache.")) return "APACHE";
        if (logger.startsWith("com.sebu.backend.")) return "APPLICATION";
        return "OTHER";
    }

    private LinkedHashMap<String, Object> base(long timestamp, String level) {
        var json = new LinkedHashMap<String, Object>();
        json.put("timestamp", Instant.ofEpochMilli(timestamp).toString());
        json.put("level", level);
        json.put("service", "sebu-backend");
        json.put("environment", environment);
        return json;
    }

    private Map<String, Object> safeStack(IThrowableProxy throwable) {
        var causes = new ArrayList<Map<String, Object>>();
        var seen = Collections.newSetFromMap(new IdentityHashMap<IThrowableProxy, Boolean>());
        boolean truncated = false;
        while (throwable != null && causes.size() < 4 && seen.add(throwable)) {
            var frames = new ArrayList<String>();
            var stack = throwable.getStackTraceElementProxyArray();
            if (stack != null) {
                for (int i = 0; i < Math.min(stack.length, 8); i++) {
                    var frame = stack[i].getStackTraceElement();
                    frames.add(frame.getClassName() + "." + frame.getMethodName() + ":" + frame.getLineNumber());
                }
                truncated |= stack.length > 8;
            }
            truncated |= throwable.getSuppressed() != null && throwable.getSuppressed().length > 0;
            causes.add(Map.of("type", throwable.getClassName(), "frames", frames));
            throwable = throwable.getCause();
        }
        return Map.of("causes", causes, "truncated", truncated || throwable != null);
    }

    private String encode(Map<String, Object> json) {
        try {
            return mapper.writeValueAsString(json) + "\n";
        } catch (JsonProcessingException ignored) {
            // 직렬화 실패를 같은 로거로 다시 기록하지 않는다.
            return "{\"level\":\"ERROR\",\"event\":\"logging.encoding.failed\"}\n";
        }
    }
}

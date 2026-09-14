package com.sebu.backend.global.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/** MDC가 정리되기 전에 실제 출력 레이아웃을 실행한다. */
final class LogCapture implements AutoCloseable {
    private final Logger logger = (Logger) LoggerFactory.getLogger("sebu.events");
    private final SafeJsonLayout layout = new SafeJsonLayout();
    private final ObjectMapper mapper = new ObjectMapper();
    final List<String> lines = new ArrayList<>();
    private final AppenderBase<ILoggingEvent> appender = new AppenderBase<>() {
        @Override protected void append(ILoggingEvent event) { lines.add(layout.doLayout(event)); }
    };

    LogCapture() {
        appender.start();
        logger.addAppender(appender);
    }

    List<JsonNode> events(String name) {
        return lines.stream().map(line -> {
            try { return mapper.readTree(line); }
            catch (Exception exception) { throw new AssertionError(exception); }
        }).filter(json -> name.equals(json.path("event").asText())).toList();
    }

    @Override public void close() { logger.detachAppender(appender); appender.stop(); }
}

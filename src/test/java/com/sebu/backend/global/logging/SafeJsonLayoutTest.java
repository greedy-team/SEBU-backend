package com.sebu.backend.global.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SafeJsonLayoutTest {
    private static final String SECRET = "FAKE-password-token-21012345-user@example.test\r\nforged";

    @AfterEach void cleanMdc() { MDC.clear(); }

    @Test
    void rendersOnlySafeExceptionTypesAndLocationsIncludingNestedFailures() throws Exception {
        var cause = new IllegalArgumentException(SECRET);
        var exception = new IllegalStateException(SECRET, cause);
        exception.addSuppressed(new RuntimeException(SECRET));
        MDC.put("traceId", "a".repeat(32));
        MDC.put("studentId", SECRET);
        try (var capture = new LogCapture()) {
            OperationalLog.unexpected(exception);
            assertThat(capture.lines).hasSize(1);
            String line = capture.lines.getFirst();
            assertThat(line).doesNotContain("FAKE-password", "21012345", "user@example.test", "forged", "studentId");
            assertThat(line.lines()).hasSize(1);
            var json = new ObjectMapper().readTree(line);
            assertThat(json.path("traceId").asText()).isEqualTo("a".repeat(32));
            assertThat(json.path("exception").path("causes").size()).isEqualTo(2);
            assertThat(json.path("exception").path("truncated").asBoolean()).isTrue();
        }
    }

    @Test
    void libraryMessagesArgumentsMdcAndThrowableMessagesAreNotOutput() throws Exception {
        var context = new LoggerContext();
        try {
            var event = new LoggingEvent("test", context.getLogger("org.hibernate.SQL"),
                Level.ERROR, "SQL {} " + SECRET, new RuntimeException(SECRET), new Object[]{SECRET});
            event.setMDCPropertyMap(Map.of("traceId", SECRET, "Authorization", SECRET));
            var layout = new SafeJsonLayout();
            String line = layout.doLayout(event);
            assertThat(line).doesNotContain("FAKE-password", "21012345", "Authorization", "forged", "traceId");
            assertThat(new ObjectMapper().readTree(line).path("event").asText()).isEqualTo("framework.diagnostic");
        } finally { context.stop(); }
    }

    @Test
    void boundsDeepExceptionChainsAndBatchFailureIsLoggedOnlyOnce() {
        RuntimeException failure = new RuntimeException(SECRET);
        for (int i = 0; i < 20; i++) failure = new RuntimeException(SECRET, failure);
        try (var capture = new LogCapture()) {
            BatchLog batch = BatchLog.start("TEST_JOB");
            batch.processed(7);
            batch.failed(2, failure);
            batch.failed(failure);
            batch.complete(false);
            var events = capture.events("batch.failed");
            assertThat(events).hasSize(1);
            assertThat(events.getFirst().path("processedCount").asInt()).isEqualTo(7);
            assertThat(events.getFirst().path("failedCount").asInt()).isEqualTo(2);
            assertThat(events.getFirst().path("exception").path("causes").size()).isEqualTo(4);
            assertThat(events.getFirst().path("exception").path("truncated").asBoolean()).isTrue();
        }
    }
}

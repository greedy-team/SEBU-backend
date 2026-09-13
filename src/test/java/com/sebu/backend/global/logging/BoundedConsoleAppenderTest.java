package com.sebu.backend.global.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class BoundedConsoleAppenderTest {
    @Test
    void blockedOutputDoesNotBlockProducerAndReportsDropsAfterRecovery() throws Exception {
        var blocked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var lines = new CopyOnWriteArrayList<String>();
        var appender = new BoundedConsoleAppender() {
            @Override protected void writeLine(String line) {
                blocked.countDown();
                try { release.await(); }
                catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
                lines.add(line);
            }
        };
        var context = new LoggerContext();
        appender.setContext(context);
        appender.setLayout(new SafeJsonLayout());
        appender.start();
        try {
            var event = new LoggingEvent("test", context.getLogger("test"), Level.WARN,
                "FAKE_SECRET", null, null);
            event.setMDCPropertyMap(Map.of());
            appender.doAppend(event);
            assertThat(blocked.await(2, TimeUnit.SECONDS)).isTrue();
            assertTimeoutPreemptively(Duration.ofSeconds(3), () -> {
                for (int i = 0; i < 600; i++) appender.doAppend(event);
            });
        } finally {
            release.countDown();
            appender.stop();
            context.stop();
        }
        assertThat(lines).anyMatch(line -> line.contains("logging.events.dropped") && line.contains("\"count\":88"));
        assertThat(lines).noneMatch(line -> line.contains("FAKE_SECRET"));
    }
}

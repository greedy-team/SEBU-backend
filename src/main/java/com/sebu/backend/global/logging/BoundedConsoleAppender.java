package com.sebu.backend.global.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.UnsynchronizedAppenderBase;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** 표준 출력 정체가 요청 스레드를 막지 않도록 이미 정제한 한 줄만 유한 큐에 넣는다. */
public class BoundedConsoleAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {
    private final ArrayBlockingQueue<String> queue = new ArrayBlockingQueue<>(512);
    private final AtomicLong dropped = new AtomicLong();
    private SafeJsonLayout layout;
    private volatile boolean draining;
    private Thread worker;

    public void setLayout(SafeJsonLayout layout) { this.layout = layout; }

    @Override
    public void start() {
        if (layout == null) { addError("SafeJsonLayout is required"); return; }
        draining = true;
        worker = new Thread(this::drain, "sebu-log-output");
        worker.setDaemon(true);
        super.start();
        worker.start();
    }

    @Override
    protected void append(ILoggingEvent event) {
        if (!queue.offer(layout.doLayout(event))) dropped.incrementAndGet();
    }

    private void drain() {
        while (draining || !queue.isEmpty()) {
            try {
                String line = queue.poll(200, TimeUnit.MILLISECONDS);
                long lost = dropped.getAndSet(0);
                if (lost > 0) writeLine(layout.dropped(lost));
                if (line != null) writeLine(line);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    protected void writeLine(String line) { System.out.print(line); }

    @Override
    public void stop() {
        super.stop();
        draining = false;
        if (worker != null) {
            try { worker.join(2_000); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        }
    }
}

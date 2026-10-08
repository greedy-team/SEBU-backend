package com.sebu.backend.global.logging;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** 작업 단위 결과만 기록한다. 처리 대상 ID나 원본 자료는 저장하지 않는다. */
public final class BatchLog {
    private final String jobName;
    private final String runId = UUID.randomUUID().toString().replace("-", "");
    private final long started = System.nanoTime();
    private long processed;
    private boolean finished;

    private BatchLog(String jobName) { this.jobName = jobName; }
    public static BatchLog start(String jobName) { return new BatchLog(jobName); }
    public void processed(long count) { processed += count; }
    public void complete(boolean limitReached) { finish(0L, limitReached, null); }
    public void failed(long count, Throwable failure) { finish(count, false, failure); }
    public void failed(Throwable failure) { finish(null, false, failure); }

    private void finish(Long failed, boolean limitReached, Throwable failure) {
        if (finished) return;
        finished = true;
        OperationalLog.batch(jobName, runId, processed, failed,
            TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started), limitReached, failure);
    }
}

package com.sebu.backend.global.logging;

import org.slf4j.MDC;

/** 서버가 생성한 요청 ID만 응답과 로그에 공유한다. */
public final class RequestTrace {
    public static final String HEADER = "X-Request-ID";
    static final String MDC_KEY = "traceId";

    private RequestTrace() { }

    public static String currentId() {
        String value = MDC.get(MDC_KEY);
        return value != null && value.matches("[a-f0-9]{32}") ? value : null;
    }
}

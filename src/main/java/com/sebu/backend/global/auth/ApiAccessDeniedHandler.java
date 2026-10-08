package com.sebu.backend.global.auth;

import com.sebu.backend.global.logging.OperationalLog;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sebu.backend.global.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class ApiAccessDeniedHandler implements AccessDeniedHandler {
    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException exception) throws IOException {
        boolean csrf = exception instanceof CsrfException;
        OperationalLog.accessRejected(csrf ? "CSRF_TOKEN_INVALID" : "FORBIDDEN");
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Cache-Control", "no-store");
        objectMapper.writeValue(response.getWriter(), ApiResponse.failure(
            csrf ? "CSRF_TOKEN_INVALID" : "FORBIDDEN",
            csrf ? "요청 출처 또는 CSRF 토큰을 확인해주세요." : "요청한 작업을 수행할 권한이 없습니다."));
    }
}

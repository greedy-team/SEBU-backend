package com.sebu.backend.global.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sebu.backend.auth.exception.AccessTokenInvalidException;
import com.sebu.backend.global.response.ApiResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {
    private static final String EXPIRED_ERROR_CODE = "access_token_expired";

    private final ObjectMapper objectMapper;

    @Override
    public void commence(
        HttpServletRequest request,
        HttpServletResponse response,
        AuthenticationException authenticationException
    ) throws IOException, ServletException {
        boolean expired = containsExpiredTokenError(authenticationException);
        String code = expired ? "ACCESS_TOKEN_EXPIRED" : AccessTokenInvalidException.CODE;
        String message = expired ? "인증 토큰이 만료되었습니다." : AccessTokenInvalidException.USER_MESSAGE;

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), ApiResponse.failure(code, message));
    }

    private boolean containsExpiredTokenError(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof JwtValidationException validationException
                && validationException.getErrors().stream()
                    .anyMatch(error -> EXPIRED_ERROR_CODE.equals(error.getErrorCode()))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}

package com.sebu.backend.auth.controller;

import com.sebu.backend.auth.exception.AccessTokenInvalidException;
import com.sebu.backend.auth.exception.AuthSessionConflictException;
import com.sebu.backend.auth.exception.InvalidGradeException;
import com.sebu.backend.auth.exception.InvalidLoginRequestException;
import com.sebu.backend.auth.exception.RecoveryTokenInvalidException;
import com.sebu.backend.auth.exception.RefreshTokenInvalidException;
import com.sebu.backend.auth.port.SejongAuthenticationException;
import com.sebu.backend.global.response.ApiResponse;
import com.sebu.backend.global.logging.OperationalLog;
import org.slf4j.event.Level;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {AuthController.class, MeController.class})
@RequiredArgsConstructor
public class AuthExceptionHandler {
    private static final String PROFILE_PATH = "/api/v1/me/profile";
    private final AuthCookieFactory cookieFactory;

    @ExceptionHandler(InvalidLoginRequestException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidLoginRequest(InvalidLoginRequestException exception) {
        return invalidLoginRequest();
    }

    private ResponseEntity<ApiResponse<Void>> invalidLoginRequest() {
        OperationalLog.auth(OperationalLog.Auth.LOGIN, OperationalLog.Outcome.REJECTED,
            "INVALID_LOGIN_REQUEST", null, Level.INFO, null);
        return failure(
            HttpStatus.BAD_REQUEST,
            "INVALID_LOGIN_REQUEST",
            "학번 또는 비밀번호 형식을 확인해주세요."
        );
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ApiResponse<Void>> handleInvalidRequestBody(
        Exception exception,
        HttpServletRequest request
    ) {
        if (PROFILE_PATH.equals(request.getRequestURI())) {
            return invalidGrade();
        }
        return invalidLoginRequest();
    }

    @ExceptionHandler(InvalidGradeException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidGrade(InvalidGradeException exception) {
        return invalidGrade();
    }

    private ResponseEntity<ApiResponse<Void>> invalidGrade() {
        return failure(
            HttpStatus.BAD_REQUEST,
            "INVALID_GRADE",
            "학년은 1부터 4까지 입력해주세요."
        );
    }

    @ExceptionHandler(SejongAuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleSejongAuthentication(SejongAuthenticationException exception) {
        boolean expected = exception.getReason() == SejongAuthenticationException.Reason.AUTHENTICATION_FAILED;
        boolean suspicious = exception.getReason() == SejongAuthenticationException.Reason.IDENTITY_MISMATCH
            || exception.getFailureKind() == SejongAuthenticationException.FailureKind.REDIRECT_BLOCKED;
        String reason = exception.getFailureKind() == SejongAuthenticationException.FailureKind.NONE
            ? exception.getReason().name() : exception.getFailureKind().name();
        OperationalLog.auth(OperationalLog.Auth.LOGIN,
            expected || suspicious ? OperationalLog.Outcome.REJECTED : OperationalLog.Outcome.FAILURE,
            reason, null, suspicious ? Level.WARN : expected ? Level.INFO : Level.ERROR,
            expected || suspicious ? null : exception, exception.getStage().name());
        if (exception.getReason() == SejongAuthenticationException.Reason.AUTHENTICATION_FAILED
            || exception.getReason() == SejongAuthenticationException.Reason.IDENTITY_MISMATCH) {
            return failure(
                HttpStatus.UNAUTHORIZED,
                "SEJONG_AUTH_FAILED",
                "학번 또는 비밀번호를 확인해주세요."
            );
        }
        return failure(
            HttpStatus.BAD_GATEWAY,
            "SEJONG_SYSTEM_UNAVAILABLE",
            "세종대학교 시스템에 연결할 수 없습니다. 잠시 후 다시 시도해주세요."
        );
    }

    @ExceptionHandler(RefreshTokenInvalidException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidRefreshToken(RefreshTokenInvalidException exception) {
        OperationalLog.auth(OperationalLog.Auth.REFRESH, OperationalLog.Outcome.REJECTED,
            exception.getReason().name(), null, Level.INFO, null);
        return failure(
            HttpStatus.UNAUTHORIZED,
            "REFRESH_TOKEN_INVALID",
            "로그인이 만료되었습니다. 다시 로그인해주세요."
        );
    }

    @ExceptionHandler(RecoveryTokenInvalidException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidRecoveryToken(RecoveryTokenInvalidException exception) {
        OperationalLog.auth(OperationalLog.Auth.RECOVERY, OperationalLog.Outcome.REJECTED,
            "RECOVERY_TOKEN_INVALID", null, Level.INFO, null);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
            .header(HttpHeaders.SET_COOKIE, cookieFactory.deleteRecovery().toString())
            .body(ApiResponse.failure(
                "RECOVERY_TOKEN_INVALID",
                "유효하지 않거나 만료된 복구 요청입니다. 다시 로그인해주세요."
            ));
    }

    @ExceptionHandler(AuthSessionConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthSessionConflict(AuthSessionConflictException exception) {
        OperationalLog.auth(OperationalLog.Auth.LOGIN, OperationalLog.Outcome.REJECTED,
            "SESSION_CONFLICT", null, Level.INFO, null);
        return failure(
            HttpStatus.CONFLICT,
            "AUTH_SESSION_CONFLICT",
            "사용자 정보가 동시에 변경되었습니다. 다시 로그인해주세요."
        );
    }

    @ExceptionHandler(AccessTokenInvalidException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidAccessToken(AccessTokenInvalidException exception) {
        OperationalLog.accessRejected(AccessTokenInvalidException.CODE);
        return failure(
            HttpStatus.UNAUTHORIZED,
            AccessTokenInvalidException.CODE,
            AccessTokenInvalidException.USER_MESSAGE
        );
    }

    private ResponseEntity<ApiResponse<Void>> failure(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(ApiResponse.failure(code, message));
    }
}

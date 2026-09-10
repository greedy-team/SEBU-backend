package com.sebu.backend.auth.controller;

import com.sebu.backend.auth.dto.LoginResponse;
import com.sebu.backend.auth.dto.LogoutResponse;
import com.sebu.backend.auth.dto.RefreshResponse;
import com.sebu.backend.auth.dto.SejongLoginRequest;
import com.sebu.backend.auth.service.AuthService;
import com.sebu.backend.auth.service.AuthSessionService;
import com.sebu.backend.global.auth.CsrfCookieSupport;
import com.sebu.backend.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "인증", description = "로그인, 계정 복구, 토큰 재발급 및 로그아웃 API")
public class AuthController {
    private final AuthService authService;
    private final AuthSessionService authSessionService;
    private final AuthCookieFactory cookieFactory;
    private final CsrfCookieSupport csrfCookieSupport;

    @Operation(summary = "CSRF 토큰 준비", description = "XSRF-TOKEN 쿠키를 발급합니다. 변경 요청에는 쿠키 값을 X-XSRF-TOKEN 헤더로 전달해야 합니다.")
    @GetMapping("/csrf")
    public ResponseEntity<Void> csrf(CsrfToken token) {
        token.getToken();
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @Operation(summary = "세종대학교 로그인", description = "세종대학교 포털 계정으로 로그인하거나 탈퇴 계정의 복구 절차를 시작합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "로그인 완료 또는 계정 복구 확인 필요",
            useReturnTypeSchema = true
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            ref = "#/components/responses/Unauthorized"
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409",
            ref = "#/components/responses/Conflict"
    )
    @PostMapping("/sejong/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody SejongLoginRequest request,
        HttpServletRequest servletRequest, HttpServletResponse servletResponse) {
        AuthSessionService.LoginOutcome outcome = authService.loginWithSejong(
            request.studentId(),
            request.password()
        );
        csrfCookieSupport.renew(servletRequest, servletResponse);
        if (outcome instanceof AuthSessionService.LoginSession session) {
            return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE,
                    cookieFactory.access(session.accessToken(), session.expiresIn()).toString(),
                    cookieFactory.refresh(session.refreshToken(), session.refreshExpiresIn()).toString(),
                    cookieFactory.deleteRecovery().toString())
                .body(ApiResponse.<LoginResponse>success(LoginResponse.authenticated(session)));
        }
        if (outcome instanceof AuthSessionService.RecoveryChallenge challenge) {
            return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE,
                    cookieFactory.deleteAccess().toString(),
                    cookieFactory.deleteRefresh().toString(),
                    cookieFactory.recovery(challenge.recoveryToken(), challenge.recoveryExpiresIn()).toString())
                .body(ApiResponse.<LoginResponse>success(LoginResponse.recoveryRequired(challenge)));
        }
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .cacheControl(CacheControl.noStore())
            .header(HttpHeaders.SET_COOKIE,
                cookieFactory.deleteAccess().toString(),
                cookieFactory.deleteRefresh().toString(),
                cookieFactory.deleteRecovery().toString())
            .body(ApiResponse.<LoginResponse>failure(
                "ACCOUNT_RECOVERY_COOLDOWN",
                "아직 계정 복구 대기 시간이 지나지 않았습니다. 잠시 후 다시 시도해주세요."
            ));
    }

    @Operation(summary = "계정 복구", description = "일회용 복구 쿠키로 탈퇴 계정을 복구하고 새 로그인 세션을 발급합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "계정 복구 및 새 로그인 세션 발급 성공",
            useReturnTypeSchema = true
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            ref = "#/components/responses/Unauthorized"
    )
    @PostMapping("/recovery")
    @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "recoveryCookie")
    public ResponseEntity<ApiResponse<LoginResponse>> recover(
        @CookieValue(name = AuthCookieFactory.RECOVERY_COOKIE, required = false) String recoveryToken,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        AuthSessionService.LoginSession session = authSessionService.recover(recoveryToken);
        csrfCookieSupport.renew(request, response);
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .header(HttpHeaders.SET_COOKIE,
                cookieFactory.access(session.accessToken(), session.expiresIn()).toString(),
                cookieFactory.refresh(session.refreshToken(), session.refreshExpiresIn()).toString(),
                cookieFactory.deleteRecovery().toString())
            .body(ApiResponse.<LoginResponse>success(LoginResponse.authenticated(session)));
    }

    @Operation(summary = "토큰 재발급", description = "리프레시 토큰 쿠키를 사용해 액세스 토큰과 리프레시 토큰을 재발급합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            ref = "#/components/responses/Unauthorized"
    )
    @PostMapping("/refresh")
    @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "refreshCookie")
    public ResponseEntity<ApiResponse<RefreshResponse>> refresh(
        @CookieValue(name = AuthCookieFactory.REFRESH_COOKIE, required = false) String refreshToken
    ) {
        AuthSessionService.RefreshSession session = authSessionService.refresh(refreshToken);
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .header(HttpHeaders.SET_COOKIE,
                cookieFactory.access(session.accessToken(), session.expiresIn()).toString(),
                cookieFactory.refresh(session.refreshToken(), session.refreshExpiresIn()).toString())
            .body(ApiResponse.success(RefreshResponse.from(session)));
    }

    @Operation(summary = "로그아웃", description = "현재 로그인 묶음의 리프레시 토큰을 폐기하고 인증·복구 쿠키를 삭제합니다.")
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<LogoutResponse>> logout(
        @CookieValue(name = AuthCookieFactory.REFRESH_COOKIE, required = false) String refreshToken,
        HttpServletRequest request, HttpServletResponse response
    ) {
        authSessionService.logout(refreshToken);
        csrfCookieSupport.renew(request, response);
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .header(HttpHeaders.SET_COOKIE,
                cookieFactory.deleteAccess().toString(),
                cookieFactory.deleteRefresh().toString(),
                cookieFactory.deleteRecovery().toString())
            .body(ApiResponse.success(new LogoutResponse("로그아웃되었습니다.")));
    }
}

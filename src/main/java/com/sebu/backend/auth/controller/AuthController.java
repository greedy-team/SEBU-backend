package com.sebu.backend.auth.controller;

import com.sebu.backend.auth.dto.LoginResponse;
import com.sebu.backend.auth.dto.LogoutResponse;
import com.sebu.backend.auth.dto.RefreshResponse;
import com.sebu.backend.auth.dto.SejongLoginRequest;
import com.sebu.backend.auth.service.AuthService;
import com.sebu.backend.auth.service.AuthSessionService;
import com.sebu.backend.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.sebu.backend.global.auth.CsrfCookieSupport;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.http.CacheControl;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "인증", description = "로그인, 토큰 재발급 및 로그아웃 API")
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

    @Operation(summary = "세종대학교 로그인", description = "세종대학교 포털 계정으로 로그인하고 액세스 토큰과 리프레시 토큰을 발급합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            ref = "#/components/responses/Unauthorized"
    )
    @PostMapping("/sejong/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody SejongLoginRequest request,
        HttpServletRequest servletRequest, HttpServletResponse servletResponse) {
        AuthSessionService.LoginSession session = authService.loginWithSejong(
            request.studentId(),
            request.password()
        );
        csrfCookieSupport.renew(servletRequest, servletResponse);
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .header(HttpHeaders.SET_COOKIE,
                cookieFactory.access(session.accessToken(), session.expiresIn()).toString(),
                cookieFactory.refresh(session.refreshToken(), session.refreshExpiresIn()).toString())
            .body(ApiResponse.success(LoginResponse.from(session)));
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

    @Operation(summary = "로그아웃", description = "현재 로그인 묶음의 리프레시 토큰을 폐기하고 두 인증 쿠키를 삭제합니다.")
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<LogoutResponse>> logout(
        @CookieValue(name = AuthCookieFactory.REFRESH_COOKIE, required = false) String refreshToken,
        HttpServletRequest request, HttpServletResponse response
    ) {
        authSessionService.logout(refreshToken);
        csrfCookieSupport.renew(request, response);
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .header(HttpHeaders.SET_COOKIE, cookieFactory.deleteAccess().toString(), cookieFactory.deleteRefresh().toString())
            .body(ApiResponse.success(new LogoutResponse("로그아웃되었습니다.")));
    }
}

package com.sebu.backend.auth.port;

import lombok.Getter;

@Getter
public class SejongAuthenticationException extends RuntimeException {
    private final Reason reason;
    private Stage stage = Stage.UNKNOWN;
    private FailureKind failureKind = FailureKind.NONE;

    public SejongAuthenticationException atStage(String stage, FailureKind kind) {
        this.stage = switch (stage) {
            case "portal-login-page" -> Stage.PORTAL_LOGIN_PAGE;
            case "portal-login" -> Stage.PORTAL_LOGIN;
            case "portal-sso-login" -> Stage.PORTAL_SSO_LOGIN;
            case "sso-login" -> Stage.SSO_LOGIN;
            case "user-info" -> Stage.USER_INFO;
            default -> Stage.UNKNOWN;
        };
        this.failureKind = kind;
        return this;
    }

    public enum Stage { UNKNOWN, PORTAL_LOGIN_PAGE, PORTAL_LOGIN, PORTAL_SSO_LOGIN, SSO_LOGIN, USER_INFO }
    public enum FailureKind { NONE, IO_FAILURE, HTTP_STATUS, COOKIE_MISSING, REDIRECT_BLOCKED }

    private SejongAuthenticationException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public static SejongAuthenticationException authenticationFailed() {
        return new SejongAuthenticationException(
            Reason.AUTHENTICATION_FAILED,
            "SEJONG_AUTHENTICATION_FAILED",
            null
        );
    }

    public static SejongAuthenticationException systemUnavailable() {
        return systemUnavailable(null);
    }

    public static SejongAuthenticationException systemUnavailable(Throwable cause) {
        return new SejongAuthenticationException(
            Reason.SYSTEM_UNAVAILABLE,
            "SEJONG_SYSTEM_UNAVAILABLE",
            cause
        );
    }

    public static SejongAuthenticationException identityMismatch() {
        return new SejongAuthenticationException(
            Reason.IDENTITY_MISMATCH,
            "SEJONG_IDENTITY_MISMATCH",
            null
        );
    }

    public static SejongAuthenticationException responseInvalid() {
        return new SejongAuthenticationException(
            Reason.RESPONSE_INVALID,
            "SEJONG_RESPONSE_INVALID",
            null
        );
    }

    public enum Reason {
        AUTHENTICATION_FAILED,
        SYSTEM_UNAVAILABLE,
        IDENTITY_MISMATCH,
        RESPONSE_INVALID
    }
}

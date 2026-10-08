package com.sebu.backend.auth.exception;

public class AccessTokenInvalidException extends RuntimeException {
    public static final String CODE = "ACCESS_TOKEN_INVALID";
    public static final String USER_MESSAGE = "유효하지 않은 인증 토큰입니다.";

    public AccessTokenInvalidException() {
        super(CODE);
    }
}

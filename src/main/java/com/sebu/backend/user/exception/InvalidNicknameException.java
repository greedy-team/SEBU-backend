package com.sebu.backend.user.exception;

public class InvalidNicknameException extends RuntimeException {
    private final String reason;
    private final String userMessage;

    private InvalidNicknameException(String reason, String userMessage) {
        super(reason);
        this.reason = reason;
        this.userMessage = userMessage;
    }

    public static InvalidNicknameException invalidFormat() {
        return new InvalidNicknameException(
                "INVALID_FORMAT",
                "닉네임에 사용할 수 없는 문자가 포함되어 있습니다."
        );
    }

    public static InvalidNicknameException tooLong(int maxLength) {
        return new InvalidNicknameException(
                "TOO_LONG",
                "닉네임은 " + maxLength + "자 이하로 입력해 주세요."
        );
    }

    public static InvalidNicknameException reservedWord() {
        return new InvalidNicknameException(
                "RESERVED_WORD",
                "사용할 수 없는 닉네임입니다."
        );
    }

    public String reason() {
        return reason;
    }

    public String userMessage() {
        return userMessage;
    }
}

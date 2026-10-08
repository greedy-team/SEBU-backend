package com.sebu.backend.laboratoryreview.exception;

public class InvalidLaboratoryReviewInputException
        extends RuntimeException {

    private final String field;
    private final String reason;
    private final String userMessage;

    private InvalidLaboratoryReviewInputException(
            String field,
            String reason,
            String userMessage
    ) {
        super("INVALID_LABORATORY_REVIEW_INPUT");
        this.field = field;
        this.reason = reason;
        this.userMessage = userMessage;
    }

    public static InvalidLaboratoryReviewInputException invalidTag() {
        return new InvalidLaboratoryReviewInputException(
                "tags",
                "INVALID_TAG",
                "후기 태그 값을 확인해 주세요."
        );
    }

    public static InvalidLaboratoryReviewInputException
    invalidParticipationYear(
            int currentYear
    ) {
        return new InvalidLaboratoryReviewInputException(
                "participationYear",
                "OUT_OF_RANGE",
                "참여 연도는 2000년부터 "
                        + currentYear
                        + "년 사이여야 합니다."
        );
    }

    public static InvalidLaboratoryReviewInputException invalidContent() {
        return new InvalidLaboratoryReviewInputException(
                "content",
                "INVALID_LENGTH",
                "후기 내용은 공백 제거 후 20~2000자로 입력해 주세요."
        );
    }

    public String field() {
        return field;
    }

    public String reason() {
        return reason;
    }

    public String userMessage() {
        return userMessage;
    }
}

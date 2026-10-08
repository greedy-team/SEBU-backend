package com.sebu.backend.community.exception;

public class CommunityContentPolicyViolationException extends RuntimeException {
    private final String field;

    public CommunityContentPolicyViolationException(String field) {
        super("CONTENT_POLICY_VIOLATION");
        this.field = field;
    }

    public String getField() {
        return field;
    }
}

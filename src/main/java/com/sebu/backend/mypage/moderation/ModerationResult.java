package com.sebu.backend.mypage.moderation;

public record ModerationResult(
        boolean allowed,
        String policyVersion,
        String providerVersion
) {
}

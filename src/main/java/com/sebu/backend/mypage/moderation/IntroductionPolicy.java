package com.sebu.backend.mypage.moderation;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class IntroductionPolicy {
    private static final List<String> BLOCKED_PATTERNS = List.of(
            "차단테스트표현",
            "ㅊㅏㄷㅏㄴㅌㅔㅅㅡㅌㅡㅍㅛㅎㅕㄴ"
    );

    private static final List<String> ALLOWED_PATTERNS = List.of(
            "차단테스트표현연구"
    );

    public boolean isAllowed(String normalizedIntroduction) {
        if (normalizedIntroduction == null
                || normalizedIntroduction.isBlank()) {
            return true;
        }

        if (containsAllowedPattern(normalizedIntroduction)) {
            return true;
        }

        return !containsBlockedPattern(normalizedIntroduction);
    }

    private boolean containsBlockedPattern(String introduction) {
        return BLOCKED_PATTERNS.stream()
                .anyMatch(introduction::contains);
    }

    private boolean containsAllowedPattern(String introduction) {
        return ALLOWED_PATTERNS.stream()
                .anyMatch(introduction::contains);
    }


}

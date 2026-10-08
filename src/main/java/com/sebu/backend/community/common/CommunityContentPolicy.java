package com.sebu.backend.community.common;

import com.sebu.backend.community.exception.CommunityContentPolicyViolationException;
import com.sebu.backend.mypage.moderation.IntroductionModerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CommunityContentPolicy {
    private final IntroductionModerator introductionModerator;

    public void validate(String field, String value) {
        if (!introductionModerator.moderate(value).allowed()) {
            throw new CommunityContentPolicyViolationException(field);
        }
    }
}

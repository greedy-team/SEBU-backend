package com.sebu.backend.mypage.moderation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class IntroductionModeratorImpl implements IntroductionModerator {

    private final IntroductionNormalizer introductionNormalizer;
    private final IntroductionPolicy introductionPolicy;

    @Override
    public ModerationResult moderate(String introduction) {
        if (introduction == null || introduction.isBlank()) {
            return new ModerationResult(
                    true,
                    "v1",
                    "internal"
            );
        }

        String normalized =
                introductionNormalizer.normalize(introduction);

        boolean allowed =
                introductionPolicy.isAllowed(normalized);

        return new ModerationResult(
                allowed,
                "v1",
                "internal"
        );
    }
}

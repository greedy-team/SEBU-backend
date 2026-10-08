package com.sebu.backend.college.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CommunityCollegeGroup {
    HUMANITIES_SOCIAL("인문사회대학"),
    BUSINESS_HOSPITALITY("경영경제호텔관광대학"),
    NATURAL_LIFE("자연생명대학"),
    AI_CONVERGENCE("인공지능융합대학"),
    ENGINEERING("공과대학"),
    ARTS_PHYSICAL("예체능대학");

    private final String displayName;
}

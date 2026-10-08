package com.sebu.backend.community.common;

import com.sebu.backend.community.common.dto.CommunityAuthorResponse;
import com.sebu.backend.community.common.dto.CommunityAuthorStatus;
import com.sebu.backend.user.domain.AppUser;
import com.sebu.backend.college.domain.CommunityCollegeGroup;
import org.springframework.stereotype.Component;

@Component
public class CommunityAuthorMapper {
    private static final String ANONYMOUS_NICKNAME = "익명";

    public CommunityAuthorResponse toResponse(AppUser user, CommunityCollegeGroup group) {
        if (user.isDeleted()) {
            return new CommunityAuthorResponse(null, null, CommunityAuthorStatus.WITHDRAW, null);
        }
        return new CommunityAuthorResponse(
                null,
                ANONYMOUS_NICKNAME,
                CommunityAuthorStatus.ACTIVE,
                group == null ? null : group.getDisplayName()
        );
    }

    public String displayNickname(AppUser user) {
        if (user.isDeleted()) {
            return "탈퇴한 사용자";
        }
        return ANONYMOUS_NICKNAME;
    }
}

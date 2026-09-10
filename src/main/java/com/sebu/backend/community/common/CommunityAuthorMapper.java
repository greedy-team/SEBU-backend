package com.sebu.backend.community.common;

import com.sebu.backend.community.common.dto.CommunityAuthorResponse;
import com.sebu.backend.community.common.dto.CommunityAuthorStatus;
import com.sebu.backend.user.domain.AppUser;
import org.springframework.stereotype.Component;

@Component
public class CommunityAuthorMapper {
    private static final String ANONYMOUS_NICKNAME = "익명";

    public CommunityAuthorResponse toResponse(AppUser user) {
        if (user.isDeleted()) {
            return new CommunityAuthorResponse(null, null, CommunityAuthorStatus.WITHDRAW);
        }
        return new CommunityAuthorResponse(
                user.getId(),
                user.getNickname() == null ? ANONYMOUS_NICKNAME : user.getNickname(),
                CommunityAuthorStatus.ACTIVE
        );
    }

    public String displayNickname(AppUser user) {
        if (user.isDeleted()) {
            return "탈퇴한 사용자";
        }
        return user.getNickname() == null ? ANONYMOUS_NICKNAME : user.getNickname();
    }
}

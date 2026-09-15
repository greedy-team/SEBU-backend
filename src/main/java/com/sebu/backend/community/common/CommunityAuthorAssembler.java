package com.sebu.backend.community.common;

import com.sebu.backend.community.common.dto.CommunityAuthorResponse;
import com.sebu.backend.department.service.AcademicAffiliationResolver;
import com.sebu.backend.user.domain.AppUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommunityAuthorAssembler {
    private final AcademicAffiliationResolver affiliationResolver;
    private final CommunityAuthorMapper authorMapper;

    public CommunityAuthorResponse toResponse(AppUser user) {
        return toResponses(List.of(user)).get(user.getId());
    }

    public Map<Long, CommunityAuthorResponse> toResponses(Collection<AppUser> users) {
        var affiliations = affiliationResolver.resolveAll(users.stream()
            .filter(user -> !user.isDeleted())
            .map(AppUser::getSejongDepartmentName).toList());
        Map<Long, CommunityAuthorResponse> responses = new HashMap<>();
        for (AppUser user : users) {
            String name = user.getSejongDepartmentName();
            var affiliation = user.isDeleted() || name == null ? null : affiliations.get(name.trim());
            var college = affiliation == null ? null : affiliation.college();
            responses.put(user.getId(), authorMapper.toResponse(
                user, college == null ? null : college.getCommunityGroup()));
        }
        return responses;
    }
}

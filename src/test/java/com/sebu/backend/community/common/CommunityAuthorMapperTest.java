package com.sebu.backend.community.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sebu.backend.college.domain.CommunityCollegeGroup;
import com.sebu.backend.user.domain.AppUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CommunityAuthorMapperTest {
    private final CommunityAuthorMapper mapper = new CommunityAuthorMapper();
    private final ObjectMapper json = new ObjectMapper();

    @ParameterizedTest
    @EnumSource(CommunityCollegeGroup.class)
    void alwaysHidesUserIdAndNicknameAndOnlyExposesGroup(CommunityCollegeGroup group) {
        AppUser user = mock(AppUser.class);
        var response = mapper.toResponse(user, group);
        var body = json.valueToTree(response);
        assertThat(body.get("id").isNull()).isTrue();
        assertThat(body.get("nickname").asText()).isEqualTo("익명");
        assertThat(body.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(body.get("collegeGroup").asText()).isEqualTo(group.getDisplayName());
        assertThat(body.size()).isEqualTo(4);
        verify(user).isDeleted();
        verifyNoMoreInteractions(user);
    }

    @Test
    void unresolvedAuthorHasExplicitNullIdAndNoGroup() {
        var body = json.valueToTree(mapper.toResponse(mock(AppUser.class), null));
        assertThat(body.has("id")).isTrue();
        assertThat(body.get("id").isNull()).isTrue();
        assertThat(body.get("nickname").asText()).isEqualTo("익명");
        assertThat(body.has("collegeGroup")).isFalse();
    }

    @Test
    void withdrawalMasksGroupEvenIfCallerSuppliesIt() {
        AppUser user = mock(AppUser.class);
        when(user.isDeleted()).thenReturn(true);
        var body = json.valueToTree(mapper.toResponse(user, CommunityCollegeGroup.AI_CONVERGENCE));
        assertThat(body.get("id").isNull()).isTrue();
        assertThat(body.get("nickname").isNull()).isTrue();
        assertThat(body.get("status").asText()).isEqualTo("WITHDRAW");
        assertThat(body.has("collegeGroup")).isFalse();
    }
}

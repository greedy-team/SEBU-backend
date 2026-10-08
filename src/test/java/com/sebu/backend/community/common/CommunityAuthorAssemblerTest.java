package com.sebu.backend.community.common;

import com.sebu.backend.college.domain.College;
import com.sebu.backend.college.domain.CommunityCollegeGroup;
import com.sebu.backend.department.service.AcademicAffiliationResolver;
import com.sebu.backend.user.domain.AppUser;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CommunityAuthorAssemblerTest {
    private final AcademicAffiliationResolver resolver = mock(AcademicAffiliationResolver.class);
    private final CommunityAuthorAssembler assembler =
        new CommunityAuthorAssembler(resolver, new CommunityAuthorMapper());

    @Test
    void existingUnlinkedUserResolvesWithoutLoginAndWithdrawalIsExcluded() {
        AppUser active = AppUser.sejong("21000001", "이름", "무인이동체공학전공", null, LocalDateTime.now());
        ReflectionTestUtils.setField(active, "id", 1L);
        AppUser withdrawn = AppUser.sejong("21000002", "탈퇴이름", "법학전공", null, LocalDateTime.now());
        ReflectionTestUtils.setField(withdrawn, "id", 2L);
        withdrawn.withdraw(LocalDateTime.now());
        College college = mock(College.class);
        when(college.getCommunityGroup()).thenReturn(CommunityCollegeGroup.AI_CONVERGENCE);
        when(resolver.resolveAll(List.of("무인이동체공학전공"))).thenReturn(java.util.Map.of(
            "무인이동체공학전공", new AcademicAffiliationResolver.Affiliation(null, college, 0)));

        var result = assembler.toResponses(List.of(active, withdrawn));

        assertThat(result.get(1L).id()).isNull();
        assertThat(result.get(1L).collegeGroup()).isEqualTo("인공지능융합대학");
        assertThat(result.get(2L).collegeGroup()).isNull();
        assertThat(active.getMajorDepartment()).isNull();
        verify(resolver).resolveAll(List.of("무인이동체공학전공"));
        verifyNoMoreInteractions(resolver);
    }
}

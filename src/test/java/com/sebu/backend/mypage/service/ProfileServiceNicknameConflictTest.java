package com.sebu.backend.mypage.service;

import com.sebu.backend.global.auth.ActiveUserCommandGuard;
import com.sebu.backend.mypage.dto.ProfileUpdateRequest;
import com.sebu.backend.mypage.moderation.IntroductionModerator;
import com.sebu.backend.mypage.moderation.ModerationResult;
import com.sebu.backend.user.domain.AppUser;
import com.sebu.backend.user.domain.AcademicField;
import com.sebu.backend.user.domain.GpaBand;
import com.sebu.backend.user.exception.NicknameAlreadyExistsException;
import com.sebu.backend.user.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileServiceNicknameConflictTest {
    @Mock AppUserRepository appUserRepository;
    @Mock ActiveUserCommandGuard activeUserCommandGuard;
    @Mock IntroductionModerator introductionModerator;
    @InjectMocks ProfileService profileService;

    @Test
    void 사전_조회_후_발생한_DB_닉네임_충돌도_도메인_오류로_변환한다() {
        AppUser user = mock(AppUser.class);
        when(user.getId()).thenReturn(1L);
        when(activeUserCommandGuard.lock(1L)).thenReturn(user);
        when(appUserRepository.existsByNicknameNormalizedAndIdNot("sebu", 1L)).thenReturn(false);
        when(introductionModerator.moderate(anyString()))
                .thenReturn(new ModerationResult(true, "v1", "test-provider"));
        doThrow(new DataIntegrityViolationException("uk_app_user_nickname_normalized"))
                .when(appUserRepository).flush();

        ProfileUpdateRequest request = new ProfileUpdateRequest(
                "SeBu",
                (short) 3,
                AcademicField.ENGINEERING,
                GpaBand.GTE_3_5,
                "동시 충돌 테스트 자기소개"
        );

        assertThatThrownBy(() -> profileService.updateProfile(1L, request))
                .isInstanceOf(NicknameAlreadyExistsException.class);
    }

    @Test
    void 닉네임_제약이_아닌_DB_오류는_중복_오류로_변환하지_않는다() {
        AppUser user = mock(AppUser.class);
        when(user.getId()).thenReturn(1L);
        when(activeUserCommandGuard.lock(1L)).thenReturn(user);
        when(appUserRepository.existsByNicknameNormalizedAndIdNot("sebu", 1L)).thenReturn(false);
        when(introductionModerator.moderate(anyString()))
                .thenReturn(new ModerationResult(true, "v1", "test-provider"));
        DataIntegrityViolationException databaseFailure =
                new DataIntegrityViolationException("ck_app_user_grade");
        doThrow(databaseFailure).when(appUserRepository).flush();

        ProfileUpdateRequest request = new ProfileUpdateRequest(
                "SeBu",
                (short) 3,
                AcademicField.ENGINEERING,
                GpaBand.GTE_3_5,
                "다른 DB 오류 테스트 자기소개"
        );

        assertThatThrownBy(() -> profileService.updateProfile(1L, request))
                .isSameAs(databaseFailure);
    }
}

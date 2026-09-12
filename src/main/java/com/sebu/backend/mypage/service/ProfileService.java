package com.sebu.backend.mypage.service;

import com.sebu.backend.department.domain.Department;
import com.sebu.backend.global.auth.ActiveUserCommandGuard;
import com.sebu.backend.mypage.dto.ProfileResponse;
import com.sebu.backend.mypage.dto.ProfileUpdateRequest;
import com.sebu.backend.user.exception.ProfileUpdateConflictException;
import com.sebu.backend.mypage.moderation.IntroductionModerationException;
import com.sebu.backend.mypage.moderation.IntroductionModerator;
import com.sebu.backend.mypage.moderation.ModerationResult;
import com.sebu.backend.user.domain.AppUser;
import com.sebu.backend.user.domain.Nickname;
import com.sebu.backend.user.exception.NicknameAlreadyExistsException;
import com.sebu.backend.user.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ProfileService {

    private final AppUserRepository appUserRepository;
    private final ActiveUserCommandGuard activeUserGuard;
    private final IntroductionModerator introductionModerator;

    @Transactional
    public ProfileResponse updateProfile(
            Long userId,
            ProfileUpdateRequest request
    ) {
        AppUser user = activeUserGuard.lock(userId);

        Nickname nickname = Nickname.from(request.nickname());
        validateNicknameUniqueness(user, nickname);

        ModerationResult moderationResult =
                introductionModerator.moderate(request.introduction());

        if (!moderationResult.allowed()) {
            throw new IntroductionModerationException();
        }

        LocalDateTime changedAt = LocalDateTime.now();
        user.selectAcademicField(request.academicField(), changedAt);
        user.updateProfile(
                nickname,
                request.grade(),
                request.gpaBand(),
                request.introduction(),
                changedAt,
                moderationResult.policyVersion(),
                moderationResult.providerVersion()
        );

        try {
            appUserRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            if (isNicknameUniqueConstraintViolation(exception)) {
                throw new NicknameAlreadyExistsException();
            }
            throw exception;
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw new ProfileUpdateConflictException();
        }

        return toResponse(user);
    }

    private void validateNicknameUniqueness(AppUser user, Nickname nickname) {
        if (nickname.normalizedValue() == null
                || nickname.normalizedValue().equals(user.getNicknameNormalized())) {
            return;
        }
        if (appUserRepository.existsByNicknameNormalizedAndIdNot(
                nickname.normalizedValue(),
                user.getId()
        )) {
            throw new NicknameAlreadyExistsException();
        }
    }

    private boolean isNicknameUniqueConstraintViolation(Throwable exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException constraintViolation
                    && "uk_app_user_nickname_normalized".equalsIgnoreCase(
                    constraintViolation.getConstraintName()
            )) {
                return true;
            }
            String message = cause.getMessage();
            if (message != null && message.toLowerCase(Locale.ROOT).contains(
                    "uk_app_user_nickname_normalized"
            )) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private ProfileResponse toResponse(AppUser user) {
        ProfileResponse.Department department = toDepartment(user);

        return new ProfileResponse(
                user.getName(),
                user.getNickname(),
                user.getGrade(),
                department,
                user.getAcademicField(),
                user.getGpaBand(),
                user.getIntroduction(),
                user.isProfileCompleted(),
                user.getProfileUpdatedAt()
        );
    }

    private ProfileResponse.Department toDepartment(AppUser user) {
        Department department = user.getMajorDepartment();
        if (department != null && department.getName().equals(user.getSejongDepartmentName())) {
            return new ProfileResponse.Department(department.getId().toString(), department.getName());
        }
        return user.getSejongDepartmentName() == null
                ? null
                : new ProfileResponse.Department(null, user.getSejongDepartmentName());
    }
}

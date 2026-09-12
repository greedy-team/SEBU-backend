package com.sebu.backend.auth.service;

import com.sebu.backend.auth.exception.AccessTokenInvalidException;
import com.sebu.backend.auth.exception.InvalidGradeException;
import com.sebu.backend.global.auth.CurrentUserProvider;
import com.sebu.backend.global.auth.ActiveUserCommandGuard;
import com.sebu.backend.user.domain.AppUser;
import com.sebu.backend.user.repository.AppUserRepository;
import com.sebu.backend.user.exception.ProfileUpdateConflictException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
public class CurrentUserService {
    private final CurrentUserProvider currentUserProvider;
    private final AppUserRepository appUserRepository;
    private final ActiveUserCommandGuard activeUserGuard;

    @Transactional(readOnly = true)
    public CurrentUser getCurrentUser() {
        Long userId = currentUserProvider.currentUserId()
            .orElseThrow(AccessTokenInvalidException::new);
        AppUser user = appUserRepository.findById(userId)
            .orElseThrow(AccessTokenInvalidException::new);
        return CurrentUser.from(user);
    }

    @Transactional
    public CurrentUser updateGrade(Integer grade) {
        if (grade == null || grade < 1 || grade > 5) {
            throw new InvalidGradeException();
        }
        Long userId = currentUserProvider.currentUserId()
            .orElseThrow(AccessTokenInvalidException::new);
        AppUser user = activeUserGuard.lock(userId);
        user.updateGrade(grade, LocalDateTime.now(ZoneOffset.UTC));
        try {
            appUserRepository.flush();
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw new ProfileUpdateConflictException();
        }
        return CurrentUser.from(user);
    }

    public record CurrentUser(
        Long id,
        String nickname,
        String studentId,
        String name,
        Short grade,
        Department department,
        boolean profileCompleted
    ) {
        private static CurrentUser from(AppUser user) {
            var major = user.getMajorDepartment();
            Department department = major != null && major.getName().equals(user.getSejongDepartmentName())
                ? new Department(major.getId(), major.getName())
                : user.getSejongDepartmentName() == null
                    ? null
                    : new Department(null, user.getSejongDepartmentName());
            return new CurrentUser(
                user.getId(),
                user.getNickname(),
                user.getProviderUserId(),
                user.getName(),
                user.getGrade(),
                department,
                user.isProfileCompleted()
            );
        }

        public record Department(Long id, String name) {
        }
    }
}

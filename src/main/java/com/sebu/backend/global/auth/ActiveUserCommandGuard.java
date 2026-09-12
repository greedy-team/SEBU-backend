package com.sebu.backend.global.auth;

import com.sebu.backend.auth.exception.AccessTokenInvalidException;
import com.sebu.backend.user.domain.AppUser;
import com.sebu.backend.user.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ActiveUserCommandGuard {
    private final AppUserRepository appUserRepository;

    public AppUser lock(Long userId) {
        return appUserRepository.findByIdForUpdate(userId)
            .filter(user -> !user.isDeleted())
            .orElseThrow(AccessTokenInvalidException::new);
    }
}

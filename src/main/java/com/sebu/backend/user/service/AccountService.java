package com.sebu.backend.user.service;

import com.sebu.backend.auth.service.AuthSessionService;
import com.sebu.backend.user.domain.AppUser;
import com.sebu.backend.user.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AppUserRepository appUserRepository;
    private final AuthSessionService authSessionService;

    @Transactional
    public void withdraw(Long userId) {
        AppUser user = appUserRepository.findByIdForUpdate(userId)
                .orElseThrow(() ->
                        new IllegalArgumentException("USER_NOT_FOUND"));

        authSessionService.revokeAllByUserId(userId);

        user.withdraw();
    }
}

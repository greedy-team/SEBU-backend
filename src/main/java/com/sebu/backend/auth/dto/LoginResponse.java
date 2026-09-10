package com.sebu.backend.auth.dto;

import com.sebu.backend.auth.service.AuthSessionService;

public record LoginResponse(
    long expiresIn,
    UserResponse user
) {
    public static LoginResponse from(AuthSessionService.LoginSession session) {
        return new LoginResponse(
            session.expiresIn(),
            new UserResponse(session.userId(), session.isNewUser(), session.isProfileCompleted())
        );
    }

    public record UserResponse(Long id, boolean isNewUser, boolean profileCompleted) {
    }
}

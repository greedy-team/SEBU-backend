package com.sebu.backend.auth.dto;

import com.sebu.backend.auth.service.AuthSessionService;

public record RefreshResponse(long expiresIn) {
    public static RefreshResponse from(AuthSessionService.RefreshSession session) {
        return new RefreshResponse(session.expiresIn());
    }

}

package com.sebu.backend.auth.dto;

import com.sebu.backend.auth.service.AuthSessionService;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(
    oneOf = {LoginResponse.Authenticated.class, LoginResponse.RecoveryRequired.class},
    discriminatorProperty = "loginStatus"
)
public sealed interface LoginResponse permits LoginResponse.Authenticated, LoginResponse.RecoveryRequired {
    LoginStatus loginStatus();

    static Authenticated authenticated(AuthSessionService.LoginSession session) {
        return new Authenticated(
            LoginStatus.AUTHENTICATED,
            session.expiresIn(),
            new UserResponse(session.userId(), session.isNewUser(), session.isProfileCompleted())
        );
    }

    static RecoveryRequired recoveryRequired(AuthSessionService.RecoveryChallenge challenge) {
        return new RecoveryRequired(
            LoginStatus.RECOVERY_REQUIRED,
            challenge.recoveryExpiresIn(),
            challenge.recoverableUntil()
        );
    }

    enum LoginStatus {
        AUTHENTICATED,
        RECOVERY_REQUIRED
    }

    record Authenticated(
        LoginStatus loginStatus,
        long expiresIn,
        UserResponse user
    ) implements LoginResponse {
    }

    record RecoveryRequired(
        LoginStatus loginStatus,
        long recoveryExpiresIn,
        Instant recoverableUntil
    ) implements LoginResponse {
    }

    record UserResponse(Long id, boolean isNewUser, boolean profileCompleted) {
    }
}

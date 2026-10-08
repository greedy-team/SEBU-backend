package com.sebu.backend.global.exception;

import com.sebu.backend.user.exception.ProfileUpdateConflictException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class ExceptionControllerAdviceTest {
    @Test
    void profileUpdateConflictUsesStable409ApiContract() {
        var response = new ExceptionControllerAdvice()
            .handleProfileUpdateConflict(new ProfileUpdateConflictException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().error().code()).isEqualTo("PROFILE_UPDATE_CONFLICT");
    }
}

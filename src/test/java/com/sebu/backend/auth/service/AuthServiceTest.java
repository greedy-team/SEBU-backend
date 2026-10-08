package com.sebu.backend.auth.service;

import com.sebu.backend.auth.exception.InvalidLoginRequestException;
import com.sebu.backend.auth.port.SejongAuthenticator;
import com.sebu.backend.auth.port.SejongAuthenticationException;
import com.sebu.backend.auth.port.SejongUserProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock
    SejongAuthenticator sejongAuthenticator;

    @Mock
    AuthSessionService authSessionService;

    @InjectMocks
    AuthService authService;

    @Test
    void provisionsUserWithVerifiedSchoolProfile() {
        SejongUserProfile profile = new SejongUserProfile("21012345", "홍길동", "컴퓨터공학과");
        when(sejongAuthenticator.authenticate("21012345", "password"))
            .thenReturn(profile);
        when(authSessionService.login(profile))
            .thenReturn(mock(AuthSessionService.LoginSession.class));

        authService.loginWithSejong("21012345", "password");

        verify(authSessionService).login(profile);
    }

    @Test
    void rejectsWhenRequestedAndVerifiedStudentIdsDiffer() {
        when(sejongAuthenticator.authenticate("21012345", "password"))
            .thenReturn(new SejongUserProfile("21054321", "홍길동", "컴퓨터공학과"));

        assertThatThrownBy(() -> authService.loginWithSejong("21012345", "password"))
            .isInstanceOfSatisfying(SejongAuthenticationException.class, exception ->
                org.assertj.core.api.Assertions.assertThat(exception.getReason())
                    .isEqualTo(SejongAuthenticationException.Reason.IDENTITY_MISMATCH));
        verifyNoInteractions(authSessionService);
    }

    @Test
    void rejectsBlankCredentialsBeforeCallingExternalSystem() {
        assertThatThrownBy(() -> authService.loginWithSejong(" ", "password"))
            .isInstanceOf(InvalidLoginRequestException.class)
            .hasMessage("INVALID_LOGIN_REQUEST");
        verifyNoInteractions(sejongAuthenticator, authSessionService);
    }

    @Test
    void rejectsInvalidStudentIdAndPasswordLengthsBeforeCallingExternalSystem() {
        assertThatThrownBy(() -> authService.loginWithSejong("2101234", "password"))
            .isInstanceOf(InvalidLoginRequestException.class);
        assertThatThrownBy(() -> authService.loginWithSejong("abcdefgh", "password"))
            .isInstanceOf(InvalidLoginRequestException.class);
        assertThatThrownBy(() -> authService.loginWithSejong("21012345", "short"))
            .isInstanceOf(InvalidLoginRequestException.class);
        assertThatThrownBy(() -> authService.loginWithSejong("21012345", "x".repeat(65)))
            .isInstanceOf(InvalidLoginRequestException.class);
        verifyNoInteractions(sejongAuthenticator, authSessionService);
    }

    @Test
    void acceptsTheExistingApiMaximumPasswordLengthWithoutChangingItsValue() {
        String password = "x".repeat(64);
        var profile = new SejongUserProfile("21012345", "홍길동", "컴퓨터공학과");
        when(sejongAuthenticator.authenticate("21012345", password)).thenReturn(profile);

        authService.loginWithSejong("21012345", password);

        verify(sejongAuthenticator).authenticate("21012345", password);
        verify(authSessionService).login(profile);
    }
}

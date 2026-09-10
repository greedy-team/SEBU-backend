package com.sebu.backend.auth.service;

import com.sebu.backend.auth.exception.InvalidLoginRequestException;
import com.sebu.backend.auth.exception.AuthSessionConflictException;
import com.sebu.backend.auth.port.SejongAuthenticationException;
import com.sebu.backend.auth.port.SejongAuthenticator;
import com.sebu.backend.auth.port.SejongUserProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {
    private static final String STUDENT_ID_PATTERN = "\\d{8}";
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_PASSWORD_LENGTH = 64;

    private final SejongAuthenticator sejongAuthenticator;
    private final AuthSessionService authSessionService;

    public AuthSessionService.LoginSession loginWithSejong(String studentId, String password) {
        if (!isValidStudentId(studentId) || !isValidPassword(password)) {
            throw new InvalidLoginRequestException();
        }
        String requestedStudentId = studentId.trim();
        SejongUserProfile profile = sejongAuthenticator.authenticate(requestedStudentId, password);
        if (!requestedStudentId.equals(profile.studentId())) {
            throw SejongAuthenticationException.identityMismatch();
        }
        try {
            return authSessionService.start(profile);
        } catch (DataIntegrityViolationException | ObjectOptimisticLockingFailureException exception) {
            try {
                return authSessionService.startExisting(profile)
                    .orElseThrow(() -> exception);
            } catch (ObjectOptimisticLockingFailureException retryConflict) {
                throw new AuthSessionConflictException();
            }
        }
    }

    private boolean isValidStudentId(String studentId) {
        return studentId != null && studentId.trim().matches(STUDENT_ID_PATTERN);
    }

    private boolean isValidPassword(String password) {
        return password != null
            && !password.isBlank()
            && password.length() >= MIN_PASSWORD_LENGTH
            && password.length() <= MAX_PASSWORD_LENGTH;
    }
}

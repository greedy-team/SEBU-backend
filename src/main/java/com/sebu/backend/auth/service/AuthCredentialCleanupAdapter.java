package com.sebu.backend.auth.service;

import com.sebu.backend.account.port.CredentialCleanupPort;
import com.sebu.backend.auth.repository.AccountRecoveryTokenRepository;
import com.sebu.backend.auth.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthCredentialCleanupAdapter implements CredentialCleanupPort {
    private final RefreshTokenRepository refreshTokenRepository;
    private final AccountRecoveryTokenRepository recoveryTokenRepository;

    @Override
    public void deleteAllByUserId(Long userId) {
        refreshTokenRepository.deleteAllByUserId(userId);
        recoveryTokenRepository.deleteAllByUserId(userId);
    }
}

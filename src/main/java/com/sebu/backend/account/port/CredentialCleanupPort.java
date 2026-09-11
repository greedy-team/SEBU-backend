package com.sebu.backend.account.port;

public interface CredentialCleanupPort {
    void deleteAllByUserId(Long userId);
}

package com.sebu.backend.account.port;

public interface ActivityCleanupPort {
    void deleteAllByUserId(Long userId);
}

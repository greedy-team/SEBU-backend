package com.sebu.backend.auth.service;

import com.sebu.backend.auth.config.AuthRetentionProperties;
import com.sebu.backend.auth.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class RefreshTokenCleanupService {
    private final RefreshTokenRepository repository;
    private final AuthRetentionProperties properties;

    @Transactional
    public int deleteExpiredBatch(LocalDateTime now) {
        var ids = repository.findExpiredIds(now, PageRequest.of(0, properties.batchSize()));
        if (ids.isEmpty()) {
            return 0;
        }
        return repository.deleteExpiredIds(ids, now);
    }
}

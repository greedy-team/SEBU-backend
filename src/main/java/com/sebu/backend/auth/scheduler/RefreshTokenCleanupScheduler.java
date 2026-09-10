package com.sebu.backend.auth.scheduler;

import com.sebu.backend.auth.config.AuthRetentionProperties;
import com.sebu.backend.auth.service.RefreshTokenCleanupService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Component
@EnableScheduling
@EnableConfigurationProperties(AuthRetentionProperties.class)
@RequiredArgsConstructor
public class RefreshTokenCleanupScheduler {
    private final RefreshTokenCleanupService service;
    private final AuthRetentionProperties properties;

    @Scheduled(cron = "${app.auth.retention.purge-cron:0 10 3 * * *}", zone = "Asia/Seoul")
    public void purgeExpiredTokens() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        for (int batch = 0; batch < properties.maxBatches(); batch++) {
            if (service.deleteExpiredBatch(now) < properties.batchSize()) {
                break;
            }
        }
    }
}

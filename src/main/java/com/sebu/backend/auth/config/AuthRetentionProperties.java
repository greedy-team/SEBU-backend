package com.sebu.backend.auth.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.auth.retention")
public record AuthRetentionProperties(@DefaultValue("500") @Min(1) @Max(5000) int batchSize,
                                      @DefaultValue("100") @Min(1) @Max(1000) int maxBatches) {
}

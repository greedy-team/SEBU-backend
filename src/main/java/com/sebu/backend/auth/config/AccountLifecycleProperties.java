package com.sebu.backend.auth.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.auth.account")
public record AccountLifecycleProperties(
    @DefaultValue("30d") @NotNull Duration recoveryWindow,
    @DefaultValue("1h") @NotNull Duration minimumRecoveryCooldown,
    @DefaultValue("1m") @NotNull Duration accessTokenSafetyMargin,
    @DefaultValue("5m") @NotNull Duration recoveryTokenExpiration,
    @DefaultValue("500") @Min(1) @Max(5000) int batchSize,
    @DefaultValue("100") @Min(1) @Max(1000) int maxBatches
) {
    @AssertTrue(message = "account lifecycle durations must be positive")
    public boolean isDurationConfigurationValid() {
        return isPositive(recoveryWindow)
            && isPositive(minimumRecoveryCooldown)
            && isPositive(accessTokenSafetyMargin)
            && isPositive(recoveryTokenExpiration);
    }

    @AssertTrue(message = "recovery token expiration must not exceed the recovery window")
    public boolean isRecoveryTokenExpirationWithinWindow() {
        return recoveryTokenExpiration != null && recoveryWindow != null
            && recoveryTokenExpiration.compareTo(recoveryWindow) <= 0;
    }

    private static boolean isPositive(Duration value) {
        return value != null && !value.isZero() && !value.isNegative();
    }
}

package com.sebu.backend.crawling.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.professor-crawler")
public class ProfessorCrawlerProperties {
    private boolean enabled;

    @Positive
    private Long sourceId;

    @NotNull
    private Duration timeout = Duration.ofSeconds(15);

    @NotNull
    private Duration requestDelay = Duration.ofSeconds(1);

    @Min(1024)
    @Max(10 * 1024 * 1024)
    private int maxBodySizeBytes = 2 * 1024 * 1024;

    @NotBlank
    private String userAgent = "SEBU-ProfessorCrawler/1.0 (+https://github.com/greedy-team/SEBU-backend)";

    @NotBlank
    private String curlExecutable = "curl";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Long getSourceId() {
        return sourceId;
    }

    public void setSourceId(Long sourceId) {
        this.sourceId = sourceId;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public Duration getRequestDelay() {
        return requestDelay;
    }

    public void setRequestDelay(Duration requestDelay) {
        this.requestDelay = requestDelay;
    }

    public int getMaxBodySizeBytes() {
        return maxBodySizeBytes;
    }

    public void setMaxBodySizeBytes(int maxBodySizeBytes) {
        this.maxBodySizeBytes = maxBodySizeBytes;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getCurlExecutable() {
        return curlExecutable;
    }

    public void setCurlExecutable(String curlExecutable) {
        this.curlExecutable = curlExecutable;
    }

    @AssertTrue(message = "timeout must be between 100ms and 2147483647ms")
    public boolean isTimeoutValid() {
        if (timeout == null) {
            return true;
        }
        long timeoutMillis = timeout.toMillis();
        return timeoutMillis >= 100 && timeoutMillis <= Integer.MAX_VALUE;
    }

    @AssertTrue(message = "request-delay must not be negative")
    public boolean isRequestDelayValid() {
        return requestDelay == null || !requestDelay.isNegative();
    }
}

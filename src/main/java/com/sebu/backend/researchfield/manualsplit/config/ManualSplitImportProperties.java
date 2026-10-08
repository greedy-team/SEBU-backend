package com.sebu.backend.researchfield.manualsplit.config;

import jakarta.validation.constraints.AssertTrue;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.nio.file.Path;

@Validated
@ConfigurationProperties(prefix = "app.research-field-manual-split")
public class ManualSplitImportProperties {
    private boolean enabled;
    private Path csvPath;
    private String reviewer;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Path getCsvPath() {
        return csvPath;
    }

    public void setCsvPath(Path csvPath) {
        this.csvPath = csvPath;
    }

    public String getReviewer() {
        return reviewer;
    }

    public void setReviewer(String reviewer) {
        this.reviewer = reviewer;
    }

    @AssertTrue(message = "csv-path and reviewer are required when manual split import is enabled")
    public boolean isEnabledConfigurationValid() {
        return !enabled
            || (csvPath != null && reviewer != null && !reviewer.isBlank());
    }
}

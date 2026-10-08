package com.sebu.backend.laboratory.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.laboratory-retention")
public class LaboratoryRetentionProperties {
    @Min(1)
    private int days = 30;

    public int getDays() {
        return days;
    }

    public void setDays(int days) {
        this.days = days;
    }
}

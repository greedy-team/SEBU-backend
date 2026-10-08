package com.sebu.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SejongLoginRequest(
    @NotBlank
    @Pattern(regexp = "\\d{8}")
    String studentId,
    @NotBlank
    @Size(min = 8, max = 64)
    String password
) {
    @Override
    public String toString() {
        return "SejongLoginRequest[studentId=REDACTED, password=REDACTED]";
    }
}

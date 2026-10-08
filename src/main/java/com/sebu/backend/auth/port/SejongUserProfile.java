package com.sebu.backend.auth.port;

public record SejongUserProfile(
    String studentId,
    String name,
    String departmentName
) {
    @Override
    public String toString() {
        return "SejongUserProfile[REDACTED]";
    }
}

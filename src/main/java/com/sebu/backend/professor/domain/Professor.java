package com.sebu.backend.professor.domain;

import com.sebu.backend.global.domain.BaseTimeEntity;
import com.sebu.backend.department.domain.Department;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Locale;
import java.util.Objects;

@Getter
@Entity
@Table(name = "professor")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Professor extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 100)
    private String position;

    @Column(unique = true, length = 255)
    private String email;

    public Professor(Department department, String name, String email) {
        this(department, name, null, email);
    }

    public Professor(Department department, String name, String position, String email) {
        this.department = Objects.requireNonNull(department, "DEPARTMENT_REQUIRED");
        this.name = requireText(name, "PROFESSOR_NAME_REQUIRED");
        this.position = normalizeNullable(position);
        this.email = normalizeEmail(email);
    }

    public boolean hasPromotionProfile(String name, String position, String email) {
        return Objects.equals(this.name, requireText(name, "PROFESSOR_NAME_REQUIRED"))
            && Objects.equals(this.position, normalizeNullable(position))
            && Objects.equals(this.email, normalizeEmail(email));
    }

    public boolean hasPromotionIdentity(String name, String email) {
        return Objects.equals(this.name, requireText(name, "PROFESSOR_NAME_REQUIRED"))
            && Objects.equals(this.email, normalizeEmail(email));
    }

    public boolean mergePromotionPosition(String position) {
        String normalizedPosition = normalizeNullable(position);
        if (normalizedPosition == null || Objects.equals(this.position, normalizedPosition)) {
            return false;
        }
        if (this.position != null) {
            throw new IllegalStateException("PROFESSOR_PROFILE_CONFLICT");
        }
        this.position = normalizedPosition;
        return true;
    }

    public void updateFromPromotion(String name, String position, String email) {
        this.name = requireText(name, "PROFESSOR_NAME_REQUIRED");
        this.position = normalizeNullable(position);
        this.email = normalizeEmail(email);
    }

    private String requireText(String value, String errorCode) {
        String normalized = normalizeNullable(value);
        if (normalized == null) {
            throw new IllegalArgumentException(errorCode);
        }
        return normalized;
    }

    private String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String normalizeEmail(String value) {
        String normalized = normalizeNullable(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }
}

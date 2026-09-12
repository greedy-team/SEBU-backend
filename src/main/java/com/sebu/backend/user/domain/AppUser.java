package com.sebu.backend.user.domain;

import com.sebu.backend.department.domain.Department;
import com.sebu.backend.global.domain.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Objects;

@Getter
@Entity
@Table(
        name = "app_user",
        indexes = @Index(
                name = "idx_app_user_withdrawal_anonymization",
                columnList = "anonymized_at, deleted_at, id"
        ),
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_app_user_provider_identity",
                        columnNames = {"provider", "provider_user_id"}
                ),
                @UniqueConstraint(
                        name = "uk_app_user_nickname_normalized",
                        columnNames = "nickname_normalized"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppUser extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, length = 255)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private AuthProvider provider;

    @Column(name = "provider_user_id", length = 100)
    private String providerUserId;

    @Column(name = "profile_completed", nullable = false)
    private boolean profileCompleted;

    @Column(length = 30)
    private String name;

    @Column(length = 30)
    private String nickname;

    @Column(name = "nickname_normalized", length = 100)
    private String nicknameNormalized;

    // 1-4: undergraduate year, 5: graduate (selected by the user).
    private Short grade;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "major_department_id")
    private Department majorDepartment;

    @Column(name = "sejong_department_name", length = 100)
    private String sejongDepartmentName;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "academic_field", length = 32)
    private AcademicField academicField;

    @Enumerated(EnumType.STRING)
    @Column(name = "gpa_band", length = 20)
    private GpaBand gpaBand;

    @Column(nullable = false, length = 500)
    private String introduction = "";

    @Column(name = "introduction_moderated_at")
    private LocalDateTime introductionModeratedAt;

    @Column(name = "introduction_policy_version", length = 50)
    private String introductionPolicyVersion;

    @Column(name = "introduction_provider_version", length = 100)
    private String introductionProviderVersion;

    @Column(name = "profile_updated_at")
    private LocalDateTime profileUpdatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "anonymized_at")
    private LocalDateTime anonymizedAt;

    @Column(name = "auth_version", nullable = false)
    private long authVersion;

    @Version
    @Column(nullable = false)
    private long version;

    public AppUser(String email) {
        this.email = normalizeEmail(email);
    }

    private AppUser(AuthProvider provider, String providerUserId) {
        this.provider = provider;
        this.providerUserId = requireProviderUserId(providerUserId);
    }

    public static AppUser sejong(String providerUserId) {
        return new AppUser(AuthProvider.SEJONG, providerUserId);
    }

    public static AppUser sejong(
        String providerUserId,
        String name,
        String departmentName,
        Department department,
        LocalDateTime profileUpdatedAt
    ) {
        AppUser user = new AppUser(AuthProvider.SEJONG, providerUserId);
        user.applySejongProfile(name, departmentName, department, profileUpdatedAt);
        return user;
    }

    public boolean applySejongProfile(
        String name,
        String departmentName,
        Department department,
        LocalDateTime changedAt
    ) {
        String normalizedName = requireName(name);
        String normalizedDepartmentName = requireDepartmentName(departmentName);
        if (Objects.equals(this.name, normalizedName)
            && Objects.equals(this.sejongDepartmentName, normalizedDepartmentName)
            && Objects.equals(this.majorDepartment, department)) {
            return false;
        }
        this.name = normalizedName;
        this.sejongDepartmentName = normalizedDepartmentName;
        this.majorDepartment = department;
        this.profileUpdatedAt = Objects.requireNonNull(changedAt, "PROFILE_UPDATED_AT_REQUIRED");
        refreshProfileCompleted();
        return true;
    }

    public void updateGrade(int grade, LocalDateTime changedAt) {
        if (grade < 1 || grade > 5) {
            throw new IllegalArgumentException("GRADE_OUT_OF_RANGE");
        }
        short normalizedGrade = (short) grade;
        if (Objects.equals(this.grade, normalizedGrade)) {
            return;
        }
        this.grade = normalizedGrade;
        this.profileUpdatedAt = Objects.requireNonNull(changedAt, "PROFILE_UPDATED_AT_REQUIRED");
        refreshProfileCompleted();
    }

    public void selectAcademicField(AcademicField academicField, LocalDateTime changedAt) {
        Objects.requireNonNull(academicField, "ACADEMIC_FIELD_REQUIRED");
        if (this.academicField == academicField) {
            return;
        }
        this.profileUpdatedAt = Objects.requireNonNull(changedAt, "PROFILE_UPDATED_AT_REQUIRED");
        this.academicField = academicField;
    }

    private void refreshProfileCompleted() {
        this.profileCompleted = name != null
            && sejongDepartmentName != null
            && grade != null;
    }

    private static String requireName(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("USER_NAME_REQUIRED");
        }
        String normalized = value.trim();
        if (normalized.length() > 30) {
            throw new IllegalArgumentException("USER_NAME_TOO_LONG");
        }
        return normalized;
    }

    private static String requireDepartmentName(String value) {
        return requireProfileText(value, 100, "SEJONG_DEPARTMENT_NAME");
    }

    private static String requireProfileText(String value, int maxLength, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + "_REQUIRED");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + "_TOO_LONG");
        }
        return normalized;
    }

    private static String normalizeEmail(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("USER_EMAIL_REQUIRED");
        }
        return value.trim().toLowerCase();
    }

    private static String requireProviderUserId(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("PROVIDER_USER_ID_REQUIRED");
        }
        return value.trim();
    }

    public void updateProfile(
            Nickname nickname,
            Short grade,
            GpaBand gpaBand,
            String introduction,
            LocalDateTime moderatedAt,
            String policyVersion,
            String providerVersion
    ) {
        requireGrade(grade);
        boolean changed =
                !Objects.equals(this.nickname, nickname.value())
                        || !Objects.equals(this.grade, grade)
                        || !Objects.equals(this.gpaBand, gpaBand)
                        || !Objects.equals(this.introduction, introduction);

        this.nickname = nickname.value();
        this.nicknameNormalized = nickname.normalizedValue();
        this.grade = grade;
        this.gpaBand = gpaBand;
        this.introduction = introduction;

        this.introductionModeratedAt = moderatedAt;
        this.introductionPolicyVersion = policyVersion;
        this.introductionProviderVersion = providerVersion;

        refreshProfileCompleted();

        if (changed) {
            this.profileUpdatedAt = Objects.requireNonNull(moderatedAt, "PROFILE_UPDATED_AT_REQUIRED");
        }
    }

    private static void requireGrade(Short grade) {
        if (grade == null || grade < 1 || grade > 5) {
            throw new IllegalArgumentException("GRADE_OUT_OF_RANGE");
        }
    }

    public void withdraw(LocalDateTime withdrawnAt) {
        if (this.deletedAt == null) {
            this.deletedAt = Objects.requireNonNull(withdrawnAt, "WITHDRAWN_AT_REQUIRED");
            this.authVersion = Math.incrementExact(this.authVersion);
        }
    }

    public void recover() {
        if (deletedAt == null || anonymizedAt != null) {
            throw new IllegalStateException("ACCOUNT_NOT_RECOVERABLE");
        }
        deletedAt = null;
    }

    public void anonymize(LocalDateTime completedAt) {
        if (deletedAt == null) {
            throw new IllegalStateException("ACTIVE_ACCOUNT_CANNOT_BE_ANONYMIZED");
        }
        if (anonymizedAt != null) {
            return;
        }
        email = null;
        provider = null;
        providerUserId = null;
        profileCompleted = false;
        name = null;
        nickname = null;
        nicknameNormalized = null;
        grade = null;
        majorDepartment = null;
        sejongDepartmentName = null;
        academicField = null;
        gpaBand = null;
        introduction = "";
        introductionModeratedAt = null;
        introductionPolicyVersion = null;
        introductionProviderVersion = null;
        profileUpdatedAt = null;
        anonymizedAt = Objects.requireNonNull(completedAt, "ANONYMIZED_AT_REQUIRED");
    }

    public boolean isDeleted() {
        return this.deletedAt != null;
    }

    public boolean isAnonymized() {
        return anonymizedAt != null;
    }
}

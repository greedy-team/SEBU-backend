package com.sebu.backend.user.domain;

import com.sebu.backend.college.domain.College;
import com.sebu.backend.department.domain.Department;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AcademicFieldProfileTest {
    private static final LocalDateTime CREATED = LocalDateTime.of(2026, 9, 12, 10, 0);

    @ParameterizedTest
    @EnumSource(AcademicField.class)
    void selectionUpdatesTimestampAndRepeatingItKeepsTheTimestamp(AcademicField field) {
        AppUser user = AppUser.sejong("academic-field-user");
        assertThat(user.getAcademicField()).isNull();

        user.selectAcademicField(field, CREATED);
        assertThat(user.getAcademicField()).isEqualTo(field);
        assertThat(user.getProfileUpdatedAt()).isEqualTo(CREATED);
        assertThat(user.isProfileCompleted()).isFalse();

        user.selectAcademicField(field, CREATED.plusHours(1));
        assertThat(user.getProfileUpdatedAt()).isEqualTo(CREATED);
    }

    @Test
    void changingOnlyTheFieldUpdatesTimestampAndNullCannotClearTheSelection() {
        AppUser user = AppUser.sejong("academic-field-user");
        user.selectAcademicField(AcademicField.ENGINEERING, CREATED);
        user.selectAcademicField(AcademicField.NATURAL_SCIENCE, CREATED.plusHours(1));
        assertThat(user.getProfileUpdatedAt()).isEqualTo(CREATED.plusHours(1));

        assertThatThrownBy(() -> user.selectAcademicField(null, CREATED.plusHours(2)))
            .isInstanceOf(NullPointerException.class).hasMessage("ACADEMIC_FIELD_REQUIRED");
        assertThat(user.getAcademicField()).isEqualTo(AcademicField.NATURAL_SCIENCE);
        assertThat(user.getProfileUpdatedAt()).isEqualTo(CREATED.plusHours(1));
    }

    @Test
    void schoolSyncAndGradeUpdatePreserveSelectionAndExistingCompletionRules() {
        Department department = new Department(new College("계열대학"), "계열학과");
        AppUser user = AppUser.sejong("academic-field-user", "홍길동", department.getName(), department, CREATED);
        user.updateGrade(3, CREATED);
        assertThat(user.isProfileCompleted()).isTrue();
        assertThat(user.getAcademicField()).isNull();

        user.selectAcademicField(AcademicField.ENGINEERING, CREATED);
        user.applySejongProfile("김길동", department.getName(), department, CREATED.plusHours(1));
        user.updateGrade(4, CREATED.plusHours(2));
        assertThat(user.getAcademicField()).isEqualTo(AcademicField.ENGINEERING);
        assertThat(user.isProfileCompleted()).isTrue();
    }

    @Test
    void recoveryPreservesSelectionAndAnonymizationClearsIt() {
        AppUser user = AppUser.sejong("academic-field-user");
        user.selectAcademicField(AcademicField.ENGINEERING, CREATED);
        user.withdraw(CREATED.plusDays(1));
        user.recover();
        assertThat(user.getAcademicField()).isEqualTo(AcademicField.ENGINEERING);
        user.withdraw(CREATED.plusDays(2));
        user.anonymize(CREATED.plusDays(40));
        assertThat(user.getAcademicField()).isNull();
    }
}

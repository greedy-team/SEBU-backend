package com.sebu.backend.professor.domain;

import com.sebu.backend.department.domain.Department;
import com.sebu.backend.global.domain.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

@Getter
@Entity
@Table(name = "professor_department")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProfessorDepartment extends BaseTimeEntity {
    @EmbeddedId
    private ProfessorDepartmentId id;

    @MapsId("professorId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "professor_id", nullable = false)
    private Professor professor;

    @MapsId("departmentId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @Column(length = 100)
    private String position;

    public ProfessorDepartment(
        Professor professor,
        Department department,
        String position
    ) {
        this.professor = Objects.requireNonNull(professor, "PROFESSOR_REQUIRED");
        this.department = Objects.requireNonNull(department, "DEPARTMENT_REQUIRED");
        id = new ProfessorDepartmentId(
            Objects.requireNonNull(professor.getId(), "PROFESSOR_ID_REQUIRED"),
            Objects.requireNonNull(department.getId(), "DEPARTMENT_ID_REQUIRED")
        );
        this.position = normalizeNullable(position);
    }

    public void updatePosition(String position) {
        this.position = normalizeNullable(position);
    }

    private String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}

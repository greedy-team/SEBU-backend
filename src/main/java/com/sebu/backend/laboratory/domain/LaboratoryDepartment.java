package com.sebu.backend.laboratory.domain;

import com.sebu.backend.department.domain.Department;
import com.sebu.backend.global.domain.BaseTimeEntity;
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
@Table(name = "laboratory_department")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LaboratoryDepartment extends BaseTimeEntity {
    @EmbeddedId
    private LaboratoryDepartmentId id;

    @MapsId("laboratoryId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "laboratory_id", nullable = false)
    private Laboratory laboratory;

    @MapsId("departmentId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    public LaboratoryDepartment(Laboratory laboratory, Department department) {
        this.laboratory = Objects.requireNonNull(laboratory, "LABORATORY_REQUIRED");
        this.department = Objects.requireNonNull(department, "DEPARTMENT_REQUIRED");
        id = new LaboratoryDepartmentId(
            Objects.requireNonNull(laboratory.getId(), "LABORATORY_ID_REQUIRED"),
            Objects.requireNonNull(department.getId(), "DEPARTMENT_ID_REQUIRED")
        );
    }
}

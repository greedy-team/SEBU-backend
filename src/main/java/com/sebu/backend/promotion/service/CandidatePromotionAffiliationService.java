package com.sebu.backend.promotion.service;

import com.sebu.backend.department.domain.Department;
import com.sebu.backend.laboratory.domain.Laboratory;
import com.sebu.backend.laboratory.domain.LaboratoryDepartment;
import com.sebu.backend.laboratory.repository.LaboratoryDepartmentRepository;
import com.sebu.backend.professor.domain.Professor;
import com.sebu.backend.professor.domain.ProfessorDepartment;
import com.sebu.backend.professor.repository.ProfessorDepartmentRepository;
import com.sebu.backend.promotion.exception.CandidatePromotionException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@RequiredArgsConstructor
class CandidatePromotionAffiliationService {
    private final ProfessorDepartmentRepository professorDepartmentRepository;
    private final LaboratoryDepartmentRepository laboratoryDepartmentRepository;

    boolean ensure(
        Professor professor,
        Laboratory laboratory,
        Department sourceDepartment,
        String position
    ) {
        boolean primaryChanged = ensurePrimaryAffiliations(professor, laboratory);
        boolean professorChanged = ensureProfessorAffiliation(
            professor,
            sourceDepartment,
            position
        );
        boolean laboratoryChanged = ensureLaboratoryAffiliation(
            laboratory,
            sourceDepartment
        );
        return primaryChanged || professorChanged || laboratoryChanged;
    }

    private boolean ensurePrimaryAffiliations(Professor professor, Laboratory laboratory) {
        boolean professorChanged = ensureProfessorAffiliation(
            professor,
            professor.getDepartment(),
            professor.getPosition()
        );
        boolean laboratoryChanged = ensureLaboratoryAffiliation(
            laboratory,
            laboratory.getDepartment()
        );
        return professorChanged || laboratoryChanged;
    }

    private boolean ensureProfessorAffiliation(
        Professor professor,
        Department department,
        String position
    ) {
        return professorDepartmentRepository.findByProfessor_IdAndDepartment_Id(
            requireId(professor.getId(), "PROFESSOR_ID_REQUIRED"),
            requireId(department.getId(), "DEPARTMENT_ID_REQUIRED")
        ).map(affiliation -> updatePosition(affiliation, position))
            .orElseGet(() -> {
                professorDepartmentRepository.save(
                    new ProfessorDepartment(professor, department, position)
                );
                return true;
            });
    }

    private boolean updatePosition(ProfessorDepartment affiliation, String position) {
        String normalizedPosition = normalizeNullable(position);
        if (Objects.equals(affiliation.getPosition(), normalizedPosition)) {
            return false;
        }
        affiliation.updatePosition(position);
        return true;
    }

    private boolean ensureLaboratoryAffiliation(
        Laboratory laboratory,
        Department department
    ) {
        Long laboratoryId = requireId(laboratory.getId(), "LABORATORY_ID_REQUIRED");
        Long departmentId = requireId(department.getId(), "DEPARTMENT_ID_REQUIRED");
        if (laboratoryDepartmentRepository.existsByLaboratory_IdAndDepartment_Id(
            laboratoryId,
            departmentId
        )) {
            return false;
        }
        if (laboratoryDepartmentRepository.existsActiveLaboratoryName(
            departmentId,
            laboratory.getName(),
            laboratoryId
        )) {
            throw new CandidatePromotionException("LABORATORY_NAME_CONFLICT");
        }
        laboratoryDepartmentRepository.save(new LaboratoryDepartment(laboratory, department));
        return true;
    }

    private Long requireId(Long value, String errorCode) {
        if (value == null) {
            throw new CandidatePromotionException(errorCode);
        }
        return value;
    }

    private String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}

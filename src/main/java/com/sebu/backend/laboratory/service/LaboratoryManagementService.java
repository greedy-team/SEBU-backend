package com.sebu.backend.laboratory.service;

import com.sebu.backend.department.domain.Department;
import com.sebu.backend.department.repository.DepartmentRepository;
import com.sebu.backend.laboratory.domain.Laboratory;
import com.sebu.backend.laboratory.domain.LaboratoryDepartment;
import com.sebu.backend.laboratory.repository.LaboratoryDepartmentRepository;
import com.sebu.backend.laboratory.repository.LaboratoryRepository;
import com.sebu.backend.laboratory.domain.RecruitmentStatus;
import com.sebu.backend.professor.domain.Professor;
import com.sebu.backend.professor.domain.ProfessorDepartment;
import com.sebu.backend.professor.repository.ProfessorDepartmentRepository;
import com.sebu.backend.professor.repository.ProfessorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LaboratoryManagementService {
    private final LaboratoryRepository laboratoryRepository;
    private final LaboratoryDepartmentRepository laboratoryDepartmentRepository;
    private final ProfessorRepository professorRepository;
    private final ProfessorDepartmentRepository professorDepartmentRepository;
    private final DepartmentRepository departmentRepository;

    @Transactional
    public Laboratory create(
        Long professorId,
        Long departmentId,
        String name,
        String websiteUrl,
        RecruitmentStatus status
    ) {
        Professor professor = findProfessor(professorId);
        Department department = findDepartment(departmentId);
        validateUniqueActiveName(departmentId, name);
        Laboratory laboratory = laboratoryRepository.save(
            new Laboratory(professor, department, name, websiteUrl, status)
        );
        professorDepartmentRepository.findByProfessor_IdAndDepartment_Id(
            professorId,
            departmentId
        ).orElseGet(() -> professorDepartmentRepository.save(
            new ProfessorDepartment(professor, department, professor.getPosition())
        ));
        laboratoryDepartmentRepository.save(new LaboratoryDepartment(laboratory, department));
        return laboratory;
    }

    @Transactional
    public void softDelete(Long laboratoryId) {
        findActiveLaboratory(laboratoryId).softDelete();
    }

    @Transactional
    public Laboratory updateWebsiteManually(Long laboratoryId, String websiteUrl) {
        Laboratory laboratory = findActiveLaboratory(laboratoryId);
        laboratory.updateWebsiteManually(websiteUrl);
        return laboratory;
    }

    private Professor findProfessor(Long professorId) {
        return professorRepository.findById(professorId)
            .orElseThrow(() -> new IllegalArgumentException("Professor not found"));
    }

    private Department findDepartment(Long departmentId) {
        return departmentRepository.findById(departmentId)
            .orElseThrow(() -> new IllegalArgumentException("Department not found"));
    }

    private Laboratory findActiveLaboratory(Long laboratoryId) {
        return laboratoryRepository.findByIdAndDeletedAtIsNull(laboratoryId)
            .orElseThrow(() -> new IllegalArgumentException("Laboratory not found"));
    }

    private void validateUniqueActiveName(Long departmentId, String name) {
        if (laboratoryRepository.existsByDepartmentIdAndNameAndDeletedAtIsNull(departmentId, name)) {
            throw new IllegalStateException("ACTIVE_LABORATORY_NAME_DUPLICATED");
        }
    }
}

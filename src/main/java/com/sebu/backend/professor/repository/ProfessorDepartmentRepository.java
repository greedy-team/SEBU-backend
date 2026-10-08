package com.sebu.backend.professor.repository;

import com.sebu.backend.professor.domain.ProfessorDepartment;
import com.sebu.backend.professor.domain.ProfessorDepartmentId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProfessorDepartmentRepository
    extends JpaRepository<ProfessorDepartment, ProfessorDepartmentId> {

    Optional<ProfessorDepartment> findByProfessor_IdAndDepartment_Id(
        Long professorId,
        Long departmentId
    );
}

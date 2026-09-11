package com.sebu.backend.department.repository;

import com.sebu.backend.department.domain.Department;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DepartmentRepository extends JpaRepository<Department, Long> {

    List<Department> findAllByName(String name);

    List<Department> findAllByOrderByNameAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select department from Department department where department.id = :departmentId")
    Optional<Department> findByIdForUpdate(@Param("departmentId") Long departmentId);
}

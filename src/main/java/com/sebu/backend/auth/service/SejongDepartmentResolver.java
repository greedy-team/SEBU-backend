package com.sebu.backend.auth.service;

import com.sebu.backend.global.logging.OperationalLog;
import com.sebu.backend.department.domain.Department;
import com.sebu.backend.department.repository.DepartmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class SejongDepartmentResolver {
    private final DepartmentRepository departmentRepository;

    public Department resolve(String departmentName) {
        List<Department> matches = departmentRepository.findAllByName(departmentName.trim());
        if (matches.size() == 1) {
            return matches.getFirst();
        }
        OperationalLog.departmentUnresolved(matches.size());
        return null;
    }
}

package com.sebu.backend.auth.service;

import com.sebu.backend.global.logging.OperationalLog;
import com.sebu.backend.department.domain.Department;
import com.sebu.backend.department.service.AcademicAffiliationResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SejongDepartmentResolver {
    private final AcademicAffiliationResolver affiliationResolver;

    public Department resolve(String departmentName) {
        var affiliation = affiliationResolver.resolve(departmentName);
        if (affiliation.college() == null) {
            OperationalLog.departmentUnresolved(affiliation.currentMatchCount());
        }
        return affiliation.department();
    }
}

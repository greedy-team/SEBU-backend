package com.sebu.backend.college.dto;

import java.util.List;

public record CollegeResponse(
        Long id,
        String name,
        long laboratoryCount,
        long departmentCount,
        List<CollegeDepartmentResponse> departments
) {
}

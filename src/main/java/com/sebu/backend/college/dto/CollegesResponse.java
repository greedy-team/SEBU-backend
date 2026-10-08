package com.sebu.backend.college.dto;

import java.util.List;

public record CollegesResponse(
        List<CollegeResponse> colleges
) {
}

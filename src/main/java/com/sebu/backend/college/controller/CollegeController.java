package com.sebu.backend.college.controller;

import com.sebu.backend.college.dto.CollegesResponse;
import com.sebu.backend.college.service.CollegeQueryService;
import com.sebu.backend.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/colleges")
@RequiredArgsConstructor
public class CollegeController {

    private final CollegeQueryService collegeQueryService;

    @GetMapping
    public ApiResponse<CollegesResponse> getAll() {
        return ApiResponse.success(collegeQueryService.getAll());
    }
}

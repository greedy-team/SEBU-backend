package com.sebu.backend.college.service;

import com.sebu.backend.college.domain.College;
import com.sebu.backend.college.dto.CollegeDepartmentResponse;
import com.sebu.backend.college.dto.CollegeResponse;
import com.sebu.backend.college.dto.CollegesResponse;
import com.sebu.backend.college.repository.CollegeRepository;
import com.sebu.backend.department.domain.Department;
import com.sebu.backend.department.repository.DepartmentRepository;
import com.sebu.backend.laboratory.repository.CollegeLaboratoryCountProjection;
import com.sebu.backend.laboratory.repository.LaboratoryDepartmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CollegeQueryService {

    private final CollegeRepository collegeRepository;
    private final DepartmentRepository departmentRepository;
    private final LaboratoryDepartmentRepository laboratoryDepartmentRepository;

    public CollegesResponse getAll() {
        List<College> colleges =
                collegeRepository.findAllByOrderByNameAsc();

        List<Department> departments =
                departmentRepository.findAllByOrderByNameAsc();

        Map<Long, List<Department>> departmentsByCollegeId =
                departments.stream()
                        .collect(Collectors.groupingBy(
                                department -> department.getCollege().getId()
                        ));

        Map<Long, Long> laboratoryCountByCollegeId =
                laboratoryDepartmentRepository
                        .countActiveLaboratoriesByCollege()
                        .stream()
                        .collect(Collectors.toMap(
                                CollegeLaboratoryCountProjection::getCollegeId,
                                CollegeLaboratoryCountProjection::getLaboratoryCount
                        ));

        List<CollegeResponse> collegeResponses =
                colleges.stream()
                        .map(college -> {
                            List<Department> collegeDepartments =
                                    departmentsByCollegeId.getOrDefault(
                                            college.getId(),
                                            List.of()
                                    );

                            List<CollegeDepartmentResponse> departmentResponses =
                                    collegeDepartments.stream()
                                            .map(department ->
                                                    new CollegeDepartmentResponse(
                                                            department.getId(),
                                                            department.getName()
                                                    )
                                            )
                                            .toList();

                            long laboratoryCount =
                                    laboratoryCountByCollegeId.getOrDefault(
                                            college.getId(),
                                            0L
                                    );

                            return new CollegeResponse(
                                    college.getId(),
                                    college.getName(),
                                    laboratoryCount,
                                    departmentResponses.size(),
                                    departmentResponses
                            );
                        })
                        .toList();

        return new CollegesResponse(collegeResponses);
    }
}

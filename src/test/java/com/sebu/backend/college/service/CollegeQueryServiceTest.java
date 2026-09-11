package com.sebu.backend.college.service;

import com.sebu.backend.college.domain.College;
import com.sebu.backend.college.repository.CollegeRepository;
import com.sebu.backend.department.domain.Department;
import com.sebu.backend.department.repository.DepartmentRepository;
import com.sebu.backend.laboratory.repository.CollegeLaboratoryCountProjection;
import com.sebu.backend.laboratory.repository.LaboratoryDepartmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CollegeQueryServiceTest {

    @Mock
    CollegeRepository collegeRepository;

    @Mock
    DepartmentRepository departmentRepository;

    @Mock
    LaboratoryDepartmentRepository laboratoryDepartmentRepository;
    @InjectMocks
    CollegeQueryService service;

    @Test
    void returnsCollegesWithDepartmentsAndActiveLaboratoryCount() {
        College college = mock(College.class);
        Department department1 = mock(Department.class);
        Department department2 = mock(Department.class);
        CollegeLaboratoryCountProjection laboratoryCount =
                mock(CollegeLaboratoryCountProjection.class);

        when(college.getId()).thenReturn(1L);
        when(college.getName()).thenReturn("인공지능융합대학");

        when(department1.getId()).thenReturn(101L);
        when(department1.getName()).thenReturn("AI로봇학과");
        when(department1.getCollege()).thenReturn(college);

        when(department2.getId()).thenReturn(102L);
        when(department2.getName()).thenReturn("인공지능학과");
        when(department2.getCollege()).thenReturn(college);

        when(laboratoryCount.getCollegeId()).thenReturn(1L);
        when(laboratoryCount.getLaboratoryCount()).thenReturn(16L);

        when(collegeRepository.findAllByOrderByNameAsc())
                .thenReturn(List.of(college));

        when(departmentRepository.findAllByOrderByNameAsc())
                .thenReturn(List.of(
                        department1,
                        department2
                ));

        when(laboratoryDepartmentRepository.countActiveLaboratoriesByCollege())
                .thenReturn(List.of(laboratoryCount));

        var response = service.getAll();

        assertThat(response.colleges())
                .hasSize(1);

        var result = response.colleges().getFirst();

        assertThat(result.id())
                .isEqualTo(1L);

        assertThat(result.name())
                .isEqualTo("인공지능융합대학");

        assertThat(result.laboratoryCount())
                .isEqualTo(16L);

        assertThat(result.departmentCount())
                .isEqualTo(2);

        assertThat(result.departments())
                .extracting(department -> department.name())
                .containsExactly(
                        "AI로봇학과",
                        "인공지능학과"
                );
    }

    @Test
    void returnsZeroLaboratoryCountWhenCollegeHasNoActiveLaboratory() {
        College college = mock(College.class);

        when(college.getId()).thenReturn(1L);
        when(college.getName()).thenReturn("인공지능융합대학");

        when(collegeRepository.findAllByOrderByNameAsc())
                .thenReturn(List.of(college));

        when(departmentRepository.findAllByOrderByNameAsc())
                .thenReturn(List.of());

        when(laboratoryDepartmentRepository.countActiveLaboratoriesByCollege())
                .thenReturn(List.of());

        var result =
                service.getAll()
                        .colleges()
                        .getFirst();

        assertThat(result.laboratoryCount())
                .isZero();

        assertThat(result.departmentCount())
                .isZero();

        assertThat(result.departments())
                .isEmpty();
    }

    @Test
    void returnsEmptyListWhenNoCollegeExists() {
        when(collegeRepository.findAllByOrderByNameAsc())
                .thenReturn(List.of());

        when(departmentRepository.findAllByOrderByNameAsc())
                .thenReturn(List.of());

        when(laboratoryDepartmentRepository.countActiveLaboratoriesByCollege())
                .thenReturn(List.of());

        assertThat(service.getAll().colleges())
                .isEmpty();
    }
}

package com.sebu.backend.department.service;

import com.sebu.backend.college.domain.College;
import com.sebu.backend.college.repository.CollegeRepository;
import com.sebu.backend.department.domain.Department;
import com.sebu.backend.department.repository.DepartmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AcademicAffiliationResolverTest {
    private final DepartmentRepository departments = mock(DepartmentRepository.class);
    private final CollegeRepository colleges = mock(CollegeRepository.class);
    private final AcademicAffiliationResolver resolver = new AcademicAffiliationResolver(departments, colleges);

    @ParameterizedTest
    @CsvSource({
        "영어영문학전공,인문과학대학",
        "일어일문학전공,인문과학대학",
        "법학전공,사회과학대학",
        "글로벌조리학과,호텔관광대학",
        "수학전공,자연과학대학",
        "응용통계학전공,자연과학대학",
        "전자정보통신공학과,인공지능융합대학",
        "소프트웨어학과,인공지능융합대학",
        "데이터사이언스학과,인공지능융합대학",
        "무인이동체공학전공,인공지능융합대학",
        "스마트기기공학전공,인공지능융합대학",
        "지능기전공학과,인공지능융합대학",
        "인공지능학과,인공지능융합대학",
        "건축공학전공,공과대학",
        "건축학전공,공과대학",
        "환경에너지공간융합학과,공과대학",
        "지구자원시스템공학과,공과대학",
        "기계공학전공,공과대학",
        "항공우주공학전공,공과대학",
        "항공시스템공학전공,공과대학",
        "국방시스템공학과,공과대학"
    })
    void resolvesAllApprovedLegacyNamesWithoutInventingDepartment(String name, String collegeName) {
        College college = new College(collegeName);
        when(colleges.findAllByNameIn(Set.of(collegeName))).thenReturn(List.of(college));

        var result = resolver.resolve(name);

        assertThat(result.department()).isNull();
        assertThat(result.college()).isSameAs(college);
        verify(departments).findAllByNameIn(Set.of(name));
        verifyNoMoreInteractions(departments);
    }

    @Test
    void currentDepartmentWinsEvenWhenLegacyEntryExists() {
        College currentCollege = new College("현재 단과대");
        Department current = new Department(currentCollege, "법학전공");
        when(departments.findAllByNameIn(Set.of("법학전공"))).thenReturn(List.of(current));

        var result = resolver.resolve(" 법학전공 ");

        assertThat(result.department()).isSameAs(current);
        assertThat(result.college()).isSameAs(currentCollege);
        verifyNoInteractions(colleges);
    }

    @Test
    void ambiguousCurrentNameDoesNotFallBackToLegacy() {
        when(departments.findAllByNameIn(Set.of("법학전공"))).thenReturn(List.of(
            new Department(new College("첫째 대학"), "법학전공"),
            new Department(new College("둘째 대학"), "법학전공")));

        var result = resolver.resolve("법학전공");

        assertThat(result.department()).isNull();
        assertThat(result.college()).isNull();
        verifyNoInteractions(colleges);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "자유전공학부", "IT계열", "항공시스템공전공", "무인이동체공학전공/지능기전공학부"})
    void doesNotGuessUnknownNames(String name) {
        var result = resolver.resolve(name);
        assertThat(result.department()).isNull();
        assertThat(result.college()).isNull();
        verifyNoInteractions(colleges);
    }

    @Test
    void missingTargetCollegeRemainsUnresolved() {
        assertThat(resolver.resolve("수학전공").college()).isNull();
    }

    @Test
    void batchesDistinctSchoolNamesAndLegacyCollegesInTwoQueries() {
        College ai = new College("인공지능융합대학");
        Department current = new Department(ai, "컴퓨터공학과");
        when(departments.findAllByNameIn(any())).thenReturn(List.of(current));
        when(colleges.findAllByNameIn(Set.of("인공지능융합대학"))).thenReturn(List.of(ai));

        var results = resolver.resolveAll(List.of(
            "컴퓨터공학과", "무인이동체공학전공", "소프트웨어학과", " 컴퓨터공학과 ", " "));

        assertThat(results).hasSize(3);
        assertThat(results.values()).allSatisfy(result -> assertThat(result.college()).isSameAs(ai));
        verify(departments).findAllByNameIn(Set.of("컴퓨터공학과", "무인이동체공학전공", "소프트웨어학과"));
        verify(colleges).findAllByNameIn(Set.of("인공지능융합대학"));
        verifyNoMoreInteractions(departments, colleges);
    }
}

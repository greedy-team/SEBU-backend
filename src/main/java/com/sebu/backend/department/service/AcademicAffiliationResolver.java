package com.sebu.backend.department.service;

import com.sebu.backend.college.domain.College;
import com.sebu.backend.college.repository.CollegeRepository;
import com.sebu.backend.department.domain.Department;
import com.sebu.backend.department.repository.DepartmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Map.entry;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AcademicAffiliationResolver {
    // Service college classification only, not historical department succession.
    // Current Department matches always take priority. Do not add legacy Department rows.
    private static final Map<String, String> LEGACY_COLLEGES = Map.ofEntries(
        entry("영어영문학전공", "인문과학대학"),
        entry("일어일문학전공", "인문과학대학"),
        entry("법학전공", "사회과학대학"),
        entry("글로벌조리학과", "호텔관광대학"),
        entry("수학전공", "자연과학대학"),
        entry("응용통계학전공", "자연과학대학"),
        entry("전자정보통신공학과", "인공지능융합대학"),
        entry("소프트웨어학과", "인공지능융합대학"),
        entry("데이터사이언스학과", "인공지능융합대학"),
        entry("무인이동체공학전공", "인공지능융합대학"),
        entry("스마트기기공학전공", "인공지능융합대학"),
        entry("지능기전공학과", "인공지능융합대학"),
        entry("인공지능학과", "인공지능융합대학"),
        entry("건축공학전공", "공과대학"),
        entry("건축학전공", "공과대학"),
        entry("환경에너지공간융합학과", "공과대학"),
        entry("지구자원시스템공학과", "공과대학"),
        entry("기계공학전공", "공과대학"),
        entry("항공우주공학전공", "공과대학"),
        entry("항공시스템공학전공", "공과대학"),
        entry("국방시스템공학과", "공과대학")
    );
    private static final Affiliation UNRESOLVED = new Affiliation(null, null, 0);

    private final DepartmentRepository departmentRepository;
    private final CollegeRepository collegeRepository;

    public Affiliation resolve(String schoolName) {
        if (schoolName == null || schoolName.isBlank()) {
            return UNRESOLVED;
        }
        return resolveAll(List.of(schoolName)).get(schoolName.trim());
    }

    // Keys are trimmed school names. At most two queries regardless of author count.
    public Map<String, Affiliation> resolveAll(Collection<String> schoolNames) {
        Set<String> names = schoolNames.stream()
            .filter(Objects::nonNull).map(String::trim).filter(name -> !name.isEmpty())
            .collect(Collectors.toSet());
        if (names.isEmpty()) {
            return Map.of();
        }
        Map<String, List<Department>> current = departmentRepository.findAllByNameIn(names).stream()
            .collect(Collectors.groupingBy(Department::getName));
        Set<String> legacyCollegeNames = names.stream()
            .filter(name -> !current.containsKey(name))
            .map(LEGACY_COLLEGES::get).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<String, College> legacyColleges = legacyCollegeNames.isEmpty() ? Map.of()
            : collegeRepository.findAllByNameIn(legacyCollegeNames).stream()
                .collect(Collectors.toMap(College::getName, college -> college));

        Map<String, Affiliation> result = new HashMap<>();
        for (String name : names) {
            List<Department> matches = current.getOrDefault(name, List.of());
            if (matches.size() == 1) {
                Department department = matches.getFirst();
                result.put(name, new Affiliation(department, department.getCollege(), 1));
            } else if (matches.isEmpty() && LEGACY_COLLEGES.containsKey(name)) {
                result.put(name, new Affiliation(null, legacyColleges.get(LEGACY_COLLEGES.get(name)), 0));
            } else {
                // TODO: classify special admission units only after real authentication evidence.
                // Ambiguous current names must not fall back to a legacy classification.
                result.put(name, new Affiliation(null, null, matches.size()));
            }
        }
        return Map.copyOf(result);
    }

    public record Affiliation(Department department, College college, int currentMatchCount) {
    }
}

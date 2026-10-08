package com.sebu.backend.laboratory.repository;

import com.sebu.backend.college.domain.College;
import com.sebu.backend.college.repository.CollegeRepository;
import com.sebu.backend.department.domain.Department;
import com.sebu.backend.department.repository.DepartmentRepository;
import com.sebu.backend.professor.domain.Professor;
import com.sebu.backend.professor.repository.ProfessorRepository;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ActiveLaboratoryNameConcurrencyTest {
    @Autowired CollegeRepository collegeRepository;
    @Autowired DepartmentRepository departmentRepository;
    @Autowired ProfessorRepository professorRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void concurrentDuplicateRegistrationsStoreOnlyOneActiveLaboratory() throws Exception {
        String suffix = UUID.randomUUID().toString();
        College college = collegeRepository.save(new College("동시성대학-" + suffix));
        Department department = departmentRepository.save(new Department(college, "동시성학과-" + suffix));
        Professor professor = professorRepository.save(new Professor(department, "동시성교수-" + suffix, null));
        String laboratoryName = "동시성연구실-" + suffix;
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Callable<Boolean> insertLaboratory = () -> {
            ready.countDown();
            start.await();
            try {
                jdbcTemplate.update("""
                    INSERT INTO laboratory (
                        professor_id, department_id, name, name_source, recruitment_status
                    ) VALUES (?, ?, ?, 'OFFICIAL', 'UNKNOWN')
                    """, professor.getId(), department.getId(), laboratoryName);
                return true;
            } catch (DataIntegrityViolationException exception) {
                return false;
            }
        };

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<Boolean>> results = List.of(
                executor.submit(insertLaboratory),
                executor.submit(insertLaboratory)
            );
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(results)
                .extracting(result -> result.get(5, TimeUnit.SECONDS))
                .containsExactlyInAnyOrder(true, false);
        }

        Integer storedCount = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM laboratory
            WHERE department_id = ? AND name = ? AND deleted_at IS NULL
            """, Integer.class, department.getId(), laboratoryName);
        assertThat(storedCount).isOne();
    }
}

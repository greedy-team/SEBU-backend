package com.sebu.backend.college.repository;

import com.sebu.backend.college.domain.College;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Collection;

public interface CollegeRepository extends JpaRepository<College, Long> {

    List<College> findAllByNameIn(Collection<String> names);

    List<College> findAllByOrderByNameAsc();
}

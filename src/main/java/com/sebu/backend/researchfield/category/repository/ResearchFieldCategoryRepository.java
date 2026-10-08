package com.sebu.backend.researchfield.category.repository;

import com.sebu.backend.researchfield.category.domain.ResearchFieldCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ResearchFieldCategoryRepository
    extends JpaRepository<ResearchFieldCategory, Long> {

    List<ResearchFieldCategory> findAllByOrderByDisplayOrderAscIdAsc();
}

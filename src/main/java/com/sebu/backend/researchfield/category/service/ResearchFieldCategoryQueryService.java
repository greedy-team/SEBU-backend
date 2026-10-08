package com.sebu.backend.researchfield.category.service;

import com.sebu.backend.researchfield.category.dto.ResearchFieldCategoriesResult;
import com.sebu.backend.researchfield.category.repository.ResearchFieldCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ResearchFieldCategoryQueryService {
    private final ResearchFieldCategoryRepository researchFieldCategoryRepository;

    @Transactional(readOnly = true)
    public ResearchFieldCategoriesResult getAll() {
        return ResearchFieldCategoriesResult.from(
            researchFieldCategoryRepository.findAllByOrderByDisplayOrderAscIdAsc()
        );
    }
}

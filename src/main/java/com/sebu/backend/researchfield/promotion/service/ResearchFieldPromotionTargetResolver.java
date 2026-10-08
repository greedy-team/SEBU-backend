package com.sebu.backend.researchfield.promotion.service;

import com.sebu.backend.researchfield.domain.ResearchField;
import com.sebu.backend.researchfield.promotion.exception.ResearchFieldPromotionException;
import com.sebu.backend.researchfield.repository.ResearchFieldRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ResearchFieldPromotionTargetResolver {
    private final ResearchFieldRepository researchFieldRepository;
    private final ResearchFieldNameNormalizer nameNormalizer;

    ResearchFieldPromotionTarget resolve(String candidateName) {
        String normalizedName = nameNormalizer.normalize(candidateName);
        List<ResearchField> matches = researchFieldRepository
            .findAllByNameIgnoreCaseForUpdate(normalizedName);
        if (matches.size() > 1) {
            throw new ResearchFieldPromotionException(
                "DUPLICATE_CANONICAL_RESEARCH_FIELD"
            );
        }
        if (!matches.isEmpty()) {
            return new ResearchFieldPromotionTarget(matches.getFirst(), false);
        }
        ResearchField created = researchFieldRepository.saveAndFlush(
            new ResearchField(normalizedName)
        );
        return new ResearchFieldPromotionTarget(created, true);
    }
}

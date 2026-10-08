package com.sebu.backend.researchfield.promotion.service;

import com.sebu.backend.laboratory.domain.Laboratory;
import com.sebu.backend.laboratory.domain.LaboratoryResearchField;
import com.sebu.backend.laboratory.domain.LaboratoryResearchFieldId;
import com.sebu.backend.laboratory.repository.LaboratoryResearchFieldRepository;
import com.sebu.backend.researchfield.domain.ResearchField;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LaboratoryResearchFieldLinkService {
    private final LaboratoryResearchFieldRepository linkRepository;

    boolean ensure(Laboratory laboratory, ResearchField researchField) {
        LaboratoryResearchFieldId id = new LaboratoryResearchFieldId(
            laboratory.getId(),
            researchField.getId()
        );
        if (linkRepository.existsById(id)) {
            return false;
        }
        linkRepository.saveAndFlush(
            new LaboratoryResearchField(laboratory, researchField)
        );
        return true;
    }
}

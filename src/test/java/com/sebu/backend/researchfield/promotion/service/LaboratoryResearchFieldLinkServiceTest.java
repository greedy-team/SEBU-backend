package com.sebu.backend.researchfield.promotion.service;

import com.sebu.backend.laboratory.domain.Laboratory;
import com.sebu.backend.laboratory.domain.LaboratoryResearchField;
import com.sebu.backend.laboratory.domain.LaboratoryResearchFieldId;
import com.sebu.backend.laboratory.repository.LaboratoryResearchFieldRepository;
import com.sebu.backend.researchfield.domain.ResearchField;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LaboratoryResearchFieldLinkServiceTest {
    private static final long LABORATORY_ID = 17L;
    private static final long RESEARCH_FIELD_ID = 23L;

    @Mock
    LaboratoryResearchFieldRepository linkRepository;

    @Mock
    Laboratory laboratory;

    @Mock
    ResearchField researchField;

    @InjectMocks
    LaboratoryResearchFieldLinkService service;

    @Test
    void skipsAnExistingLaboratoryResearchFieldLink() {
        LaboratoryResearchFieldId id = linkId();
        when(linkRepository.existsById(id)).thenReturn(true);

        boolean created = service.ensure(laboratory, researchField);

        assertThat(created).isFalse();
        verify(linkRepository, never()).saveAndFlush(any());
    }

    @Test
    void createsAMissingLaboratoryResearchFieldLink() {
        LaboratoryResearchFieldId id = linkId();
        when(linkRepository.existsById(id)).thenReturn(false);

        boolean created = service.ensure(laboratory, researchField);

        ArgumentCaptor<LaboratoryResearchField> captor = ArgumentCaptor.forClass(
            LaboratoryResearchField.class
        );
        verify(linkRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getLaboratory()).isSameAs(laboratory);
        assertThat(captor.getValue().getResearchField()).isSameAs(researchField);
        assertThat(created).isTrue();
    }

    private LaboratoryResearchFieldId linkId() {
        when(laboratory.getId()).thenReturn(LABORATORY_ID);
        when(researchField.getId()).thenReturn(RESEARCH_FIELD_ID);
        return new LaboratoryResearchFieldId(
            LABORATORY_ID,
            RESEARCH_FIELD_ID
        );
    }
}

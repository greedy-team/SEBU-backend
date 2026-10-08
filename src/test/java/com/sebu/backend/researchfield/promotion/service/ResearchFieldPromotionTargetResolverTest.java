package com.sebu.backend.researchfield.promotion.service;

import com.sebu.backend.researchfield.domain.ResearchField;
import com.sebu.backend.researchfield.promotion.exception.ResearchFieldPromotionException;
import com.sebu.backend.researchfield.repository.ResearchFieldRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResearchFieldPromotionTargetResolverTest {
    @Mock
    ResearchFieldRepository researchFieldRepository;

    @Spy
    ResearchFieldNameNormalizer nameNormalizer = new ResearchFieldNameNormalizer();

    @InjectMocks
    ResearchFieldPromotionTargetResolver resolver;

    @Test
    void reusesTheExistingCanonicalResearchField() {
        ResearchField existing = new ResearchField("AI Vision");
        when(researchFieldRepository.findAllByNameIgnoreCaseForUpdate("AI Vision"))
            .thenReturn(List.of(existing));

        ResearchFieldPromotionTarget target = resolver.resolve("  AI  Vision ");

        assertThat(target.researchField()).isSameAs(existing);
        assertThat(target.created()).isFalse();
        verify(researchFieldRepository, never()).saveAndFlush(any());
    }

    @Test
    void createsANormalizedResearchFieldWhenNoCanonicalFieldExists() {
        when(researchFieldRepository.findAllByNameIgnoreCaseForUpdate(
            "Digital Holography"
        )).thenReturn(List.of());
        when(researchFieldRepository.saveAndFlush(any(ResearchField.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        ResearchFieldPromotionTarget target = resolver.resolve(
            " Digital\t Holography "
        );

        ArgumentCaptor<ResearchField> captor = ArgumentCaptor.forClass(
            ResearchField.class
        );
        verify(researchFieldRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("Digital Holography");
        assertThat(target.researchField()).isSameAs(captor.getValue());
        assertThat(target.created()).isTrue();
    }

    @Test
    void rejectsAmbiguousCanonicalResearchFields() {
        when(researchFieldRepository.findAllByNameIgnoreCaseForUpdate("AI"))
            .thenReturn(List.of(new ResearchField("AI"), new ResearchField("ai")));

        assertThatThrownBy(() -> resolver.resolve("AI"))
            .isInstanceOf(ResearchFieldPromotionException.class)
            .hasMessage("DUPLICATE_CANONICAL_RESEARCH_FIELD");

        verify(researchFieldRepository, never()).saveAndFlush(any());
    }
}

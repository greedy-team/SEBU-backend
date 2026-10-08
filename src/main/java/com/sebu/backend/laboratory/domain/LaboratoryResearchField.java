package com.sebu.backend.laboratory.domain;

import com.sebu.backend.researchfield.domain.ResearchField;
import com.sebu.backend.laboratory.domain.Laboratory;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "laboratory_research_field")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LaboratoryResearchField {
    @EmbeddedId
    private LaboratoryResearchFieldId id;

    @MapsId("laboratoryId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "laboratory_id", nullable = false)
    private Laboratory laboratory;

    @MapsId("researchFieldId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "research_field_id", nullable = false)
    private ResearchField researchField;

    public LaboratoryResearchField(Laboratory laboratory, ResearchField researchField) {
        this.laboratory = laboratory;
        this.researchField = researchField;
        this.id = new LaboratoryResearchFieldId(laboratory.getId(), researchField.getId());
    }
}

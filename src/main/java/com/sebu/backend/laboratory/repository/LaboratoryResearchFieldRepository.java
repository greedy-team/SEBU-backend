package com.sebu.backend.laboratory.repository;

import com.sebu.backend.laboratory.domain.LaboratoryResearchField;
import com.sebu.backend.laboratory.domain.LaboratoryResearchFieldId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface LaboratoryResearchFieldRepository extends JpaRepository<LaboratoryResearchField, LaboratoryResearchFieldId> {
    @Query("""
        select lrf.laboratory.id as laboratoryId, rf.id as researchFieldId, rf.name as name
        from LaboratoryResearchField lrf join lrf.researchField rf
        where lrf.laboratory.id in :laboratoryIds
        order by lrf.laboratory.id, rf.name, rf.id
        """)
    List<LaboratoryResearchFieldProjection> findFieldsByLaboratoryIds(@Param("laboratoryIds") Collection<Long> laboratoryIds);
}

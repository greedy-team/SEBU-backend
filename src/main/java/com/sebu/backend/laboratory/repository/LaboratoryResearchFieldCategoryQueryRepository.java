package com.sebu.backend.laboratory.repository;

import com.sebu.backend.researchfield.category.domain.ResearchFieldCategory;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface LaboratoryResearchFieldCategoryQueryRepository
    extends Repository<ResearchFieldCategory, Long> {

    @Query(value = """
        SELECT DISTINCT
            laboratory_field.laboratory_id AS laboratoryId,
            laboratory_field.research_field_id AS researchFieldId,
            category.id AS categoryId,
            category.code AS categoryCode,
            category.name AS categoryName,
            category.display_order AS displayOrder
        FROM laboratory_research_field laboratory_field
        JOIN research_field_category_mapping mapping
          ON mapping.research_field_id = laboratory_field.research_field_id
        JOIN research_field_category category
          ON category.id = mapping.category_id
        WHERE laboratory_field.laboratory_id IN (:laboratoryIds)
        ORDER BY laboratory_field.laboratory_id,
                 category.display_order,
                 category.id,
                 laboratory_field.research_field_id
        """, nativeQuery = true)
    List<LaboratoryResearchFieldCategoryProjection> findAllByLaboratoryIds(
        @Param("laboratoryIds") Collection<Long> laboratoryIds
    );
}

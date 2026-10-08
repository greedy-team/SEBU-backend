package com.sebu.backend.laboratoryreview.repository;

import com.sebu.backend.laboratoryreview.domain.LaboratoryReview;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LaboratoryReviewQueryRepository
        extends JpaRepository<LaboratoryReview, Long> {

    @Query("""
            select r.laboratory.id as laboratoryId,
                   count(r.id) as reviewCount
            from LaboratoryReview r
            where r.laboratory.id in :laboratoryIds
              and r.deletedAt is null
            group by r.laboratory.id
            """)
    List<LaboratoryReviewCountProjection> countActiveReviewsByLaboratoryIds(
            @Param("laboratoryIds") List<Long> laboratoryIds
    );

    @Query(
            value = """
                    select l.id as laboratoryId,
                           count(r.id) as reviewCount
                    from Laboratory l
                    left join LaboratoryReview r
                        on r.laboratory = l
                        and r.deletedAt is null
                    where l.deletedAt is null
                    group by l.id
                    order by count(r.id) desc, l.id desc
                    """,
            countQuery = """
                    select count(l)
                    from Laboratory l
                    where l.deletedAt is null
                    """
    )
    Page<LaboratoryReviewCountPageProjection>
    findLaboratoryIdsByReviewCount(
            Pageable pageable
    );
}

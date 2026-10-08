package com.sebu.backend.laboratoryreview.repository;

import com.sebu.backend.laboratoryreview.domain.LaboratoryReview;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LaboratoryReviewRepository
        extends JpaRepository<LaboratoryReview, Long> {

    boolean existsByLaboratoryIdAndAuthorIdAndDeletedAtIsNull(
            Long laboratoryId,
            Long authorId
    );

    Optional<LaboratoryReview>
    findByLaboratoryIdAndAuthorIdAndDeletedAtIsNull(
            Long laboratoryId,
            Long authorId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select review
        from LaboratoryReview review
        where review.id = :reviewId
          and review.laboratory.id = :laboratoryId
          and review.deletedAt is null
        """)
    Optional<LaboratoryReview> findForUpdate(
            @Param("reviewId") Long reviewId,
            @Param("laboratoryId") Long laboratoryId
    );

    Page<LaboratoryReview>
    findByLaboratoryIdAndDeletedAtIsNull(
            Long laboratoryId,
            Pageable pageable
    );

    List<LaboratoryReview> findAllByLaboratoryIdAndDeletedAtIsNull(
            Long laboratoryId
    );

    @Query(value = """
        SELECT review_id, tag
        FROM laboratory_review_tag
        WHERE review_id IN (:reviewIds)
        ORDER BY review_id
        """, nativeQuery = true)
    List<Object[]> findTagsByReviewIds(
            @Param("reviewIds") List<Long> reviewIds
    );
}

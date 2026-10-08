package com.sebu.backend.crawling.repository;

import com.sebu.backend.crawling.domain.CandidateReviewStatus;
import com.sebu.backend.crawling.domain.ProfessorCrawlCandidate;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProfessorCrawlCandidateRepository extends JpaRepository<ProfessorCrawlCandidate, Long> {
    List<ProfessorCrawlCandidate> findAllByReviewStatusAndStaleFalse(CandidateReviewStatus reviewStatus);
    List<ProfessorCrawlCandidate> findAllBySourceId(Long sourceId);

    @Query("""
        select candidate.id
        from ProfessorCrawlCandidate candidate
        where candidate.reviewStatus = com.sebu.backend.crawling.domain.CandidateReviewStatus.APPROVED
          and candidate.stale = false
          and candidate.reviewedAt is not null
          and (
              candidate.promotedReviewRevision is null
              or candidate.promotedReviewRevision <> candidate.reviewRevision
              or candidate.promotedAt is not null and candidate.promotedProfessor is null
          )
        order by candidate.id
        """)
    List<Long> findPromotionCandidateIds();

    @Query("""
        select candidate.id
        from ProfessorCrawlCandidate candidate
        where candidate.source.id = :sourceId
          and candidate.reviewStatus = com.sebu.backend.crawling.domain.CandidateReviewStatus.APPROVED
          and candidate.stale = false
          and candidate.reviewedAt is not null
          and (
              candidate.promotedReviewRevision is null
              or candidate.promotedReviewRevision <> candidate.reviewRevision
              or candidate.promotedAt is not null and candidate.promotedProfessor is null
          )
        order by candidate.id
        """)
    List<Long> findPromotionCandidateIdsBySourceId(@Param("sourceId") Long sourceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select candidate
        from ProfessorCrawlCandidate candidate
        where candidate.id = :candidateId
        """)
    Optional<ProfessorCrawlCandidate> findByIdForPromotion(
        @Param("candidateId") Long candidateId
    );
}

package com.sebu.backend.researchfield.candidate.repository;

import com.sebu.backend.researchfield.candidate.domain.LaboratoryResearchFieldCandidate;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LaboratoryResearchFieldCandidateRepository
    extends JpaRepository<LaboratoryResearchFieldCandidate, Long> {

    @Query("""
        select candidate.id
        from LaboratoryResearchFieldCandidate candidate
        where candidate.reviewStatus = com.sebu.backend.researchfield.candidate.domain.ResearchFieldCandidateReviewStatus.APPROVED
          and candidate.stale = false
          and candidate.candidateName is not null
          and candidate.reviewedAt is not null
          and candidate.reviewRevision > 0
          and (
              candidate.promotedReviewRevision is null
              or candidate.promotedReviewRevision <> candidate.reviewRevision
              or candidate.promotedAt is not null
                 and candidate.promotedResearchField is null
          )
        order by candidate.id
        """)
    List<Long> findCurrentApprovedCandidateIds();

    @Query("""
        select candidate.id
        from LaboratoryResearchFieldCandidate candidate
        where candidate.laboratory.id = :laboratoryId
          and candidate.reviewStatus = com.sebu.backend.researchfield.candidate.domain.ResearchFieldCandidateReviewStatus.APPROVED
          and candidate.stale = false
          and candidate.candidateName is not null
          and candidate.reviewedAt is not null
          and candidate.reviewRevision > 0
          and (
              candidate.promotedReviewRevision is null
              or candidate.promotedReviewRevision <> candidate.reviewRevision
              or candidate.promotedAt is not null
                 and candidate.promotedResearchField is null
          )
        order by candidate.id
        """)
    List<Long> findCurrentApprovedCandidateIdsByLaboratoryId(
        @Param("laboratoryId") Long laboratoryId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select candidate
        from LaboratoryResearchFieldCandidate candidate
        where candidate.laboratory.id = :laboratoryId
        order by candidate.sourceOrder, candidate.id
        """)
    List<LaboratoryResearchFieldCandidate> findAllByLaboratoryIdForUpdate(
        @Param("laboratoryId") Long laboratoryId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select candidate
        from LaboratoryResearchFieldCandidate candidate
        where candidate.id = :candidateId
        """)
    Optional<LaboratoryResearchFieldCandidate> findByIdForUpdate(
        @Param("candidateId") Long candidateId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select candidate
        from LaboratoryResearchFieldCandidate candidate
        where candidate.id = :candidateId
        """)
    Optional<LaboratoryResearchFieldCandidate> findByIdForPromotion(
        @Param("candidateId") Long candidateId
    );
}

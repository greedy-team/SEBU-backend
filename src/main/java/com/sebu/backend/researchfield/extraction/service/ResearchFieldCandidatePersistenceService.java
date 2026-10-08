package com.sebu.backend.researchfield.extraction.service;

import com.sebu.backend.laboratory.domain.Laboratory;
import com.sebu.backend.researchfield.candidate.domain.LaboratoryResearchFieldCandidate;
import com.sebu.backend.researchfield.candidate.domain.ResearchFieldCandidateDraft;
import com.sebu.backend.researchfield.candidate.repository.LaboratoryResearchFieldCandidateRepository;
import com.sebu.backend.researchfield.extraction.dto.ResearchFieldCandidateReconciliation;
import com.sebu.backend.researchfield.extraction.dto.ResearchFieldExtractionResult;
import com.sebu.backend.researchfield.extraction.exception.ResearchFieldExtractionException;
import com.sebu.backend.researchfield.extraction.repository.ResearchFieldExtractionLaboratoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ResearchFieldCandidatePersistenceService {
    private final ResearchFieldExtractionLaboratoryRepository laboratoryRepository;
    private final LaboratoryResearchFieldCandidateRepository candidateRepository;
    private final ResearchFieldTextExtractor textExtractor;
    private final ResearchFieldTextHasher textHasher;
    private final ResearchFieldCandidateReconciler reconciler;

    public ResearchFieldCandidatePersistenceService(
        ResearchFieldExtractionLaboratoryRepository laboratoryRepository,
        LaboratoryResearchFieldCandidateRepository candidateRepository,
        ResearchFieldTextExtractor textExtractor,
        ResearchFieldTextHasher textHasher,
        ResearchFieldCandidateReconciler reconciler
    ) {
        this.laboratoryRepository = laboratoryRepository;
        this.candidateRepository = candidateRepository;
        this.textExtractor = textExtractor;
        this.textHasher = textHasher;
        this.reconciler = reconciler;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ResearchFieldExtractionResult extract(long laboratoryId) {
        Laboratory laboratory = laboratoryRepository.findByIdForUpdate(laboratoryId)
            .orElseThrow(() -> new ResearchFieldExtractionException(
                "LABORATORY_NOT_FOUND: " + laboratoryId
            ));
        String description = laboratory.isDeleted() ? null : laboratory.getDescription();
        List<ResearchFieldCandidateDraft> extractedFields = textExtractor.extract(description);
        List<LaboratoryResearchFieldCandidate> existingCandidates =
            candidateRepository.findAllByLaboratoryIdForUpdate(laboratoryId);
        LocalDateTime extractedAt = LocalDateTime.now();
        ResearchFieldCandidateReconciliation reconciliation = reconciler.reconcile(
            laboratory,
            extractedFields,
            existingCandidates,
            textHasher.hashSourceDescription(description),
            ResearchFieldTextExtractor.RULE_VERSION,
            extractedAt
        );
        candidateRepository.saveAll(reconciliation.createdCandidates());
        return new ResearchFieldExtractionResult(
            laboratoryId,
            reconciliation.extractedCount(),
            reconciliation.createdCount(),
            reconciliation.refreshedCount(),
            reconciliation.staleCount(),
            reconciliation.unchangedCount()
        );
    }
}

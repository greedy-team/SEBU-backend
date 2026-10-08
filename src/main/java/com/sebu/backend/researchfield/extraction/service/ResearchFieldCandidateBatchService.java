package com.sebu.backend.researchfield.extraction.service;

import com.sebu.backend.researchfield.extraction.dto.ResearchFieldExtractionBatchResult;
import com.sebu.backend.researchfield.extraction.dto.ResearchFieldExtractionResult;
import com.sebu.backend.researchfield.extraction.repository.ResearchFieldExtractionLaboratoryRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ResearchFieldCandidateBatchService {
    private final ResearchFieldExtractionLaboratoryRepository laboratoryRepository;
    private final ResearchFieldCandidatePersistenceService persistenceService;

    public ResearchFieldCandidateBatchService(
        ResearchFieldExtractionLaboratoryRepository laboratoryRepository,
        ResearchFieldCandidatePersistenceService persistenceService
    ) {
        this.laboratoryRepository = laboratoryRepository;
        this.persistenceService = persistenceService;
    }

    public ResearchFieldExtractionBatchResult extract(Long laboratoryId) {
        List<Long> laboratoryIds = laboratoryId == null
            ? laboratoryRepository.findAllLaboratoryIds()
            : List.of(laboratoryId);
        List<ResearchFieldExtractionResult> successes = new ArrayList<>();
        List<ResearchFieldExtractionBatchResult.Failure> failures = new ArrayList<>();

        for (Long targetId : laboratoryIds) {
            try {
                successes.add(persistenceService.extract(targetId));
            } catch (RuntimeException exception) {
                failures.add(new ResearchFieldExtractionBatchResult.Failure(
                    targetId,
                    failureReason(exception),
                    exception
                ));
            }
        }
        return new ResearchFieldExtractionBatchResult(
            laboratoryIds.size(),
            successes,
            failures
        );
    }

    private String failureReason(RuntimeException exception) {
        if (exception.getMessage() == null || exception.getMessage().isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return exception.getMessage();
    }
}

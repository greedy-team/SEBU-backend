package com.sebu.backend.promotion.service;

import com.sebu.backend.crawling.domain.ProfessorCrawlCandidate;
import com.sebu.backend.crawling.repository.CrawlSourceRepository;
import com.sebu.backend.crawling.repository.ProfessorCrawlCandidateRepository;
import com.sebu.backend.department.domain.Department;
import com.sebu.backend.department.repository.DepartmentRepository;
import com.sebu.backend.promotion.dto.PromotionResult;
import com.sebu.backend.promotion.exception.CandidatePromotionException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class ProfessorCandidatePromotionService {
    private final ProfessorCrawlCandidateRepository candidateRepository;
    private final CrawlSourceRepository sourceRepository;
    private final DepartmentRepository departmentRepository;
    private final CandidatePromotionTargetResolver targetResolver;
    private final CandidatePromotionAffiliationService affiliationService;
    private final TransactionTemplate transactionTemplate;

    public ProfessorCandidatePromotionService(
        ProfessorCrawlCandidateRepository candidateRepository,
        CrawlSourceRepository sourceRepository,
        DepartmentRepository departmentRepository,
        CandidatePromotionTargetResolver targetResolver,
        CandidatePromotionAffiliationService affiliationService,
        PlatformTransactionManager transactionManager
    ) {
        this.candidateRepository = candidateRepository;
        this.sourceRepository = sourceRepository;
        this.departmentRepository = departmentRepository;
        this.targetResolver = targetResolver;
        this.affiliationService = affiliationService;
        transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public PromotionResult promote(Long sourceId) {
        validateSource(sourceId);
        List<Long> candidateIds = findCandidateIds(sourceId);
        int createdCount = 0;
        int updatedCount = 0;
        int skippedCount = 0;
        List<PromotionResult.Failure> failures = new ArrayList<>();

        for (Long candidateId : candidateIds) {
            try {
                PromotionAction action = executePromotion(candidateId);
                if (action == PromotionAction.CREATED) {
                    createdCount++;
                } else if (action == PromotionAction.UPDATED) {
                    updatedCount++;
                } else {
                    skippedCount++;
                }
            } catch (RuntimeException exception) {
                failures.add(new PromotionResult.Failure(
                    candidateId,
                    failureReason(exception),
                    exception
                ));
            }
        }

        return new PromotionResult(
            candidateIds.size(),
            createdCount,
            updatedCount,
            skippedCount,
            failures
        );
    }

    private PromotionAction executePromotion(Long candidateId) {
        try {
            return executePromotionTransaction(candidateId);
        } catch (DataIntegrityViolationException firstConflict) {
            return executePromotionTransaction(candidateId);
        }
    }

    private PromotionAction executePromotionTransaction(Long candidateId) {
        return transactionTemplate.execute(status -> promoteCandidate(candidateId));
    }

    private PromotionAction promoteCandidate(Long candidateId) {
        ProfessorCrawlCandidate candidate = candidateRepository.findByIdForPromotion(candidateId)
            .orElse(null);
        if (candidate == null) {
            return PromotionAction.SKIPPED;
        }
        if (!candidate.hasConsistentPromotionState()) {
            throw new CandidatePromotionException("INVALID_CANDIDATE_PROMOTION_STATE");
        }
        if (!candidate.needsPromotion()) {
            return PromotionAction.SKIPPED;
        }

        Department sourceDepartment = lockSourceDepartment(candidate);
        if (candidate.hasBeenPromoted()) {
            return refreshPromotedCandidate(candidate, sourceDepartment);
        }
        return promoteNewCandidate(candidate, sourceDepartment);
    }

    private PromotionAction promoteNewCandidate(
        ProfessorCrawlCandidate candidate,
        Department sourceDepartment
    ) {
        PromotionTargets targets = targetResolver.resolve(candidate);
        affiliationService.ensure(
            targets.professor(),
            targets.laboratory(),
            sourceDepartment,
            candidate.getPosition()
        );
        candidate.recordPromotion(
            targets.professor(),
            targets.laboratory(),
            LocalDateTime.now()
        );
        candidateRepository.save(candidate);
        return targets.canonicalCreated()
            ? PromotionAction.CREATED
            : PromotionAction.UPDATED;
    }

    private PromotionAction refreshPromotedCandidate(
        ProfessorCrawlCandidate candidate,
        Department sourceDepartment
    ) {
        PromotionTargets targets = targetResolver.refresh(candidate);
        boolean affiliationChanged = affiliationService.ensure(
            targets.professor(),
            targets.laboratory(),
            sourceDepartment,
            candidate.getPosition()
        );
        candidate.recordPromotion(
            targets.professor(),
            targets.laboratory(),
            LocalDateTime.now()
        );
        candidateRepository.save(candidate);
        return targets.canonicalUpdated() || affiliationChanged
            ? PromotionAction.UPDATED
            : PromotionAction.SKIPPED;
    }

    private List<Long> findCandidateIds(Long sourceId) {
        if (sourceId == null) {
            return candidateRepository.findPromotionCandidateIds();
        }
        return candidateRepository.findPromotionCandidateIdsBySourceId(sourceId);
    }

    private void validateSource(Long sourceId) {
        if (sourceId != null && !sourceRepository.existsById(sourceId)) {
            throw new CandidatePromotionException("CRAWL_SOURCE_NOT_FOUND: " + sourceId);
        }
    }

    private Department lockSourceDepartment(ProfessorCrawlCandidate candidate) {
        Long departmentId = candidate.getSource().getDepartment().getId();
        if (departmentId == null) {
            throw new CandidatePromotionException("SOURCE_DEPARTMENT_NOT_PERSISTED");
        }
        return departmentRepository.findByIdForUpdate(departmentId)
            .orElseThrow(() -> new CandidatePromotionException(
                "SOURCE_DEPARTMENT_NOT_FOUND"
            ));
    }

    private String failureReason(RuntimeException exception) {
        if (exception instanceof DataIntegrityViolationException) {
            return "PROMOTION_DATA_CONFLICT";
        }
        if (exception.getMessage() == null || exception.getMessage().isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return exception.getMessage();
    }

    private enum PromotionAction {
        CREATED,
        UPDATED,
        SKIPPED
    }
}

package com.sebu.backend.laboratory.service;

import com.sebu.backend.global.auth.CurrentUserProvider;
import com.sebu.backend.laboratory.dto.LaboratoriesPagedResult;
import com.sebu.backend.laboratory.dto.LaboratoriesResult;
import com.sebu.backend.laboratory.dto.LaboratoriesResult.AffiliationResult;
import com.sebu.backend.laboratory.dto.LaboratoriesResult.LaboratoryResult;
import com.sebu.backend.laboratory.dto.LaboratoriesResult.ResearchFieldCategoryResult;
import com.sebu.backend.laboratory.dto.LaboratoriesResult.ResearchFieldResult;
import com.sebu.backend.laboratory.query.LaboratorySummaryAssembler;
import com.sebu.backend.laboratory.repository.LaboratoryAffiliationProjection;
import com.sebu.backend.laboratory.repository.LaboratoryDepartmentRepository;
import com.sebu.backend.laboratory.repository.LaboratoryRepository;
import com.sebu.backend.laboratory.repository.LaboratoryResearchFieldCategoryProjection;
import com.sebu.backend.laboratory.repository.LaboratoryResearchFieldCategoryQueryRepository;
import com.sebu.backend.laboratory.repository.LaboratoryResearchFieldProjection;
import com.sebu.backend.laboratory.repository.LaboratoryResearchFieldRepository;
import com.sebu.backend.laboratory.repository.LaboratorySummaryProjection;
import com.sebu.backend.laboratoryreview.repository.LaboratoryReviewCountPageProjection;
import com.sebu.backend.laboratoryreview.repository.LaboratoryReviewCountProjection;
import com.sebu.backend.laboratoryreview.repository.LaboratoryReviewQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LaboratoryQueryService {

    private final LaboratoryRepository laboratoryRepository;
    private final LaboratoryDepartmentRepository laboratoryDepartmentRepository;
    private final LaboratoryResearchFieldRepository laboratoryResearchFieldRepository;
    private final LaboratoryResearchFieldCategoryQueryRepository
            laboratoryResearchFieldCategoryQueryRepository;
    private final CurrentUserProvider currentUserProvider;
    private final LaboratorySummaryAssembler laboratorySummaryAssembler;
    private final LaboratoryReviewQueryRepository laboratoryReviewQueryRepository;

    @Transactional(readOnly = true)
    public LaboratoriesResult getAll() {
        Long userId = currentUserProvider.currentUserId()
                .orElse(null);

        List<LaboratorySummaryProjection> summaries =
                laboratoryRepository.findAllSummaries(userId);

        if (summaries.isEmpty()) {
            return new LaboratoriesResult(List.of());
        }

        List<LaboratoryResult> laboratories =
                assembleLaboratories(summaries);

        return new LaboratoriesResult(laboratories);
    }

    @Transactional(readOnly = true)
    public LaboratoriesPagedResult getAllByReviewCount(
            int page,
            int size
    ) {
        Long userId = currentUserProvider.currentUserId()
                .orElse(null);

        Page<LaboratoryReviewCountPageProjection> reviewCountPage =
                laboratoryReviewQueryRepository.findLaboratoryIdsByReviewCount(
                        PageRequest.of(page, size)
                );

        Map<Long, Long> reviewCounts =
                reviewCountPage.getContent()
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        LaboratoryReviewCountPageProjection::getLaboratoryId,
                                        LaboratoryReviewCountPageProjection::getReviewCount
                                )
                        );

        List<Long> laboratoryIds =
                reviewCountPage.getContent()
                        .stream()
                        .map(
                                LaboratoryReviewCountPageProjection::getLaboratoryId
                        )
                        .toList();

        if (laboratoryIds.isEmpty()) {
            return new LaboratoriesPagedResult(
                    List.of(),
                    reviewCountPage.getNumber(),
                    reviewCountPage.getSize(),
                    reviewCountPage.getTotalElements(),
                    reviewCountPage.hasNext()
            );
        }

        Map<Long, LaboratorySummaryProjection> summaryById =
                laboratoryRepository
                        .findSummariesByIds(
                                userId,
                                laboratoryIds
                        )
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        LaboratorySummaryProjection::getId,
                                        summary -> summary
                                )
                        );

        List<LaboratorySummaryProjection> orderedSummaries =
                laboratoryIds.stream()
                        .map(summaryById::get)
                        .filter(java.util.Objects::nonNull)
                        .toList();

        List<LaboratoryResult> laboratories =
                assembleLaboratories(
                        orderedSummaries,
                        reviewCounts
                );

        return new LaboratoriesPagedResult(
                laboratories,
                reviewCountPage.getNumber(),
                reviewCountPage.getSize(),
                reviewCountPage.getTotalElements(),
                reviewCountPage.hasNext()
        );
    }

    private List<LaboratoryResult> assembleLaboratories(
            List<LaboratorySummaryProjection> summaries
    ) {
        List<Long> laboratoryIds =
                laboratoryIds(summaries);

        Map<Long, Long> reviewCounts =
                findReviewCounts(laboratoryIds);

        return assembleLaboratories(
                summaries,
                reviewCounts
        );
    }

    private List<LaboratoryResult> assembleLaboratories(
            List<LaboratorySummaryProjection> summaries,
            Map<Long, Long> reviewCounts
    ) {
        List<Long> laboratoryIds =
                laboratoryIds(summaries);

        List<LaboratoryResearchFieldCategoryProjection> categoryMappings =
                laboratoryResearchFieldCategoryQueryRepository
                        .findAllByLaboratoryIds(laboratoryIds);

        Map<Long, List<ResearchFieldResult>> researchFields =
                findResearchFields(laboratoryIds, categoryMappings);

        Map<Long, List<ResearchFieldCategoryResult>>
                researchFieldCategories =
                findResearchFieldCategories(categoryMappings);

        Map<Long, List<AffiliationResult>> affiliations =
                findAffiliations(laboratoryIds);

        return summaries.stream()
                .map(summary ->
                        laboratorySummaryAssembler.assembleWithResearchFieldDetails(
                                summary,
                                researchFields.getOrDefault(
                                        summary.getId(),
                                        List.of()
                                ),
                                researchFieldCategories.getOrDefault(
                                        summary.getId(),
                                        List.of()
                                ),
                                affiliations.getOrDefault(
                                        summary.getId(),
                                        List.of()
                                ),
                                reviewCounts.getOrDefault(
                                        summary.getId(),
                                        0L
                                )
                        )
                )
                .toList();
    }

    private Map<Long, Long> findReviewCounts(
            List<Long> laboratoryIds
    ) {
        if (laboratoryIds.isEmpty()) {
            return Map.of();
        }

        return laboratoryReviewQueryRepository
                .countActiveReviewsByLaboratoryIds(laboratoryIds)
                .stream()
                .collect(
                        Collectors.toMap(
                                LaboratoryReviewCountProjection::getLaboratoryId,
                                LaboratoryReviewCountProjection::getReviewCount
                        )
                );
    }

    private List<Long> laboratoryIds(
            List<LaboratorySummaryProjection> summaries
    ) {
        return summaries.stream()
                .map(LaboratorySummaryProjection::getId)
                .distinct()
                .toList();
    }

    private Map<Long, List<ResearchFieldResult>> findResearchFields(
            List<Long> laboratoryIds,
            List<LaboratoryResearchFieldCategoryProjection> categoryMappings
    ) {
        Map<ResearchFieldKey, List<Long>> categoryIdsByField =
                categoryMappings.stream()
                        .collect(Collectors.groupingBy(
                                category -> new ResearchFieldKey(
                                        category.getLaboratoryId(),
                                        category.getResearchFieldId()
                                ),
                                Collectors.mapping(
                                        LaboratoryResearchFieldCategoryProjection::getCategoryId,
                                        Collectors.collectingAndThen(
                                                Collectors.toCollection(LinkedHashSet::new),
                                                List::copyOf
                                        )
                                )
                        ));

        return laboratoryResearchFieldRepository
                .findFieldsByLaboratoryIds(laboratoryIds)
                .stream()
                .collect(
                        Collectors.groupingBy(
                                LaboratoryResearchFieldProjection::getLaboratoryId,
                                LinkedHashMap::new,
                                Collectors.mapping(
                                        field -> new ResearchFieldResult(
                                                field.getResearchFieldId(),
                                                field.getName(),
                                                categoryIdsByField.getOrDefault(
                                                        new ResearchFieldKey(
                                                                field.getLaboratoryId(),
                                                                field.getResearchFieldId()
                                                        ),
                                                        List.of()
                                                )
                                        ),
                                        Collectors.toList()
                                )
                        )
                );
    }

    private Map<Long, List<ResearchFieldCategoryResult>>
    findResearchFieldCategories(
            List<LaboratoryResearchFieldCategoryProjection> categoryMappings
    ) {
        Map<Long, LinkedHashMap<Long, ResearchFieldCategoryResult>>
                categoriesByLaboratory =
                new LinkedHashMap<>();

        for (LaboratoryResearchFieldCategoryProjection category : categoryMappings) {

            categoriesByLaboratory
                    .computeIfAbsent(
                            category.getLaboratoryId(),
                            ignored -> new LinkedHashMap<>()
                    )
                    .computeIfAbsent(
                            category.getCategoryId(),
                            ignored -> toCategoryResult(category)
                    );
        }

        Map<Long, List<ResearchFieldCategoryResult>> results =
                new LinkedHashMap<>();

        categoriesByLaboratory.forEach(
                (laboratoryId, categories) ->
                        results.put(
                                laboratoryId,
                                List.copyOf(categories.values())
                        )
        );

        return results;
    }

    private ResearchFieldCategoryResult toCategoryResult(
            LaboratoryResearchFieldCategoryProjection category
    ) {
        return new ResearchFieldCategoryResult(
                category.getCategoryId(),
                category.getCategoryCode(),
                category.getCategoryName()
        );
    }

    private record ResearchFieldKey(Long laboratoryId, Long researchFieldId) {
    }

    private Map<Long, List<AffiliationResult>> findAffiliations(
            List<Long> laboratoryIds
    ) {
        return laboratoryDepartmentRepository
                .findAffiliationsByLaboratoryIds(laboratoryIds)
                .stream()
                .collect(
                        Collectors.groupingBy(
                                LaboratoryAffiliationProjection::getLaboratoryId,
                                LinkedHashMap::new,
                                Collectors.mapping(
                                        this::toAffiliationResult,
                                        Collectors.toList()
                                )
                        )
                );
    }

    private AffiliationResult toAffiliationResult(
            LaboratoryAffiliationProjection affiliation
    ) {
        return new AffiliationResult(
                new LaboratoriesResult.CollegeResult(
                        affiliation.getCollegeId(),
                        affiliation.getCollegeName()
                ),
                new LaboratoriesResult.DepartmentResult(
                        affiliation.getDepartmentId(),
                        affiliation.getDepartmentName()
                )
        );
    }

}

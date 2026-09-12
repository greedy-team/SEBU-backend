package com.sebu.backend.laboratoryreview.service;

import com.sebu.backend.global.auth.ActiveUserCommandGuard;
import com.sebu.backend.laboratory.domain.Laboratory;
import com.sebu.backend.laboratory.exception.LaboratoryNotFoundException;
import com.sebu.backend.laboratory.repository.LaboratoryRepository;
import com.sebu.backend.laboratoryreview.domain.LaboratoryReview;
import com.sebu.backend.laboratoryreview.dto.LaboratoryReviewCreateRequest;
import com.sebu.backend.laboratoryreview.dto.LaboratoryReviewCreateResponse;
import com.sebu.backend.laboratoryreview.dto.LaboratoryReviewDeleteResponse;
import com.sebu.backend.laboratoryreview.dto.LaboratoryReviewListResponse;
import com.sebu.backend.laboratoryreview.dto.LaboratoryReviewMeResponse;
import com.sebu.backend.laboratoryreview.dto.LaboratoryReviewSummaryResponse;
import com.sebu.backend.laboratoryreview.dto.LaboratoryReviewUpdateRequest;
import com.sebu.backend.laboratoryreview.dto.LaboratoryReviewUpdateResponse;
import com.sebu.backend.laboratoryreview.exception.InvalidReviewPageException;
import com.sebu.backend.laboratoryreview.exception.InvalidReviewSizeException;
import com.sebu.backend.laboratoryreview.exception.LaboratoryReviewAlreadyExistsException;
import com.sebu.backend.laboratoryreview.exception.LaboratoryReviewForbiddenException;
import com.sebu.backend.laboratoryreview.exception.LaboratoryReviewNotFoundException;
import com.sebu.backend.laboratoryreview.repository.LaboratoryReviewRepository;
import com.sebu.backend.user.domain.AppUser;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LaboratoryReviewService {

    private static final String ACTIVE_REVIEW_UNIQUE_CONSTRAINT =
            "uk_laboratory_review_active_author_laboratory";

    private final LaboratoryReviewRepository laboratoryReviewRepository;
    private final LaboratoryRepository laboratoryRepository;
    private final ActiveUserCommandGuard activeUserGuard;

    @Transactional
    public LaboratoryReviewCreateResponse createReview(
            Long laboratoryId,
            Long userId,
            LaboratoryReviewCreateRequest request
    ) {
        AppUser author = activeUserGuard.lock(userId);
        Laboratory laboratory = findActiveLaboratoryForUpdate(laboratoryId);

        boolean alreadyExists =
                laboratoryReviewRepository
                        .existsByLaboratoryIdAndAuthorIdAndDeletedAtIsNull(
                                laboratoryId,
                                userId
                        );

        if (alreadyExists) {
            throw new LaboratoryReviewAlreadyExistsException();
        }

        LaboratoryReview review = new LaboratoryReview(
                laboratory,
                author,
                request.category(),
                request.researchIntensity(),
                request.compensation(),
                request.atmosphere(),
                request.tags(),
                request.content(),
                request.participationYear(),
                request.participationTerm()
        );

        LaboratoryReview saved;

        try {
            saved = laboratoryReviewRepository.saveAndFlush(review);
        } catch (DataIntegrityViolationException exception) {
            if (isActiveReviewUniqueConstraintViolation(exception)) {
                throw new LaboratoryReviewAlreadyExistsException();
            }
            throw exception;
        }

        return new LaboratoryReviewCreateResponse(
                saved.getId()
        );
    }

    @Transactional(readOnly = true)
    public LaboratoryReviewListResponse getReviews(
            Long laboratoryId,
            Long currentUserId,
            int page,
            int size
    ) {
        validatePagination(page, size);

        Laboratory laboratory =
                findActiveLaboratory(laboratoryId);

        PageRequest pageable = PageRequest.of(
                page,
                size,
                Sort.by(
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("id")
                )
        );

        Page<LaboratoryReview> reviewPage =
                laboratoryReviewRepository
                        .findByLaboratoryIdAndDeletedAtIsNull(
                                laboratoryId,
                                pageable
                        );

        List<LaboratoryReview> reviewEntities =
                reviewPage.getContent();

        List<Long> reviewIds =
                reviewEntities.stream()
                        .map(LaboratoryReview::getId)
                        .toList();

        Map<Long, List<String>> tagsByReviewId;

        if (reviewIds.isEmpty()) {
            tagsByReviewId = Map.of();
        } else {
            tagsByReviewId =
                    laboratoryReviewRepository
                            .findTagsByReviewIds(reviewIds)
                            .stream()
                            .collect(
                                    Collectors.groupingBy(
                                            row -> ((Number) row[0]).longValue(),
                                            LinkedHashMap::new,
                                            Collectors.mapping(
                                                    row -> (String) row[1],
                                                    Collectors.toList()
                                            )
                                    )
                            );
        }

        List<LaboratoryReviewListResponse.ReviewItem> reviews =
                reviewEntities.stream()
                        .map(review ->
                                LaboratoryReviewListResponse.ReviewItem.from(
                                        review,
                                        tagsByReviewId.getOrDefault(
                                                review.getId(),
                                                List.of()
                                        )
                                )
                        )
                        .toList();

        boolean reviewedByMe =
                currentUserId != null
                        && laboratoryReviewRepository
                        .existsByLaboratoryIdAndAuthorIdAndDeletedAtIsNull(
                                laboratoryId,
                                currentUserId
                        );

        return new LaboratoryReviewListResponse(
                LaboratoryReviewListResponse
                        .LaboratoryInfo
                        .from(laboratory),
                reviewedByMe,
                reviews,
                reviewPage.getNumber(),
                reviewPage.getSize(),
                reviewPage.getTotalElements(),
                reviewPage.hasNext()
        );
    }

    @Transactional(readOnly = true)
    public LaboratoryReviewMeResponse getMyReview(
            Long laboratoryId,
            Long userId
    ) {
        findActiveLaboratory(laboratoryId);

        LaboratoryReview review =
                laboratoryReviewRepository
                        .findByLaboratoryIdAndAuthorIdAndDeletedAtIsNull(
                                laboratoryId,
                                userId
                        )
                        .orElseThrow(
                                LaboratoryReviewNotFoundException::new
                        );

        return LaboratoryReviewMeResponse.from(review);
    }

    @Transactional
    public LaboratoryReviewUpdateResponse updateReview(
            Long laboratoryId,
            Long reviewId,
            Long userId,
            LaboratoryReviewUpdateRequest request
    ) {
        activeUserGuard.lock(userId);
        findActiveLaboratoryForUpdate(laboratoryId);

        LaboratoryReview review =
                findActiveReview(
                        laboratoryId,
                        reviewId
                );

        if (!review.isWrittenBy(userId)) {
            throw new LaboratoryReviewForbiddenException();
        }

        review.update(
                request.category(),
                request.researchIntensity(),
                request.compensation(),
                request.atmosphere(),
                request.tags(),
                request.content(),
                request.participationYear(),
                request.participationTerm()
        );

        laboratoryReviewRepository.flush();

        return new LaboratoryReviewUpdateResponse(
                review.getId(),
                review.getUpdatedAt()
        );
    }

    @Transactional
    public LaboratoryReviewDeleteResponse deleteReview(
            Long laboratoryId,
            Long reviewId,
            Long userId
    ) {
        activeUserGuard.lock(userId);
        findActiveLaboratoryForUpdate(laboratoryId);

        LaboratoryReview review =
                findActiveReview(
                        laboratoryId,
                        reviewId
                );

        if (!review.isWrittenBy(userId)) {
            throw new LaboratoryReviewForbiddenException();
        }

        review.softDelete();

        return new LaboratoryReviewDeleteResponse(
                review.getId()
        );
    }

    private Laboratory findActiveLaboratory(
            Long laboratoryId
    ) {
        return laboratoryRepository
                .findById(laboratoryId)
                .filter(laboratory -> !laboratory.isDeleted())
                .orElseThrow(
                        LaboratoryNotFoundException::new
                );
    }

    private Laboratory findActiveLaboratoryForUpdate(Long laboratoryId) {
        return laboratoryRepository.findByIdForUpdate(laboratoryId)
                .filter(laboratory -> !laboratory.isDeleted())
                .orElseThrow(LaboratoryNotFoundException::new);
    }

    private LaboratoryReview findActiveReview(
            Long laboratoryId,
            Long reviewId
    ) {
        return laboratoryReviewRepository
                .findForUpdate(
                        reviewId,
                        laboratoryId
                )
                .orElseThrow(
                        LaboratoryReviewNotFoundException::new
                );
    }

    private void validatePagination(
            int page,
            int size
    ) {
        if (page < 0) {
            throw new InvalidReviewPageException();
        }

        if (size < 1 || size > 50) {
            throw new InvalidReviewSizeException();
        }
    }

    private boolean isActiveReviewUniqueConstraintViolation(
            Throwable exception
    ) {
        Throwable cause = exception;

        while (cause != null) {
            if (cause instanceof ConstraintViolationException constraintViolation
                    && ACTIVE_REVIEW_UNIQUE_CONSTRAINT.equalsIgnoreCase(
                    constraintViolation.getConstraintName()
            )) {
                return true;
            }

            String message = cause.getMessage();

            if (message != null
                    && message.toLowerCase(Locale.ROOT).contains(
                    ACTIVE_REVIEW_UNIQUE_CONSTRAINT
            )) {
                return true;
            }

            cause = cause.getCause();
        }

        return false;
    }

    @Transactional(readOnly = true)
    public LaboratoryReviewSummaryResponse getReviewSummary(
            Long laboratoryId
    ) {
        Laboratory laboratory =
                findActiveLaboratory(laboratoryId);

        List<LaboratoryReview> reviews =
                laboratoryReviewRepository
                        .findAllByLaboratoryIdAndDeletedAtIsNull(
                                laboratoryId
                        );

        long reviewCount = reviews.size();

        return new LaboratoryReviewSummaryResponse(
                new LaboratoryReviewSummaryResponse.LaboratoryInfo(
                        laboratory.getId(),
                        laboratory.getName(),

                        new LaboratoryReviewSummaryResponse.ProfessorInfo(
                                laboratory.getProfessor().getId(),
                                laboratory.getProfessor().getName()
                        ),

                        new LaboratoryReviewSummaryResponse.CollegeInfo(
                                laboratory.getDepartment()
                                        .getCollege()
                                        .getId(),
                                laboratory.getDepartment()
                                        .getCollege()
                                        .getName()
                        ),

                        new LaboratoryReviewSummaryResponse.DepartmentInfo(
                                laboratory.getDepartment().getId(),
                                laboratory.getDepartment().getName()
                        )
                ),

                reviewCount,

                new LaboratoryReviewSummaryResponse.EvaluationDistributions(
                        createEnumDistribution(
                                reviews,
                                LaboratoryReview::getResearchIntensity
                        ),
                        createEnumDistribution(
                                reviews,
                                LaboratoryReview::getCompensation
                        ),
                        createEnumDistribution(
                                reviews,
                                LaboratoryReview::getAtmosphere
                        )
                )
        );
    }

    private <E extends Enum<E>>
    List<LaboratoryReviewSummaryResponse.EvaluationDistribution>
    createEnumDistribution(
            List<LaboratoryReview> reviews,
            Function<LaboratoryReview, E> extractor
    ) {
        if (reviews.isEmpty()) {
            return List.of();
        }

        Map<E, Long> counts =
                reviews.stream()
                        .map(extractor)
                        .collect(
                                Collectors.groupingBy(
                                        Function.identity(),
                                        LinkedHashMap::new,
                                        Collectors.counting()
                                )
                        );

        long total = reviews.size();

        return counts.entrySet()
                .stream()
                .map(entry ->
                        new LaboratoryReviewSummaryResponse
                                .EvaluationDistribution(
                                entry.getKey().name(),
                                entry.getValue(),
                                entry.getValue() * 100.0 / total
                        )
                )
                .toList();
    }
}

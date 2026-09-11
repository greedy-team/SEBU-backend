package com.sebu.backend.laboratoryreview;

import com.sebu.backend.laboratory.domain.Laboratory;
import com.sebu.backend.laboratory.repository.LaboratoryRepository;
import com.sebu.backend.laboratoryreview.domain.Atmosphere;
import com.sebu.backend.laboratoryreview.domain.Compensation;
import com.sebu.backend.laboratoryreview.domain.LaboratoryReview;
import com.sebu.backend.laboratoryreview.domain.LaboratoryReviewCategory;
import com.sebu.backend.laboratoryreview.domain.ParticipationTerm;
import com.sebu.backend.laboratoryreview.domain.ResearchIntensity;
import com.sebu.backend.laboratoryreview.dto.LaboratoryReviewCreateRequest;
import com.sebu.backend.laboratoryreview.dto.LaboratoryReviewUpdateRequest;
import com.sebu.backend.laboratoryreview.exception.LaboratoryReviewAlreadyExistsException;
import com.sebu.backend.laboratoryreview.repository.LaboratoryReviewRepository;
import com.sebu.backend.laboratoryreview.service.LaboratoryReviewService;
import com.sebu.backend.global.auth.ActiveUserCommandGuard;
import com.sebu.backend.user.domain.AppUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LaboratoryReviewServicePersistenceTest {

    @Mock
    LaboratoryReviewRepository laboratoryReviewRepository;

    @Mock
    LaboratoryRepository laboratoryRepository;

    @Mock
    ActiveUserCommandGuard activeUserGuard;

    @InjectMocks
    LaboratoryReviewService laboratoryReviewService;

    @Test
    void 활성_후기_유일_제약_충돌은_중복_후기_오류로_변환한다() {
        prepareCreateDependencies();
        doThrow(new DataIntegrityViolationException(
                "uk_laboratory_review_active_author_laboratory"
        )).when(laboratoryReviewRepository).saveAndFlush(any());

        assertThatThrownBy(() -> laboratoryReviewService.createReview(
                1L,
                2L,
                createRequest()
        )).isInstanceOf(LaboratoryReviewAlreadyExistsException.class);
    }

    @Test
    void 다른_DB_무결성_오류는_중복_후기_오류로_변환하지_않는다() {
        prepareCreateDependencies();
        DataIntegrityViolationException databaseFailure =
                new DataIntegrityViolationException(
                        "ck_laboratory_review_participation_year"
                );
        doThrow(databaseFailure)
                .when(laboratoryReviewRepository)
                .saveAndFlush(any());

        assertThatThrownBy(() -> laboratoryReviewService.createReview(
                1L,
                2L,
                createRequest()
        )).isSameAs(databaseFailure);
    }

    @Test
    void 수정_응답은_flush로_갱신된_시각을_반환한다() {
        Laboratory laboratory = mock(Laboratory.class);
        LaboratoryReview review = mock(LaboratoryReview.class);
        LocalDateTime updatedAt = LocalDateTime.of(2026, 9, 1, 12, 0);

        when(laboratoryRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(laboratory));
        when(laboratoryReviewRepository
                .findForUpdate(3L, 1L))
                .thenReturn(Optional.of(review));
        when(review.isWrittenBy(2L)).thenReturn(true);
        when(review.getId()).thenReturn(3L);
        when(review.getUpdatedAt()).thenReturn(updatedAt);

        var response = laboratoryReviewService.updateReview(
                1L,
                3L,
                2L,
                updateRequest()
        );

        InOrder persistenceOrder = inOrder(
                review,
                laboratoryReviewRepository
        );
        persistenceOrder.verify(review).update(
                LaboratoryReviewCategory.RESEARCH_ENVIRONMENT,
                ResearchIntensity.LOW,
                Compensation.SUFFICIENT,
                Atmosphere.COOPERATIVE,
                Set.of(),
                "수정 응답 시각 검증을 위한 충분히 긴 후기 내용입니다.",
                2026,
                ParticipationTerm.FIRST_SEMESTER
        );
        persistenceOrder.verify(laboratoryReviewRepository).flush();
        persistenceOrder.verify(review).getUpdatedAt();

        assertThat(response.reviewId()).isEqualTo(3L);
        assertThat(response.updatedAt()).isEqualTo(updatedAt);
    }

    private void prepareCreateDependencies() {
        Laboratory laboratory = mock(Laboratory.class);
        AppUser author = mock(AppUser.class);

        when(laboratoryRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(laboratory));
        when(activeUserGuard.lock(2L)).thenReturn(author);
        when(laboratoryReviewRepository
                .existsByLaboratoryIdAndAuthorIdAndDeletedAtIsNull(1L, 2L))
                .thenReturn(false);
    }

    private LaboratoryReviewCreateRequest createRequest() {
        return new LaboratoryReviewCreateRequest(
                LaboratoryReviewCategory.RESEARCH_ENVIRONMENT,
                ResearchIntensity.LOW,
                Compensation.SUFFICIENT,
                Atmosphere.COOPERATIVE,
                Set.of(),
                "DB 충돌 변환을 확인하기 위한 충분히 긴 후기 내용입니다.",
                2026,
                ParticipationTerm.FIRST_SEMESTER
        );
    }

    private LaboratoryReviewUpdateRequest updateRequest() {
        return new LaboratoryReviewUpdateRequest(
                LaboratoryReviewCategory.RESEARCH_ENVIRONMENT,
                ResearchIntensity.LOW,
                Compensation.SUFFICIENT,
                Atmosphere.COOPERATIVE,
                Set.of(),
                "수정 응답 시각 검증을 위한 충분히 긴 후기 내용입니다.",
                2026,
                ParticipationTerm.FIRST_SEMESTER
        );
    }
}

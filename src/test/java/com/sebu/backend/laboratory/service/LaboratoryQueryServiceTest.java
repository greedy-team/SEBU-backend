package com.sebu.backend.laboratory.service;

import com.sebu.backend.global.auth.CurrentUserProvider;
import com.sebu.backend.laboratory.domain.LaboratoryNameSource;
import com.sebu.backend.laboratory.domain.RecruitmentStatus;
import com.sebu.backend.laboratory.query.LaboratorySummaryAssembler;
import com.sebu.backend.laboratory.repository.LaboratoryAffiliationProjection;
import com.sebu.backend.laboratory.repository.LaboratoryDepartmentRepository;
import com.sebu.backend.laboratory.repository.LaboratoryRepository;
import com.sebu.backend.laboratory.repository.LaboratoryResearchFieldCategoryProjection;
import com.sebu.backend.laboratory.repository.LaboratoryResearchFieldCategoryQueryRepository;
import com.sebu.backend.laboratory.repository.LaboratoryResearchFieldRepository;
import com.sebu.backend.laboratory.repository.LaboratoryResearchFieldProjection;
import com.sebu.backend.laboratory.repository.LaboratorySummaryProjection;
import com.sebu.backend.laboratoryreview.repository.LaboratoryReviewQueryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LaboratoryQueryServiceTest {

    @Mock
    LaboratoryRepository laboratoryRepository;

    @Mock
    LaboratoryDepartmentRepository laboratoryDepartmentRepository;

    @Mock
    LaboratoryResearchFieldRepository laboratoryResearchFieldRepository;

    @Mock
    LaboratoryResearchFieldCategoryQueryRepository
            laboratoryResearchFieldCategoryQueryRepository;

    @Mock
    LaboratoryReviewQueryRepository laboratoryReviewQueryRepository;

    @Mock
    CurrentUserProvider currentUserProvider;

    @Mock
    LaboratorySummaryProjection summary;

    @Mock
    LaboratoryAffiliationProjection primaryAffiliation;

    @Mock
    LaboratoryAffiliationProjection secondaryAffiliation;

    @Mock
    LaboratoryResearchFieldCategoryProjection categoryProjection;

    @Mock
    LaboratoryResearchFieldCategoryProjection duplicateCategoryProjection;

    @Mock
    LaboratoryResearchFieldProjection researchField;

    @Mock
    LaboratoryResearchFieldProjection secondResearchField;

    @Spy
    LaboratorySummaryAssembler laboratorySummaryAssembler =
            new LaboratorySummaryAssembler();

    @InjectMocks
    LaboratoryQueryService service;

    @Test
    void mapsGeneratedLaboratoryNameSourceToQueryResult() {
        when(currentUserProvider.currentUserId())
                .thenReturn(Optional.empty());

        when(laboratoryRepository.findAllSummaries(null))
                .thenReturn(List.of(summary));

        when(summary.getId()).thenReturn(1L);
        when(summary.getName())
                .thenReturn("김교수 교수님 연구실");
        when(summary.getNameSource())
                .thenReturn(LaboratoryNameSource.GENERATED);
        when(summary.getProfessorId()).thenReturn(2L);
        when(summary.getProfessorName()).thenReturn("김교수");
        when(summary.getCollegeId()).thenReturn(3L);
        when(summary.getCollegeName())
                .thenReturn("인공지능융합대학");
        when(summary.getDepartmentId()).thenReturn(4L);
        when(summary.getDepartmentName())
                .thenReturn("컴퓨터공학과");
        when(summary.getRecruitmentStatus())
                .thenReturn(RecruitmentStatus.UNKNOWN);
        when(summary.getBookmarkCount()).thenReturn(0L);
        when(summary.getBookmarked()).thenReturn(false);

        /*
         * 후기 수는 별도 batch query로 조회한다.
         * 현재 테스트 연구실에는 후기가 없으므로 빈 결과를 반환한다.
         */
        when(laboratoryReviewQueryRepository
                .countActiveReviewsByLaboratoryIds(
                        List.of(1L)
                ))
                .thenReturn(List.of());

        when(laboratoryResearchFieldRepository
                .findFieldsByLaboratoryIds(
                        List.of(1L)
                ))
                .thenReturn(List.of(researchField, secondResearchField));

        when(researchField.getLaboratoryId()).thenReturn(1L);
        when(researchField.getResearchFieldId()).thenReturn(101L);
        when(researchField.getName()).thenReturn("머신러닝");
        when(secondResearchField.getLaboratoryId()).thenReturn(1L);
        when(secondResearchField.getResearchFieldId()).thenReturn(102L);
        when(secondResearchField.getName()).thenReturn("인공지능");

        when(laboratoryResearchFieldCategoryQueryRepository
                .findAllByLaboratoryIds(
                        List.of(1L)
                ))
                .thenReturn(
                        List.of(
                                categoryProjection,
                                duplicateCategoryProjection
                        )
                );

        when(laboratoryDepartmentRepository
                .findAffiliationsByLaboratoryIds(
                        List.of(1L)
                ))
                .thenReturn(
                        List.of(
                                primaryAffiliation,
                                secondaryAffiliation
                        )
                );

        mockCategory(
                categoryProjection,
                1L,
                10L,
                "AI_ML",
                "인공지능·기계학습"
        );

        when(duplicateCategoryProjection.getLaboratoryId())
                .thenReturn(1L);

        when(categoryProjection.getResearchFieldId()).thenReturn(101L);
        when(duplicateCategoryProjection.getResearchFieldId()).thenReturn(102L);

        when(duplicateCategoryProjection.getCategoryId())
                .thenReturn(10L);

        mockAffiliation(
                primaryAffiliation,
                1L,
                3L,
                "인공지능융합대학",
                4L,
                "컴퓨터공학과"
        );

        mockAffiliation(
                secondaryAffiliation,
                1L,
                3L,
                "인공지능융합대학",
                5L,
                "정보보호학과"
        );

        var laboratory =
                service.getAll()
                        .laboratories()
                        .getFirst();

        assertThat(laboratory.name())
                .isEqualTo("김교수 교수님 연구실");

        assertThat(laboratory.nameSource())
                .isEqualTo(LaboratoryNameSource.GENERATED);

        assertThat(laboratory.department().name())
                .isEqualTo("컴퓨터공학과");

        assertThat(laboratory.affiliations())
                .extracting(
                        affiliation ->
                                affiliation.department().name()
                )
                .containsExactly(
                        "컴퓨터공학과",
                        "정보보호학과"
                );

        assertThat(laboratory.researchFieldCategories())
                .extracting(
                        category -> category.code()
                )
                .containsExactly("AI_ML");

        assertThat(laboratory.researchFieldDetails())
                .extracting(field -> field.researchFieldId())
                .containsExactly(101L, 102L);
        assertThat(laboratory.researchFieldDetails())
                .allSatisfy(field -> assertThat(field.categoryIds()).containsExactly(10L));
        assertThat(laboratory.researchFields())
                .containsExactly("머신러닝", "인공지능");

        assertThat(laboratory.reviewCount())
                .isZero();
    }

    @Test
    void skipsBatchQueriesWhenNoLaboratoryExists() {
        when(currentUserProvider.currentUserId())
                .thenReturn(Optional.empty());

        when(laboratoryRepository.findAllSummaries(null))
                .thenReturn(List.of());

        assertThat(service.getAll().laboratories())
                .isEmpty();

        verifyNoInteractions(
                laboratoryResearchFieldRepository,
                laboratoryResearchFieldCategoryQueryRepository,
                laboratoryDepartmentRepository,
                laboratoryReviewQueryRepository
        );
    }

    private void mockAffiliation(
            LaboratoryAffiliationProjection affiliation,
            Long laboratoryId,
            Long collegeId,
            String collegeName,
            Long departmentId,
            String departmentName
    ) {
        when(affiliation.getLaboratoryId())
                .thenReturn(laboratoryId);

        when(affiliation.getCollegeId())
                .thenReturn(collegeId);

        when(affiliation.getCollegeName())
                .thenReturn(collegeName);

        when(affiliation.getDepartmentId())
                .thenReturn(departmentId);

        when(affiliation.getDepartmentName())
                .thenReturn(departmentName);
    }

    private void mockCategory(
            LaboratoryResearchFieldCategoryProjection category,
            Long laboratoryId,
            Long categoryId,
            String categoryCode,
            String categoryName
    ) {
        when(category.getLaboratoryId())
                .thenReturn(laboratoryId);

        when(category.getCategoryId())
                .thenReturn(categoryId);

        when(category.getCategoryCode())
                .thenReturn(categoryCode);

        when(category.getCategoryName())
                .thenReturn(categoryName);
    }
}

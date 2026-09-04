package com.sebu.backend.researchfield.extraction.service;

import com.sebu.backend.researchfield.candidate.domain.ResearchFieldCandidateDraft;
import com.sebu.backend.researchfield.candidate.domain.ResearchFieldExtractionMethod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ResearchFieldTextExtractorTest {
    private ResearchFieldTextExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new ResearchFieldTextExtractor(new ResearchFieldTextHasher());
    }

    @Test
    void splitsOnlyTopLevelListSeparatorsAndPreservesTechnicalAliases() {
        List<ResearchFieldCandidateDraft> fields = extractor.extract(
            "기계학습, Video Coding (HEVC, VVC, post VVC); 5G/6G • VR/AR"
        );

        assertThat(fields)
            .extracting(ResearchFieldCandidateDraft::rawFieldText)
            .containsExactly(
                "기계학습",
                "Video Coding (HEVC, VVC, post VVC)",
                "5G/6G",
                "VR/AR"
            );
        assertThat(fields)
            .extracting(ResearchFieldCandidateDraft::extractionMethod)
            .containsOnly(ResearchFieldExtractionMethod.DELIMITED);
    }

    @Test
    void supportsNewlinesAndSpacedSlashButKeepsUnspacedSlash() {
        List<ResearchFieldCandidateDraft> fields = extractor.extract(
            "AI/ML/FL\n컴퓨터 비전 / 영상처리"
        );

        assertThat(fields)
            .extracting(ResearchFieldCandidateDraft::rawFieldText)
            .containsExactly("AI/ML/FL", "컴퓨터 비전", "영상처리");
    }

    @Test
    void splitsRepeatedSpacedHyphenListsWithoutBreakingHyphenatedTerms() {
        List<ResearchFieldCandidateDraft> fields = extractor.extract(
            "*주요연구분야 - On-device AI - AI-RAN - 프라이버시-보존 머신러닝"
        );

        assertThat(fields)
            .extracting(ResearchFieldCandidateDraft::rawFieldText)
            .containsExactly("On-device AI", "AI-RAN", "프라이버시-보존 머신러닝");
    }

    @Test
    void splitsSingleSpacedHyphenAndSentencePeriodsButPreservesUnspacedMiddleDots() {
        List<ResearchFieldCandidateDraft> fields = extractor.extract(
            "기계/시스템 AI - 가상센서. 설계·합성"
        );

        assertThat(fields)
            .extracting(ResearchFieldCandidateDraft::rawFieldText)
            .containsExactly("기계/시스템 AI", "가상센서", "설계·합성");
    }

    @Test
    void splitsInlineNumberedListsWithoutKeepingTheirMarkers() {
        List<ResearchFieldCandidateDraft> fields = extractor.extract(
            "1. 그래핀-고분자 복합재료 개발 2. 원자힘 현미경 연구 "
                + "3. 고분자 화학 4. 하이드로겔 입자 제조 5. 나노소재"
        );

        assertThat(fields)
            .extracting(ResearchFieldCandidateDraft::rawFieldText)
            .containsExactly(
                "그래핀-고분자 복합재료 개발",
                "원자힘 현미경 연구",
                "고분자 화학",
                "하이드로겔 입자 제조",
                "나노소재"
            );
    }

    @Test
    void preservesDecimalAndTechnicalVersionNumbersInsideNumberedFields() {
        List<ResearchFieldCandidateDraft> fields = extractor.extract(
            "1. Web 3.0 기반 시스템 2. TLS 1.3 보안 3. 모델 2.5 분석"
        );

        assertThat(fields)
            .extracting(ResearchFieldCandidateDraft::rawFieldText)
            .containsExactly(
                "Web 3.0 기반 시스템",
                "TLS 1.3 보안",
                "모델 2.5 분석"
            );
    }

    @Test
    void normalizesUnicodeWhitespaceAndRemovesDuplicatesInSourceOrder() {
        List<ResearchFieldCandidateDraft> fields = extractor.extract(
            "ＡＩ   시스템, AI 시스템, 로보틱스"
        );

        assertThat(fields)
            .extracting(ResearchFieldCandidateDraft::rawFieldText)
            .containsExactly("AI 시스템", "로보틱스");
        assertThat(fields)
            .extracting(ResearchFieldCandidateDraft::sourceOrder)
            .containsExactly(0, 1);
        assertThat(fields)
            .allSatisfy(field -> assertThat(field.sourceFieldKey())
                .matches("[0-9a-f]{64}"));
    }

    @Test
    void preservesLongOrNarrativeTextButLeavesItsCandidateNameForManualReview() {
        List<ResearchFieldCandidateDraft> fields = extractor.extract(
            "본 연구실에서는 자율주행 환경 인식 연구를 수행하고 있습니다."
        );

        assertThat(fields).singleElement().satisfies(field -> {
            assertThat(field.rawFieldText())
                .isEqualTo("본 연구실에서는 자율주행 환경 인식 연구를 수행하고 있습니다");
            assertThat(field.candidateName()).isNull();
            assertThat(field.extractionMethod()).isEqualTo(
                ResearchFieldExtractionMethod.LONG_TEXT
            );
        });
    }

    @Test
    void keepsAnEntireNarrativeDescriptionTogetherEvenWhenItContainsCommas() {
        List<ResearchFieldCandidateDraft> fields = extractor.extract(
            "본 연구실에서는 카메라, 레이더, 라이다를 융합하여 연구를 수행하고 있습니다."
        );

        assertThat(fields).singleElement().satisfies(field -> {
            assertThat(field.rawFieldText())
                .isEqualTo(
                    "본 연구실에서는 카메라, 레이더, 라이다를 융합하여 연구를 수행하고 있습니다"
                );
            assertThat(field.candidateName()).isNull();
            assertThat(field.extractionMethod()).isEqualTo(
                ResearchFieldExtractionMethod.LONG_TEXT
            );
        });
    }

    @Test
    void leavesAFieldLongerThanTheSafeSuggestionLimitForManualNaming() {
        String unstructuredField = "A".repeat(81);

        assertThat(extractor.extract(unstructuredField))
            .singleElement()
            .satisfies(field -> {
                assertThat(field.rawFieldText()).isEqualTo(unstructuredField);
                assertThat(field.candidateName()).isNull();
                assertThat(field.extractionMethod()).isEqualTo(
                    ResearchFieldExtractionMethod.LONG_TEXT
                );
            });
    }

    @Test
    void returnsNoFieldsWhenDescriptionIsMissing() {
        assertThat(extractor.extract(null)).isEmpty();
        assertThat(extractor.extract("   ")).isEmpty();
    }
}

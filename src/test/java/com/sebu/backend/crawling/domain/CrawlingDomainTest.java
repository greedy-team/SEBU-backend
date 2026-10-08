package com.sebu.backend.crawling.domain;

import com.sebu.backend.college.domain.College;
import com.sebu.backend.department.domain.Department;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CrawlingDomainTest {
    private static final LocalDateTime CRAWLED_AT = LocalDateTime.of(2026, 8, 17, 1, 0);
    private static final LocalDateTime REVIEWED_AT = LocalDateTime.of(2026, 8, 17, 2, 0);

    @Test
    void failedReviewerValidationLeavesCandidatePending() {
        ProfessorCrawlCandidate candidate = candidate(completeData());

        assertThatThrownBy(() -> candidate.approve(" ", "확인", REVIEWED_AT))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("REVIEWER_REQUIRED");

        assertThat(candidate.getReviewStatus()).isEqualTo(CandidateReviewStatus.PENDING);
        assertThat(candidate.getReviewedBy()).isNull();
        assertThat(candidate.getReviewNote()).isNull();
        assertThat(candidate.getReviewedAt()).isNull();
    }

    @Test
    void candidateWithoutLaboratoryNameCanBeApprovedForGeneratedNaming() {
        ProfessorCrawlCandidate candidate = candidate(new ProfessorCrawlData(
            "홍길동",
            "교수",
            "professor@sejong.ac.kr",
            null,
            "인공지능 연구",
            "https://example.com/lab"
        ));

        candidate.approve("developer", null, REVIEWED_AT);

        assertThat(candidate.getReviewStatus()).isEqualTo(CandidateReviewStatus.APPROVED);
        assertThat(candidate.getLaboratoryName()).isNull();
        assertThat(candidate.needsPromotion()).isTrue();
    }

    @Test
    void staleCandidateCannotBeReviewedUntilItAppearsAgain() {
        ProfessorCrawlData data = completeData();
        ProfessorCrawlCandidate candidate = candidate(data);
        candidate.markStale();

        assertThatThrownBy(() -> candidate.approve("developer", null, REVIEWED_AT))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("STALE_CANDIDATE_NOT_REVIEWABLE");

        candidate.refreshFromCrawl(
            data,
            CrawlSourceProvenance.from(candidate.getSource()),
            CRAWLED_AT.plusDays(1)
        );

        assertThat(candidate.isStale()).isFalse();
        assertThat(candidate.getReviewStatus()).isEqualTo(CandidateReviewStatus.PENDING);
    }

    @Test
    void changedRecrawlResetsReviewAndRefreshesCandidate() {
        ProfessorCrawlCandidate candidate = candidate(completeData());
        candidate.approve("developer", "확인 완료", REVIEWED_AT);
        LocalDateTime recrawledAt = CRAWLED_AT.plusDays(1);

        candidate.refreshFromCrawl(
            new ProfessorCrawlData(
                "홍길동",
                "교수",
                "professor@sejong.ac.kr",
                "AI 연구실",
                "변경된 연구 소개",
                "https://example.com/lab"
            ),
            CrawlSourceProvenance.from(candidate.getSource()),
            recrawledAt
        );

        assertThat(candidate.getResearchIntroduction()).isEqualTo("변경된 연구 소개");
        assertThat(candidate.getCrawledAt()).isEqualTo(recrawledAt);
        assertThat(candidate.getReviewStatus()).isEqualTo(CandidateReviewStatus.PENDING);
        assertThat(candidate.getReviewedBy()).isNull();
        assertThat(candidate.getReviewNote()).isNull();
        assertThat(candidate.getReviewedAt()).isNull();
    }

    @Test
    void sourceSnapshotPreservesTheOriginalUrlUntilARecrawlUsesTheNewConfiguration() {
        CrawlSource source = source();
        ProfessorCrawlData data = completeData();
        ProfessorCrawlCandidate candidate = new ProfessorCrawlCandidate(
            source,
            data,
            CrawlSourceProvenance.from(source),
            CRAWLED_AT
        );
        String originalUrl = source.getSourceUrl();
        candidate.approve("developer", "확인 완료", REVIEWED_AT);

        source.rename("컴퓨터공학과 새 교수진");
        source.changeEndpoint(
            "https://dept.sejong.ac.kr/new-professors",
            CrawlParserType.SEJONG_QUANTUM
        );

        assertThat(candidate.getSourceUrlAtCrawl()).isEqualTo(originalUrl);
        assertThat(candidate.getParserTypeAtCrawl()).isEqualTo(CrawlParserType.SEJONG_STANDARD);
        assertThat(candidate.getReviewStatus()).isEqualTo(CandidateReviewStatus.APPROVED);

        candidate.refreshFromCrawl(
            data,
            CrawlSourceProvenance.from(source),
            CRAWLED_AT.plusDays(1)
        );

        assertThat(candidate.getSourceUrlAtCrawl())
            .isEqualTo("https://dept.sejong.ac.kr/new-professors");
        assertThat(candidate.getParserTypeAtCrawl()).isEqualTo(CrawlParserType.SEJONG_QUANTUM);
        assertThat(candidate.getReviewStatus()).isEqualTo(CandidateReviewStatus.PENDING);
    }

    @Test
    void unchangedRecrawlKeepsCompletedReview() {
        ProfessorCrawlData data = completeData();
        ProfessorCrawlCandidate candidate = candidate(data);
        candidate.approve("developer", "확인 완료", REVIEWED_AT);
        LocalDateTime recrawledAt = CRAWLED_AT.plusDays(1);

        candidate.refreshFromCrawl(
            data,
            CrawlSourceProvenance.from(candidate.getSource()),
            recrawledAt
        );

        assertThat(candidate.getReviewStatus()).isEqualTo(CandidateReviewStatus.APPROVED);
        assertThat(candidate.getReviewedBy()).isEqualTo("developer");
        assertThat(candidate.getReviewedAt()).isEqualTo(REVIEWED_AT);
        assertThat(candidate.getCrawledAt()).isEqualTo(recrawledAt);
    }

    @Test
    void changingSourceEndpointResetsPreviousCrawlResult() {
        CrawlSource source = source();
        source.markFailed(CRAWLED_AT, "연결 실패");
        source.deactivate();

        source.rename(" 컴퓨터공학과 새 교수진 ");
        source.changeEndpoint(
            " https://dept.sejong.ac.kr/new-professors ",
            CrawlParserType.SEJONG_QUANTUM
        );
        source.activate();

        assertThat(source.getSourceName()).isEqualTo("컴퓨터공학과 새 교수진");
        assertThat(source.getSourceUrl()).isEqualTo("https://dept.sejong.ac.kr/new-professors");
        assertThat(source.getParserType()).isEqualTo(CrawlParserType.SEJONG_QUANTUM);
        assertThat(source.getLastCrawlStatus()).isEqualTo(CrawlSourceStatus.NOT_STARTED);
        assertThat(source.getLastCrawledAt()).isNull();
        assertThat(source.getLastErrorMessage()).isNull();
        assertThat(source.isActive()).isTrue();
    }

    @Test
    void renamingSourceDoesNotResetTheLastCrawlResult() {
        CrawlSource source = source();
        source.markSucceeded(CRAWLED_AT);

        source.rename(" 컴퓨터공학과 교수 목록 ");

        assertThat(source.getSourceName()).isEqualTo("컴퓨터공학과 교수 목록");
        assertThat(source.getLastCrawlStatus()).isEqualTo(CrawlSourceStatus.SUCCESS);
        assertThat(source.getLastCrawledAt()).isEqualTo(CRAWLED_AT);
    }

    @Test
    void manualRevisionDoesNotSilentlyChangeTheCrawlIdentity() {
        ProfessorCrawlCandidate candidate = candidate(completeData());
        String identityBeforeRevision = candidate.getSourceIdentityKey();

        candidate.revise(new ProfessorCrawlData(
            "홍길동",
            "교수",
            "corrected@sejong.ac.kr",
            "AI 연구실",
            "인공지능 연구",
            "https://example.com/lab"
        ));

        assertThat(candidate.getEmail()).isEqualTo("corrected@sejong.ac.kr");
        assertThat(candidate.getSourceIdentityKey()).isEqualTo(identityBeforeRevision);
    }

    private ProfessorCrawlCandidate candidate(ProfessorCrawlData data) {
        CrawlSource source = source();
        return new ProfessorCrawlCandidate(
            source,
            data,
            CrawlSourceProvenance.from(source),
            CRAWLED_AT
        );
    }

    private CrawlSource source() {
        College college = new College("인공지능융합대학");
        Department department = new Department(college, "컴퓨터공학과");
        return new CrawlSource(
            department,
            "컴퓨터공학과 교수진",
            "https://dept.sejong.ac.kr/cedpt/intro/professor.do",
            CrawlParserType.SEJONG_STANDARD
        );
    }

    private ProfessorCrawlData completeData() {
        return new ProfessorCrawlData(
            "홍길동",
            "교수",
            "PROFESSOR@SEJONG.AC.KR",
            "AI 연구실",
            "인공지능 연구",
            "https://example.com/lab"
        );
    }
}

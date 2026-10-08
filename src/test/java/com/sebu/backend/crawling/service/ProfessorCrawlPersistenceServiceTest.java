package com.sebu.backend.crawling.service;

import com.sebu.backend.crawling.dto.CrawlSourceRun;
import com.sebu.backend.crawling.dto.ProfessorCrawlResult;
import com.sebu.backend.crawling.exception.ProfessorCrawlException;
import com.sebu.backend.crawling.domain.CandidateReviewStatus;
import com.sebu.backend.crawling.domain.CrawlSource;
import com.sebu.backend.crawling.repository.CrawlSourceRepository;
import com.sebu.backend.crawling.domain.CrawlSourceStatus;
import com.sebu.backend.crawling.domain.CrawlParserType;
import com.sebu.backend.crawling.domain.ProfessorCrawlCandidate;
import com.sebu.backend.crawling.repository.ProfessorCrawlCandidateRepository;
import com.sebu.backend.crawling.domain.ProfessorCrawlData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class ProfessorCrawlPersistenceServiceTest {
    private static final String COMPUTER_SCIENCE_URL =
        "https://dept.sejong.ac.kr/cedpt/intro/professor.do";

    @Autowired
    ProfessorCrawlPersistenceService persistenceService;

    @Autowired
    CrawlSourceRepository crawlSourceRepository;

    @Autowired
    ProfessorCrawlCandidateRepository candidateRepository;

    @Test
    void insertsNewCandidatesAndRefreshesThemWithoutDuplicates() {
        CrawlSource source = crawlSourceRepository.findBySourceUrl(COMPUTER_SCIENCE_URL)
            .orElseThrow();
        LocalDateTime firstCrawledAt = LocalDateTime.of(2026, 8, 17, 10, 0);
        LocalDateTime secondCrawledAt = firstCrawledAt.plusHours(1);
        CrawlSourceRun sourceRun = CrawlSourceRun.from(source);

        ProfessorCrawlResult firstResult = persistenceService.saveSuccessful(
            sourceRun,
            List.of(data("홍길동", "인공지능"), data("김철수", "보안")),
            firstCrawledAt
        );
        ProfessorCrawlResult secondResult = persistenceService.saveSuccessful(
            sourceRun,
            List.of(data("홍길동", "생성형 인공지능")),
            secondCrawledAt
        );
        candidateRepository.flush();

        List<ProfessorCrawlCandidate> candidates = candidateRepository.findAllBySourceId(source.getId());
        assertThat(firstResult.createdCount()).isEqualTo(2);
        assertThat(firstResult.refreshedCount()).isZero();
        assertThat(secondResult.createdCount()).isZero();
        assertThat(secondResult.refreshedCount()).isEqualTo(1);
        assertThat(secondResult.staleCount()).isEqualTo(1);
        assertThat(candidates).hasSize(2);
        assertThat(candidates)
            .filteredOn(candidate -> candidate.getProfessorName().equals("홍길동"))
            .singleElement()
            .satisfies(candidate -> {
                assertThat(candidate.getResearchIntroduction()).isEqualTo("생성형 인공지능");
                assertThat(candidate.getReviewStatus()).isEqualTo(CandidateReviewStatus.PENDING);
                assertThat(candidate.getCrawledAt()).isEqualTo(secondCrawledAt);
                assertThat(candidate.isStale()).isFalse();
                assertThat(candidate.getSourceUrlAtCrawl()).isEqualTo(COMPUTER_SCIENCE_URL);
            });
        assertThat(candidates)
            .filteredOn(candidate -> candidate.getProfessorName().equals("김철수"))
            .singleElement()
            .satisfies(candidate -> {
                assertThat(candidate.isStale()).isTrue();
                assertThat(candidate.getCrawledAt()).isEqualTo(firstCrawledAt);
            });
        assertThat(source.getLastCrawlStatus()).isEqualTo(CrawlSourceStatus.SUCCESS);
        assertThat(source.getLastCrawledAt()).isEqualTo(secondCrawledAt);
    }

    @Test
    void rejectsResultsWhenTheSourceConfigurationChangedDuringFetch() {
        CrawlSource source = crawlSourceRepository.findBySourceUrl(COMPUTER_SCIENCE_URL)
            .orElseThrow();
        CrawlSourceRun startedRun = CrawlSourceRun.from(source);
        source.changeEndpoint(
            "https://dept.sejong.ac.kr/changed/professors",
            CrawlParserType.SEJONG_STANDARD
        );

        assertThatThrownBy(() -> persistenceService.saveSuccessful(
            startedRun,
            List.of(data("홍길동", "인공지능")),
            LocalDateTime.of(2026, 8, 17, 10, 0)
        ))
            .isInstanceOf(ProfessorCrawlException.class)
            .hasMessage("CRAWL_SOURCE_CONFIGURATION_CHANGED: " + source.getId());
    }

    @Test
    void collapsesExactDuplicatesButKeepsProfessorsWhoShareAName() {
        CrawlSource source = crawlSourceRepository.findBySourceUrl(COMPUTER_SCIENCE_URL)
            .orElseThrow();
        ProfessorCrawlData firstProfessor = new ProfessorCrawlData(
            "김민수",
            "교수",
            "first@sejong.ac.kr",
            null,
            "보안 연구",
            null
        );
        ProfessorCrawlData professorWithSameName = new ProfessorCrawlData(
            "김민수",
            "부교수",
            "second@sejong.ac.kr",
            null,
            "인공지능 연구",
            null
        );

        ProfessorCrawlResult result = persistenceService.saveSuccessful(
            CrawlSourceRun.from(source),
            List.of(firstProfessor, firstProfessor, professorWithSameName),
            LocalDateTime.of(2026, 8, 17, 10, 0)
        );
        candidateRepository.flush();

        assertThat(result.crawledCount()).isEqualTo(2);
        assertThat(result.createdCount()).isEqualTo(2);
        assertThat(candidateRepository.findAllBySourceId(source.getId()))
            .extracting(ProfessorCrawlCandidate::getProfessorName)
            .containsExactlyInAnyOrder("김민수", "김민수");
    }

    @Test
    void rejectsDifferentPayloadsThatClaimTheSameIdentity() {
        CrawlSource source = crawlSourceRepository.findBySourceUrl(COMPUTER_SCIENCE_URL)
            .orElseThrow();
        ProfessorCrawlData original = new ProfessorCrawlData(
            "홍길동",
            "교수",
            "same@sejong.ac.kr",
            null,
            "기존 연구",
            null
        );
        ProfessorCrawlData conflict = new ProfessorCrawlData(
            "홍길동",
            "교수",
            "same@sejong.ac.kr",
            null,
            "서로 다른 연구",
            null
        );

        assertThatThrownBy(() -> persistenceService.saveSuccessful(
            CrawlSourceRun.from(source),
            List.of(original, conflict),
            LocalDateTime.of(2026, 8, 17, 10, 0)
        ))
            .isInstanceOf(ProfessorCrawlException.class)
            .hasMessageStartingWith("CONFLICTING_DUPLICATE_PROFESSOR_IDENTITY: email:");
    }

    @Test
    void keepsTheSameCandidateWhenResearchChangesWithoutContactInformation() {
        CrawlSource source = crawlSourceRepository.findBySourceUrl(COMPUTER_SCIENCE_URL)
            .orElseThrow();
        CrawlSourceRun sourceRun = CrawlSourceRun.from(source);
        LocalDateTime firstCrawledAt = LocalDateTime.of(2026, 8, 17, 10, 0);

        persistenceService.saveSuccessful(
            sourceRun,
            List.of(dataWithoutContact("홍길동", "기존 연구")),
            firstCrawledAt
        );
        ProfessorCrawlResult secondResult = persistenceService.saveSuccessful(
            sourceRun,
            List.of(dataWithoutContact("홍길동", "변경된 연구")),
            firstCrawledAt.plusHours(1)
        );
        candidateRepository.flush();

        assertThat(secondResult.createdCount()).isZero();
        assertThat(secondResult.refreshedCount()).isEqualTo(1);
        assertThat(candidateRepository.findAllBySourceId(source.getId()))
            .singleElement()
            .satisfies(candidate -> {
                assertThat(candidate.getSourceIdentityKey()).startsWith("name:");
                assertThat(candidate.getResearchIntroduction()).isEqualTo("변경된 연구");
            });
    }

    @Test
    void reconcilesAnEmailThatAppearsAndIsTemporarilyMissing() {
        CrawlSource source = crawlSourceRepository.findBySourceUrl(COMPUTER_SCIENCE_URL)
            .orElseThrow();
        CrawlSourceRun sourceRun = CrawlSourceRun.from(source);
        LocalDateTime firstCrawledAt = LocalDateTime.of(2026, 8, 17, 10, 0);
        ProfessorCrawlData withoutEmail = dataWithoutContact("홍길동", "인공지능 연구");
        ProfessorCrawlData withEmail = new ProfessorCrawlData(
            "홍길동",
            "교수",
            "professor@sejong.ac.kr",
            null,
            "인공지능 연구",
            null
        );

        persistenceService.saveSuccessful(sourceRun, List.of(withoutEmail), firstCrawledAt);
        ProfessorCrawlResult emailAppeared = persistenceService.saveSuccessful(
            sourceRun,
            List.of(withEmail),
            firstCrawledAt.plusHours(1)
        );
        ProfessorCrawlResult emailMissing = persistenceService.saveSuccessful(
            sourceRun,
            List.of(withoutEmail),
            firstCrawledAt.plusHours(2)
        );
        candidateRepository.flush();

        assertThat(emailAppeared.createdCount()).isZero();
        assertThat(emailMissing.createdCount()).isZero();
        assertThat(candidateRepository.findAllBySourceId(source.getId()))
            .singleElement()
            .satisfies(candidate -> {
                assertThat(candidate.getSourceIdentityKey())
                    .isEqualTo("email:professor@sejong.ac.kr");
                assertThat(candidate.getEmail()).isNull();
                assertThat(candidate.isStale()).isFalse();
            });
    }

    @Test
    void rejectsSameNameProfessorsWhenThePageProvidesNoStableIdentifier() {
        CrawlSource source = crawlSourceRepository.findBySourceUrl(COMPUTER_SCIENCE_URL)
            .orElseThrow();

        assertThatThrownBy(() -> persistenceService.saveSuccessful(
            CrawlSourceRun.from(source),
            List.of(
                dataWithoutContact("홍길동", "인공지능 연구"),
                dataWithoutContact("홍길동", "보안 연구")
            ),
            LocalDateTime.of(2026, 8, 17, 10, 0)
        ))
            .isInstanceOf(ProfessorCrawlException.class)
            .hasMessageStartingWith("CONFLICTING_DUPLICATE_PROFESSOR_IDENTITY: name:");
    }

    @Test
    void reconcilesALegacyMigrationKeyWithoutCreatingADuplicate() {
        CrawlSource source = crawlSourceRepository.findBySourceUrl(COMPUTER_SCIENCE_URL)
            .orElseThrow();
        CrawlSourceRun sourceRun = CrawlSourceRun.from(source);
        LocalDateTime firstCrawledAt = LocalDateTime.of(2026, 8, 17, 10, 0);
        ProfessorCrawlData professor = new ProfessorCrawlData(
            "홍길동",
            "교수",
            "professor@sejong.ac.kr",
            null,
            "인공지능 연구",
            null
        );

        persistenceService.saveSuccessful(sourceRun, List.of(professor), firstCrawledAt);
        candidateRepository.flush();
        ProfessorCrawlCandidate candidate = candidateRepository.findAllBySourceId(source.getId())
            .getFirst();
        candidate.reidentify("legacy:" + candidate.getId());
        candidateRepository.flush();

        ProfessorCrawlResult result = persistenceService.saveSuccessful(
            sourceRun,
            List.of(professor),
            firstCrawledAt.plusHours(1)
        );
        candidateRepository.flush();

        assertThat(result.createdCount()).isZero();
        assertThat(result.refreshedCount()).isEqualTo(1);
        assertThat(candidateRepository.findAllBySourceId(source.getId()))
            .singleElement()
            .extracting(ProfessorCrawlCandidate::getSourceIdentityKey)
            .isEqualTo("email:professor@sejong.ac.kr");
    }

    @Test
    void reservesExactMatchesBeforeUsingASharedHomepageAlias() {
        CrawlSource source = crawlSourceRepository.findBySourceUrl(COMPUTER_SCIENCE_URL)
            .orElseThrow();
        CrawlSourceRun sourceRun = CrawlSourceRun.from(source);
        LocalDateTime firstCrawledAt = LocalDateTime.of(2026, 8, 17, 10, 0);
        ProfessorCrawlData existingProfessor = identifiedData(
            "기존교수",
            "existing@sejong.ac.kr",
            "https://example.com/shared-lab"
        );
        ProfessorCrawlData newProfessor = identifiedData(
            "신규교수",
            "new@sejong.ac.kr",
            "https://example.com/shared-lab"
        );

        persistenceService.saveSuccessful(
            sourceRun,
            List.of(existingProfessor),
            firstCrawledAt
        );
        candidateRepository.flush();
        ProfessorCrawlCandidate existingCandidate = candidateRepository
            .findAllBySourceId(source.getId())
            .getFirst();
        Long existingCandidateId = existingCandidate.getId();
        existingCandidate.approve("reviewer", "검수 완료", firstCrawledAt.plusMinutes(30));
        candidateRepository.flush();

        persistenceService.saveSuccessful(
            sourceRun,
            List.of(newProfessor, existingProfessor),
            firstCrawledAt.plusHours(1)
        );
        candidateRepository.flush();

        List<ProfessorCrawlCandidate> candidates = candidateRepository.findAllBySourceId(source.getId());
        assertThat(candidates).hasSize(2);
        assertThat(candidates)
            .filteredOn(candidate -> candidate.getEmail().equals("existing@sejong.ac.kr"))
            .singleElement()
            .satisfies(candidate -> {
                assertThat(candidate.getId()).isEqualTo(existingCandidateId);
                assertThat(candidate.getReviewStatus()).isEqualTo(CandidateReviewStatus.APPROVED);
                assertThat(candidate.getReviewedBy()).isEqualTo("reviewer");
            });
        assertThat(candidates)
            .filteredOn(candidate -> candidate.getEmail().equals("new@sejong.ac.kr"))
            .singleElement()
            .extracting(ProfessorCrawlCandidate::getReviewStatus)
            .isEqualTo(CandidateReviewStatus.PENDING);
    }

    @Test
    void doesNotBridgeALegacyCandidateToAConflictingEmailByNameAlone() {
        CrawlSource source = crawlSourceRepository.findBySourceUrl(COMPUTER_SCIENCE_URL)
            .orElseThrow();
        CrawlSourceRun sourceRun = CrawlSourceRun.from(source);
        LocalDateTime firstCrawledAt = LocalDateTime.of(2026, 8, 17, 10, 0);
        ProfessorCrawlData oldProfessor = identifiedData(
            "동명이인",
            "old@sejong.ac.kr",
            null
        );
        ProfessorCrawlData newProfessor = identifiedData(
            "동명이인",
            "new@sejong.ac.kr",
            null
        );

        persistenceService.saveSuccessful(sourceRun, List.of(oldProfessor), firstCrawledAt);
        candidateRepository.flush();
        ProfessorCrawlCandidate oldCandidate = candidateRepository.findAllBySourceId(source.getId())
            .getFirst();
        Long oldCandidateId = oldCandidate.getId();
        oldCandidate.approve("reviewer", "검수 완료", firstCrawledAt.plusMinutes(30));
        oldCandidate.reidentify("legacy:" + oldCandidateId);
        candidateRepository.flush();

        persistenceService.saveSuccessful(
            sourceRun,
            List.of(newProfessor),
            firstCrawledAt.plusHours(1)
        );
        candidateRepository.flush();

        List<ProfessorCrawlCandidate> candidates = candidateRepository.findAllBySourceId(source.getId());
        assertThat(candidates).hasSize(2);
        assertThat(candidates)
            .filteredOn(candidate -> candidate.getId().equals(oldCandidateId))
            .singleElement()
            .satisfies(candidate -> {
                assertThat(candidate.getEmail()).isEqualTo("old@sejong.ac.kr");
                assertThat(candidate.getReviewStatus()).isEqualTo(CandidateReviewStatus.APPROVED);
                assertThat(candidate.isStale()).isTrue();
            });
        assertThat(candidates)
            .filteredOn(candidate -> "new@sejong.ac.kr".equals(candidate.getEmail()))
            .singleElement()
            .satisfies(candidate -> {
                assertThat(candidate.getReviewStatus()).isEqualTo(CandidateReviewStatus.PENDING);
                assertThat(candidate.isStale()).isFalse();
            });
    }

    @Test
    void doesNotTransferReviewHistoryToADifferentProfessorOnASharedHomepage() {
        CrawlSource source = crawlSourceRepository.findBySourceUrl(COMPUTER_SCIENCE_URL)
            .orElseThrow();
        CrawlSourceRun sourceRun = CrawlSourceRun.from(source);
        LocalDateTime firstCrawledAt = LocalDateTime.of(2026, 8, 17, 10, 0);
        String sharedHomepage = "https://example.com/shared-lab";
        ProfessorCrawlData departedProfessor = identifiedData(
            "퇴임교수",
            "departed@sejong.ac.kr",
            sharedHomepage
        );
        ProfessorCrawlData successor = identifiedData(
            "신임교수",
            "successor@sejong.ac.kr",
            sharedHomepage
        );

        persistenceService.saveSuccessful(
            sourceRun,
            List.of(departedProfessor),
            firstCrawledAt
        );
        candidateRepository.flush();
        ProfessorCrawlCandidate departedCandidate = candidateRepository
            .findAllBySourceId(source.getId())
            .getFirst();
        Long departedCandidateId = departedCandidate.getId();
        departedCandidate.approve("reviewer", "검수 완료", firstCrawledAt.plusMinutes(30));
        candidateRepository.flush();

        persistenceService.saveSuccessful(
            sourceRun,
            List.of(successor),
            firstCrawledAt.plusHours(1)
        );
        candidateRepository.flush();

        List<ProfessorCrawlCandidate> candidates = candidateRepository.findAllBySourceId(source.getId());
        assertThat(candidates).hasSize(2);
        assertThat(candidates)
            .filteredOn(candidate -> candidate.getId().equals(departedCandidateId))
            .singleElement()
            .satisfies(candidate -> {
                assertThat(candidate.getProfessorName()).isEqualTo("퇴임교수");
                assertThat(candidate.getReviewStatus()).isEqualTo(CandidateReviewStatus.APPROVED);
                assertThat(candidate.isStale()).isTrue();
            });
        assertThat(candidates)
            .filteredOn(candidate -> "successor@sejong.ac.kr".equals(candidate.getEmail()))
            .singleElement()
            .satisfies(candidate -> {
                assertThat(candidate.getReviewStatus()).isEqualTo(CandidateReviewStatus.PENDING);
                assertThat(candidate.isStale()).isFalse();
            });
    }

    private ProfessorCrawlData data(String professorName, String researchIntroduction) {
        return new ProfessorCrawlData(
            professorName,
            "교수",
            professorName + "@sejong.ac.kr",
            null,
            researchIntroduction,
            null
        );
    }

    private ProfessorCrawlData dataWithoutContact(
        String professorName,
        String researchIntroduction
    ) {
        return new ProfessorCrawlData(
            professorName,
            "교수",
            null,
            null,
            researchIntroduction,
            null
        );
    }

    private ProfessorCrawlData identifiedData(
        String professorName,
        String email,
        String homepageUrl
    ) {
        return new ProfessorCrawlData(
            professorName,
            "교수",
            email,
            "연구실",
            "연구 분야",
            homepageUrl
        );
    }
}

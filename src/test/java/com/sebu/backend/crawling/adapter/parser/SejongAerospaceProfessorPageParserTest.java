package com.sebu.backend.crawling.adapter.parser;

import com.sebu.backend.crawling.domain.CrawlParserType;
import com.sebu.backend.crawling.domain.ProfessorCrawlData;
import com.sebu.backend.crawling.dto.FetchedProfessorPage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SejongAerospaceProfessorPageParserTest {
    private final SejongAerospaceProfessorPageParser parser = new SejongAerospaceProfessorPageParser();

    @Test
    void supportsASeparateParserType() {
        assertThat(parser.supports()).isEqualTo(CrawlParserType.SEJONG_AEROSPACE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"82031", "82041", "82051"})
    void parsesAllThreeMajorsWithoutMixingOfficesDegreesOrProfileLinksIntoResearchData(String category)
        throws IOException {
        assertThat(parser.parse(page(fixture(), category))).containsExactly(
            new ProfessorCrawlData(
                "홍항공", "전임교원", "aero@example.com", null,
                "유도제어, 군집/편대 비행, AI 기반 최적화: 경로 계획", null
            ),
            new ProfessorCrawlData(
                "Alex Drone", "연구중점전임교원", "drone@example.com", null,
                "UAV/UAM 제어, 3D 영상, HILS", null
            ),
            new ProfessorCrawlData("김시스템", "비전임교원", null, null, null, null)
        );
    }

    @Test
    void preservesTheSameProfessorsIdentityAcrossMajorSources() throws IOException {
        ProfessorCrawlData aerospace = parser.parse(page(fixture(), "82031")).getFirst();
        ProfessorCrawlData drone = parser.parse(page(fixture(), "82041")).getFirst();
        assertThat(aerospace.identityKey()).isEqualTo(drone.identityKey()).isEqualTo("email:aero@example.com");
    }

    @Test
    void rejectsAnEmptyOrChangedPage() {
        assertParseFailure("<html><body>로그인</body></html>", "PROFESSOR_CARD_NOT_FOUND");
    }

    @Test
    void rejectsTheWholeResultWhenOneProfessorHasNoName() throws IOException {
        assertParseFailure(fixture().replace("홍항공", " "), "PROFESSOR_NAME_MISSING: 0");
    }

    @Test
    void rejectsTheWholeResultWhenANameElementIsMissing() throws IOException {
        assertParseFailure(fixture().replace("class=\"name\"", "class=\"renamed\""), "PROFESSOR_NAME_MISSING: 0");
    }

    @Test
    void rejectsAMissingSummaryInsteadOfAssumingTheListIsComplete() throws IOException {
        assertParseFailure(fixture().replace("총 3건, 1/1 페이지", ""), "PAGE_SUMMARY_MISSING");
    }

    @Test
    void rejectsATruncatedListBeforeCandidatesCanBeMarkedStale() throws IOException {
        assertParseFailure(fixture().replace("총 3건", "총 4건"), "PROFESSOR_COUNT_MISMATCH: expected=4, actual=3");
    }

    @ParameterizedTest
    @ValueSource(strings = {"총 6건, 1/2 페이지", "총 6건, 2/2 페이지"})
    void refusesPartialPaginationUntilMultiPageFetchingIsImplemented(String summary) throws IOException {
        assertParseFailure(fixture().replace("총 3건, 1/1 페이지", summary), "MULTIPLE_PAGES_NOT_SUPPORTED");
    }

    private void assertParseFailure(String html, String suffix) {
        assertThatThrownBy(() -> parser.parse(page(html, "82031")))
            .isInstanceOf(ProfessorPageParseException.class)
            .hasMessage("SEJONG_AEROSPACE_" + suffix);
    }

    private FetchedProfessorPage page(String html, String category) {
        return new FetchedProfessorPage(html,
            "https://ae.sejong.ac.kr/shop_contents/myboard_list.htm?myboard_code=professor&category_idx=" + category);
    }

    private String fixture() throws IOException {
        return new ClassPathResource("fixtures/crawling/sejong-aerospace-professors.html")
            .getContentAsString(StandardCharsets.UTF_8);
    }
}

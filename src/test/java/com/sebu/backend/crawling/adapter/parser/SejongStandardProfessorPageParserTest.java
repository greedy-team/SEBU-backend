package com.sebu.backend.crawling.adapter.parser;

import com.sebu.backend.crawling.dto.FetchedProfessorPage;
import com.sebu.backend.crawling.domain.ProfessorCrawlData;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SejongStandardProfessorPageParserTest {
    private static final String SOURCE_URL =
        "https://dept.sejong.ac.kr/cedpt/intro/professor.do";

    private final SejongStandardProfessorPageParser parser =
        new SejongStandardProfessorPageParser(new ProfessorHomepageUrlNormalizer());

    @Test
    void parsesProfessorFieldsWithoutMixingLabelsOrPortalLinks() {
        FetchedProfessorPage page = new FetchedProfessorPage(
            fixture("sejong-standard-professors.html"),
            SOURCE_URL
        );

        List<ProfessorCrawlData> professors = parser.parse(page);

        assertThat(professors).containsExactly(
            new ProfessorCrawlData(
                "홍길동",
                "부교수",
                "hong@sejong.edu",
                null,
                "인공지능 머신러닝",
                "https://example.com/lab"
            ),
            new ProfessorCrawlData(
                "김철수",
                null,
                "second@sejong.ac.kr",
                null,
                null,
                null
            )
        );
    }

    @Test
    void rejectsAChangedPageThatHasNoProfessorCards() {
        FetchedProfessorPage page = new FetchedProfessorPage(
            "<html><body></body></html>",
            SOURCE_URL
        );

        assertThatThrownBy(() -> parser.parse(page))
            .isInstanceOf(ProfessorPageParseException.class)
            .hasMessage("SEJONG_STANDARD_PROFESSOR_CARD_NOT_FOUND");
    }

    private String fixture(String name) {
        String path = "/fixtures/crawling/" + name;
        try (InputStream input = getClass().getResourceAsStream(path)) {
            if (input == null) {
                throw new IllegalStateException("TEST_FIXTURE_NOT_FOUND: " + path);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("TEST_FIXTURE_READ_FAILED: " + path, exception);
        }
    }
}

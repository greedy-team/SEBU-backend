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

class SejongQuantumProfessorPageParserTest {
    private static final String SOURCE_URL =
        "https://dept.sejong.ac.kr/qisedpt/intro/professor-introduction.do";

    private final SejongQuantumProfessorPageParser parser =
        new SejongQuantumProfessorPageParser(new ProfessorHomepageUrlNormalizer());

    @Test
    void parsesLabelBasedQuantumProfessorCards() {
        FetchedProfessorPage page = new FetchedProfessorPage(
            fixture("sejong-quantum-professors.html"),
            SOURCE_URL
        );

        List<ProfessorCrawlData> professors = parser.parse(page);

        assertThat(professors).containsExactly(
            new ProfessorCrawlData(
                "이양자",
                "조교수",
                "quantum@sejong.ac.kr",
                null,
                "양자 컴퓨팅 양자 통신",
                "https://dept.sejong.ac.kr/quantum/lab"
            )
        );
    }

    @Test
    void rejectsAChangedPageThatHasNoQuantumProfessorCards() {
        FetchedProfessorPage page = new FetchedProfessorPage(
            "<html><body></body></html>",
            SOURCE_URL
        );

        assertThatThrownBy(() -> parser.parse(page))
            .isInstanceOf(ProfessorPageParseException.class)
            .hasMessage("SEJONG_QUANTUM_PROFESSOR_CARD_NOT_FOUND");
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

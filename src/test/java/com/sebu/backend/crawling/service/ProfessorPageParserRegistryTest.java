package com.sebu.backend.crawling.service;

import com.sebu.backend.crawling.port.ProfessorPageParser;
import com.sebu.backend.crawling.domain.CrawlParserType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProfessorPageParserRegistryTest {
    @Test
    void selectsAllThreeSiteParsersIndependently() {
        ProfessorPageParser standard = parser(CrawlParserType.SEJONG_STANDARD);
        ProfessorPageParser quantum = parser(CrawlParserType.SEJONG_QUANTUM);
        ProfessorPageParser aerospace = parser(CrawlParserType.SEJONG_AEROSPACE);
        ProfessorPageParserRegistry registry = new ProfessorPageParserRegistry(List.of(standard, quantum, aerospace));

        assertThat(registry.get(CrawlParserType.SEJONG_STANDARD)).isSameAs(standard);
        assertThat(registry.get(CrawlParserType.SEJONG_QUANTUM)).isSameAs(quantum);
        assertThat(registry.get(CrawlParserType.SEJONG_AEROSPACE)).isSameAs(aerospace);
    }

    @Test
    void rejectsDuplicateParsersForTheSameParserType() {
        ProfessorPageParser first = parser(CrawlParserType.SEJONG_STANDARD);
        ProfessorPageParser duplicate = parser(CrawlParserType.SEJONG_STANDARD);

        assertThatThrownBy(() -> new ProfessorPageParserRegistry(List.of(first, duplicate)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("DUPLICATE_PROFESSOR_PAGE_PARSER: SEJONG_STANDARD");
    }

    private ProfessorPageParser parser(CrawlParserType parserType) {
        ProfessorPageParser parser = mock(ProfessorPageParser.class);
        when(parser.supports()).thenReturn(parserType);
        return parser;
    }
}

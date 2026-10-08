package com.sebu.backend.crawling.adapter.parser;

import com.sebu.backend.crawling.dto.FetchedProfessorPage;
import com.sebu.backend.crawling.port.ProfessorPageParser;
import com.sebu.backend.crawling.domain.CrawlParserType;
import com.sebu.backend.crawling.domain.ProfessorCrawlData;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class SejongQuantumProfessorPageParser implements ProfessorPageParser {
    private static final String PROFESSOR_CARDS = ".professor-container > .professor-item";

    private final ProfessorHomepageUrlNormalizer homepageUrlNormalizer;

    @Override
    public CrawlParserType supports() {
        return CrawlParserType.SEJONG_QUANTUM;
    }

    @Override
    public List<ProfessorCrawlData> parse(FetchedProfessorPage page) {
        Document document = Jsoup.parse(page.html(), page.location());
        List<Element> cards = document.select(PROFESSOR_CARDS);
        if (cards.isEmpty()) {
            throw new ProfessorPageParseException("SEJONG_QUANTUM_PROFESSOR_CARD_NOT_FOUND");
        }

        List<ProfessorCrawlData> professors = new ArrayList<>(cards.size());
        for (int index = 0; index < cards.size(); index++) {
            professors.add(parseCard(cards.get(index), document.location(), index));
        }
        return List.copyOf(professors);
    }

    private ProfessorCrawlData parseCard(Element card, String baseUri, int index) {
        Element nameElement = card.selectFirst(".professor-name");
        String professorName = ProfessorPageParsingSupport.nullableText(
            nameElement == null ? null : nameElement.text()
        );
        if (professorName == null) {
            throw new ProfessorPageParseException("SEJONG_QUANTUM_PROFESSOR_NAME_MISSING: " + index);
        }
        professorName = professorName.replaceFirst("\\s*교수\\s*$", "").trim();

        String position = null;
        String email = null;
        String researchIntroduction = null;
        String homepageUrl = null;
        for (Element row : card.select(".info-row")) {
            Element labelElement = row.selectFirst(".info-label");
            Element valueElement = row.selectFirst(".info-value");
            String label = normalizeLabel(labelElement);
            if (label == null || valueElement == null) {
                continue;
            }

            switch (label) {
                case "직위" -> position = ProfessorPageParsingSupport.nullableText(valueElement.text());
                case "연구분야" -> researchIntroduction = ProfessorPageParsingSupport.nullableText(
                    valueElement.text()
                );
                case "e-mail", "email" -> email = ProfessorPageParsingSupport.emailFrom(valueElement);
                case "홈페이지" -> {
                    Element link = valueElement.selectFirst("a[href]");
                    homepageUrl = homepageUrlNormalizer.normalize(
                        link == null ? null : link.attr("href"),
                        baseUri
                    );
                }
                default -> {
                }
            }
        }

        return new ProfessorCrawlData(
            professorName,
            position,
            email,
            null,
            researchIntroduction,
            homepageUrl
        );
    }

    private String normalizeLabel(Element labelElement) {
        String label = ProfessorPageParsingSupport.nullableText(
            labelElement == null ? null : labelElement.text()
        );
        if (label == null) {
            return null;
        }
        return label
            .replaceFirst("^[•·]\\s*", "")
            .replaceAll("\\s+", "")
            .toLowerCase(Locale.ROOT);
    }

}

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

@Component
@RequiredArgsConstructor
public class SejongStandardProfessorPageParser implements ProfessorPageParser {
    private static final String PROFESSOR_CARDS = "#proShow > li";

    private final ProfessorHomepageUrlNormalizer homepageUrlNormalizer;

    @Override
    public CrawlParserType supports() {
        return CrawlParserType.SEJONG_STANDARD;
    }

    @Override
    public List<ProfessorCrawlData> parse(FetchedProfessorPage page) {
        Document document = Jsoup.parse(page.html(), page.location());
        List<Element> cards = document.select(PROFESSOR_CARDS);
        if (cards.isEmpty()) {
            throw new ProfessorPageParseException("SEJONG_STANDARD_PROFESSOR_CARD_NOT_FOUND");
        }

        List<ProfessorCrawlData> professors = new ArrayList<>(cards.size());
        for (int index = 0; index < cards.size(); index++) {
            professors.add(parseCard(cards.get(index), document.location(), index));
        }
        return List.copyOf(professors);
    }

    private ProfessorCrawlData parseCard(Element card, String baseUri, int index) {
        Element nameElement = card.selectFirst(".b-professor-name > p");
        String professorName = ProfessorPageParsingSupport.nullableText(
            nameElement == null ? null : nameElement.ownText()
        );
        if (professorName == null) {
            throw new ProfessorPageParseException("SEJONG_STANDARD_PROFESSOR_NAME_MISSING: " + index);
        }

        Element researchElement = card.selectFirst(".b-professor-field > p");
        Element homepageElement = card.selectFirst(".b-professor-link a.homepage[href]");
        return new ProfessorCrawlData(
            professorName,
            findPosition(card),
            findEmail(card),
            null,
            ProfessorPageParsingSupport.nullableText(
                researchElement == null ? null : researchElement.text()
            ),
            homepageUrlNormalizer.normalize(
                homepageElement == null ? null : homepageElement.attr("href"),
                baseUri
            )
        );
    }

    private String findPosition(Element card) {
        String position = null;
        for (Element valueElement : card.select(".b-professor-name > ul > li > span")) {
            String value = ProfessorPageParsingSupport.nullableText(valueElement.text());
            if (value != null && value.contains("교수")) {
                position = value;
            }
        }
        return position;
    }

    private String findEmail(Element card) {
        return ProfessorPageParsingSupport.emailFrom(
            card.selectFirst(".b-professor-info .b-email")
        );
    }
}

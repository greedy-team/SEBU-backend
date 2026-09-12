package com.sebu.backend.crawling.adapter.parser;

import com.sebu.backend.crawling.domain.CrawlParserType;
import com.sebu.backend.crawling.domain.ProfessorCrawlData;
import com.sebu.backend.crawling.dto.FetchedProfessorPage;
import com.sebu.backend.crawling.port.ProfessorPageParser;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class SejongAerospaceProfessorPageParser implements ProfessorPageParser {
    private static final String PROFESSOR_CARDS = ".sub04_wrap > ul > li";
    private static final Pattern PAGE_SUMMARY = Pattern.compile(
        "총\\s*([\\d,]+)건\\s*,\\s*(\\d+)\\s*/\\s*(\\d+)\\s*페이지"
    );

    @Override
    public CrawlParserType supports() {
        return CrawlParserType.SEJONG_AEROSPACE;
    }

    @Override
    public List<ProfessorCrawlData> parse(FetchedProfessorPage page) {
        Document document = Jsoup.parse(page.html(), page.location());
        List<Element> cards = document.select(PROFESSOR_CARDS);
        if (cards.isEmpty()) {
            throw new ProfessorPageParseException("SEJONG_AEROSPACE_PROFESSOR_CARD_NOT_FOUND");
        }
        validateCompleteList(document, cards.size());

        List<ProfessorCrawlData> professors = new ArrayList<>(cards.size());
        for (int index = 0; index < cards.size(); index++) {
            professors.add(parseCard(cards.get(index), index));
        }
        return List.copyOf(professors);
    }

    private ProfessorCrawlData parseCard(Element card, int index) {
        Element nameElement = card.selectFirst("a > p > .name");
        String name = null;
        if (nameElement != null) {
            Element nameCopy = nameElement.clone();
            nameCopy.select("i").remove();
            name = ProfessorPageParsingSupport.nullableText(nameCopy.text());
        }
        if (name == null) {
            throw new ProfessorPageParseException("SEJONG_AEROSPACE_PROFESSOR_NAME_MISSING: " + index);
        }

        String position = null;
        String email = null;
        String researchIntroduction = null;
        for (Element row : card.select("a > p > span:not(.name)")) {
            String text = row.text().replace('\u00A0', ' ');
            int separator = text.indexOf(':');
            if (separator < 0) {
                continue;
            }
            String label = text.substring(0, separator).replaceAll("\\s+", "");
            String value = ProfessorPageParsingSupport.nullableText(text.substring(separator + 1));
            switch (label) {
                case "직위" -> position = value;
                case "이메일" -> email = value;
                case "전공" -> researchIntroduction = value;
                default -> {
                    // Offices, phone numbers and degree history are not research fields.
                }
            }
        }

        // The card links to a faculty profile, not a laboratory homepage.
        return new ProfessorCrawlData(name, position, email, null, researchIntroduction, null);
    }

    private void validateCompleteList(Document document, int cardCount) {
        Matcher summary = PAGE_SUMMARY.matcher(document.text());
        if (!summary.find()) {
            throw new ProfessorPageParseException("SEJONG_AEROSPACE_PAGE_SUMMARY_MISSING");
        }
        long total = Long.parseLong(summary.group(1).replace(",", ""));
        long currentPage = Long.parseLong(summary.group(2));
        long pageCount = Long.parseLong(summary.group(3));
        if (currentPage != 1 || pageCount != 1) {
            throw new ProfessorPageParseException("SEJONG_AEROSPACE_MULTIPLE_PAGES_NOT_SUPPORTED");
        }
        if (total != cardCount) {
            throw new ProfessorPageParseException(
                "SEJONG_AEROSPACE_PROFESSOR_COUNT_MISMATCH: expected=" + total + ", actual=" + cardCount
            );
        }
    }
}

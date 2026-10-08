package com.sebu.backend.crawling.adapter.parser;

import org.jsoup.nodes.Element;

import java.util.Locale;

final class ProfessorPageParsingSupport {
    private ProfessorPageParsingSupport() {
    }

    static String nullableText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value
            .replace('\u00A0', ' ')
            .replaceAll("\\s+", " ")
            .trim();
        if (normalized.isEmpty() || normalized.equals("-")) {
            return null;
        }
        return normalized;
    }

    static String emailFrom(Element container) {
        if (container == null) {
            return null;
        }
        for (Element link : container.select("a[href]")) {
            String href = link.attr("href").trim();
            if (!href.toLowerCase(Locale.ROOT).startsWith("mailto:")) {
                continue;
            }
            String address = href.substring("mailto:".length());
            int queryStart = address.indexOf('?');
            if (queryStart >= 0) {
                address = address.substring(0, queryStart);
            }
            return nullableText(address);
        }
        return nullableText(container.text());
    }
}

package com.sebu.backend.crawling.adapter.parser;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Pattern;

@Component
public class ProfessorHomepageUrlNormalizer {
    private static final Pattern DOMAIN_WITH_OPTIONAL_PATH = Pattern.compile(
        "^(?:www\\.)?[A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?\\.[A-Za-z]{2,}"
            + "(?::\\d{1,5})?(?:[/#?].*)?$"
    );

    public String normalize(String rawUrl, String baseUri) {
        String value = ProfessorPageParsingSupport.nullableText(rawUrl);
        if (value == null) {
            return null;
        }

        String lowerCase = value.toLowerCase(Locale.ROOT);
        if (lowerCase.equals("http")
            || lowerCase.equals("https")
            || lowerCase.startsWith("#")
            || lowerCase.startsWith("javascript:")
            || lowerCase.startsWith("mailto:")) {
            return null;
        }

        String candidate;
        if (value.startsWith("//")) {
            candidate = "https:" + value;
        } else if (lowerCase.startsWith("http://") || lowerCase.startsWith("https://")) {
            candidate = value;
        } else if (DOMAIN_WITH_OPTIONAL_PATH.matcher(value).matches()) {
            candidate = "https://" + value;
        } else {
            candidate = resolveRelative(value, baseUri);
        }
        return validHttpUrl(candidate);
    }

    private String resolveRelative(String value, String baseUri) {
        try {
            URI base = URI.create(baseUri);
            if (!isHttpScheme(base.getScheme()) || base.getRawAuthority() == null) {
                return null;
            }
            return base.resolve(value).toString();
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String validHttpUrl(String value) {
        if (value == null) {
            return null;
        }
        try {
            URI uri = URI.create(value);
            if (!isHttpScheme(uri.getScheme()) || uri.getRawAuthority() == null) {
                return null;
            }
            return uri.toString();
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private boolean isHttpScheme(String scheme) {
        return scheme != null
            && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"));
    }
}

package com.sebu.backend.researchfield.extraction.service;

import com.sebu.backend.researchfield.candidate.domain.ResearchFieldCandidateDraft;
import com.sebu.backend.researchfield.candidate.domain.ResearchFieldExtractionMethod;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class ResearchFieldTextExtractor {
    public static final String RULE_VERSION = "sejong-v2";

    private static final int MAX_AUTO_SUGGESTION_LENGTH = 80;
    private static final Pattern FIELD_PREFIX = Pattern.compile(
        "^(?:[*#]\\s*)?(?:주요\\s*)?연구\\s*분야\\s*[:：]?\\s*",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern LEADING_DECORATORS = Pattern.compile(
        "^[\\s*#•●▪■\\-–—:：]+"
    );
    private static final Pattern TRAILING_DECORATORS = Pattern.compile(
        "[\\s,，;；.。]+$"
    );
    private static final Pattern NARRATIVE_ENDING = Pattern.compile(
        "(?:습니다|입니다|합니다|있습니다|됩니다)(?:[.!?。]|\\s|$)"
    );

    private final ResearchFieldTextHasher textHasher;

    public ResearchFieldTextExtractor(ResearchFieldTextHasher textHasher) {
        this.textHasher = textHasher;
    }

    public List<ResearchFieldCandidateDraft> extract(String description) {
        String normalizedDescription = normalizeSource(description);
        if (normalizedDescription == null) {
            return List.of();
        }
        String content = stripFieldPrefix(normalizedDescription);
        String narrativeContent = normalizeDisplay(content);
        if (narrativeContent == null) {
            return List.of();
        }
        if (NARRATIVE_ENDING.matcher(narrativeContent).find()) {
            return List.of(new ResearchFieldCandidateDraft(
                textHasher.hashFieldIdentity(narrativeContent),
                narrativeContent,
                null,
                ResearchFieldExtractionMethod.LONG_TEXT,
                0
            ));
        }
        List<String> fragments = splitTopLevel(content);
        Map<String, String> uniqueFragments = new LinkedHashMap<>();

        for (String fragment : fragments) {
            String normalizedFragment = normalizeDisplay(fragment);
            if (normalizedFragment == null) {
                continue;
            }
            uniqueFragments.putIfAbsent(
                textHasher.hashFieldIdentity(normalizedFragment),
                normalizedFragment
            );
        }

        boolean delimited = uniqueFragments.size() > 1;
        List<ResearchFieldCandidateDraft> drafts = new ArrayList<>();
        for (Map.Entry<String, String> entry : uniqueFragments.entrySet()) {
            String rawFieldText = entry.getValue();
            boolean requiresManualName = rawFieldText.length() > MAX_AUTO_SUGGESTION_LENGTH
                || NARRATIVE_ENDING.matcher(rawFieldText).find();
            ResearchFieldExtractionMethod method = requiresManualName
                ? ResearchFieldExtractionMethod.LONG_TEXT
                : delimited
                    ? ResearchFieldExtractionMethod.DELIMITED
                    : ResearchFieldExtractionMethod.WHOLE_TEXT;
            drafts.add(new ResearchFieldCandidateDraft(
                entry.getKey(),
                rawFieldText,
                requiresManualName ? null : rawFieldText,
                method,
                drafts.size()
            ));
        }
        return List.copyOf(drafts);
    }

    private List<String> splitTopLevel(String content) {
        List<String> fragments = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int bracketDepth = 0;
        int expectedListNumber = 1;

        for (int index = 0; index < content.length(); index++) {
            char currentCharacter = content.charAt(index);
            if (isOpeningBracket(currentCharacter)) {
                bracketDepth++;
                current.append(currentCharacter);
                continue;
            }
            if (isClosingBracket(currentCharacter)) {
                bracketDepth = Math.max(0, bracketDepth - 1);
                current.append(currentCharacter);
                continue;
            }
            int markerEnd = bracketDepth == 0
                ? numberedListMarkerEnd(content, index, expectedListNumber)
                : -1;
            if (markerEnd >= 0) {
                appendFragment(fragments, current);
                expectedListNumber++;
                index = markerEnd - 1;
                continue;
            }
            if (bracketDepth == 0 && isSeparator(content, index, currentCharacter)) {
                appendFragment(fragments, current);
                continue;
            }
            current.append(currentCharacter);
        }
        appendFragment(fragments, current);
        return fragments;
    }

    private int numberedListMarkerEnd(
        String content,
        int index,
        int expectedListNumber
    ) {
        if (!Character.isDigit(content.charAt(index))
            || index > 0 && !Character.isWhitespace(content.charAt(index - 1))) {
            return -1;
        }
        int cursor = index;
        while (cursor < content.length() && Character.isDigit(content.charAt(cursor))) {
            cursor++;
        }
        if (cursor >= content.length() || content.charAt(cursor) != '.') {
            return -1;
        }
        String number = content.substring(index, cursor);
        if (!number.equals(Integer.toString(expectedListNumber))) {
            return -1;
        }
        cursor++;
        if (cursor >= content.length() || !Character.isWhitespace(content.charAt(cursor))) {
            return -1;
        }
        while (cursor < content.length() && Character.isWhitespace(content.charAt(cursor))) {
            cursor++;
        }
        return cursor;
    }

    private boolean isSeparator(
        String content,
        int index,
        char character
    ) {
        if (character == ',' || character == '，'
            || character == ';' || character == '；'
            || character == '\n' || character == '\r'
            || character == '•' || character == '●'
            || character == '▪' || character == '■') {
            return true;
        }
        if (character == '.'
            && index + 1 < content.length()
            && Character.isWhitespace(content.charAt(index + 1))) {
            return true;
        }
        boolean surroundedByWhitespace = index > 0
            && index + 1 < content.length()
            && Character.isWhitespace(content.charAt(index - 1))
            && Character.isWhitespace(content.charAt(index + 1));
        return surroundedByWhitespace
            && (character == '/' || character == '-' || character == '·');
    }

    private void appendFragment(List<String> fragments, StringBuilder current) {
        fragments.add(current.toString());
        current.setLength(0);
    }

    private boolean isOpeningBracket(char character) {
        return character == '(' || character == '[' || character == '{' || character == '（';
    }

    private boolean isClosingBracket(char character) {
        return character == ')' || character == ']' || character == '}' || character == '）';
    }

    private String stripFieldPrefix(String value) {
        return LEADING_DECORATORS.matcher(
            FIELD_PREFIX.matcher(value).replaceFirst("")
        ).replaceFirst("");
    }

    private String normalizeDisplay(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
            .trim()
            .replaceAll("\\s+", " ");
        normalized = LEADING_DECORATORS.matcher(normalized).replaceFirst("");
        normalized = TRAILING_DECORATORS.matcher(normalized).replaceFirst("");
        return normalized.isBlank() ? null : normalized;
    }

    private String normalizeSource(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
            .trim()
            .replaceAll("[\\t\\x0B\\f ]+", " ")
            .replaceAll(" *\\r?\\n *", "\n");
    }
}

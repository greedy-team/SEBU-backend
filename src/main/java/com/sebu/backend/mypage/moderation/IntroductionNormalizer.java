package com.sebu.backend.mypage.moderation;

import org.springframework.stereotype.Component;

import java.text.Normalizer;

@Component
public class IntroductionNormalizer {

    private static final String CHOSEONG =
            "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ";

    private static final String JUNGSEONG =
            "ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ";

    private static final String JONGSEONG =
            " ㄱㄲㄳㄴㄵㄶㄷㄹㄺㄻㄼㄽㄾㄿㅀㅁㅂㅄㅅㅆㅇㅈㅊㅋㅌㅍㅎ";

    public String normalize(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }

        String normalized = input
                .replaceAll("[\\u200B-\\u200D\\uFEFF]", "")
                .replaceAll("\\s+", "")
                .replaceAll("[^가-힣ㄱ-ㅎㅏ-ㅣa-zA-Z0-9]", "")
                .toLowerCase();

        normalized = composeHangul(normalized);

        normalized = Normalizer.normalize(
                normalized,
                Normalizer.Form.NFKC
        );

        normalized = normalized.replaceAll(
                "(.)\\1{2,}",
                "$1$1"
        );

        return normalized;
    }

    private String composeHangul(String input) {
        StringBuilder result = new StringBuilder();

        int i = 0;

        while (i < input.length()) {
            char current = input.charAt(i);

            int choseongIndex = CHOSEONG.indexOf(current);

            if (choseongIndex >= 0 && i + 1 < input.length()) {
                char next = input.charAt(i + 1);

                int jungseongIndex = JUNGSEONG.indexOf(next);

                if (jungseongIndex >= 0) {
                    int jongseongIndex = 0;

                    if (i + 2 < input.length()) {
                        char third = input.charAt(i + 2);
                        int candidate = JONGSEONG.indexOf(third);

                        if (candidate > 0) {
                            boolean nextIsVowel =
                                    i + 3 < input.length()
                                            && JUNGSEONG.indexOf(
                                            input.charAt(i + 3)
                                    ) >= 0;

                            if (!nextIsVowel) {
                                jongseongIndex = candidate;
                            }
                        }
                    }

                    char syllable = (char) (
                            0xAC00
                                    + (choseongIndex * 21 + jungseongIndex) * 28
                                    + jongseongIndex
                    );

                    result.append(syllable);

                    i += jongseongIndex > 0 ? 3 : 2;
                    continue;
                }
            }

            result.append(current);
            i++;
        }

        return result.toString();
    }
}

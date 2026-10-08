package com.sebu.backend.mypage.moderation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IntroductionNormalizerTest {

    private final IntroductionNormalizer normalizer =
            new IntroductionNormalizer();

    @Test
    void 공백과_기호를_제거한다() {
        String result = normalizer.normalize(
                "머 신.러-닝"
        );

        assertThat(result)
                .isEqualTo("머신러닝");
    }

    @Test
    void zeroWidth문자를_제거한다() {
        String result = normalizer.normalize(
                "머\u200B신\u200C러\u200D닝"
        );

        assertThat(result)
                .isEqualTo("머신러닝");
    }

    @Test
    void 영문은_소문자로_정규화한다() {
        String result = normalizer.normalize(
                "Machine Learning"
        );

        assertThat(result)
                .isEqualTo("machinelearning");
    }

    @Test
    void 빈_문자열은_빈_문자열을_반환한다() {
        assertThat(normalizer.normalize(""))
                .isEmpty();
    }

    @Test
    void 반복_문자를_두_글자로_축약한다() {
        assertThat(normalizer.normalize("aaaaa"))
                .isEqualTo("aa");
    }
}

package com.sebu.backend.mypage.moderation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IntroductionModeratorImplTest {

    private final IntroductionNormalizer normalizer =
            new IntroductionNormalizer();

    private final IntroductionPolicy policy =
            new IntroductionPolicy();

    private final IntroductionModeratorImpl moderator =
            new IntroductionModeratorImpl(normalizer, policy);

    @Test
    void 정상적인_자기소개는_허용한다() {
        ModerationResult result =
                moderator.moderate("머신러닝과 컴퓨터 비전에 관심이 있습니다.");

        assertThat(result.allowed()).isTrue();
    }

    @Test
    void 차단_표현이_포함된_자기소개는_거부한다() {
        ModerationResult result =
                moderator.moderate("저는 차단테스트표현에 관심이 있습니다.");

        assertThat(result.allowed()).isFalse();
    }

    @Test
    void 공백과_기호를_넣어_우회해도_거부한다() {
        ModerationResult result =
                moderator.moderate("저는 차 단.테-스 트 표현에 관심이 있습니다.");

        assertThat(result.allowed()).isFalse();
    }

    @Test
    void zeroWidth로_우회해도_거부한다() {
        ModerationResult result =
                moderator.moderate("차\u200B단\u200C테\u200D스트표현");

        assertThat(result.allowed()).isFalse();
    }

    @Test
    void 빈_자기소개는_허용한다() {
        ModerationResult result =
                moderator.moderate("");

        assertThat(result.allowed()).isTrue();
    }
    @Test
    void 한글_자모로_우회해도_거부한다() {
        ModerationResult result =
                moderator.moderate(
                        "ㅊ ㅏ ㄷ ㅏ ㄴ ㅌ ㅔ ㅅ ㅡ ㅌ ㅡ ㅍ ㅛ ㅎ ㅕ ㄴ"
                );

        assertThat(result.allowed()).isFalse();
    }
}

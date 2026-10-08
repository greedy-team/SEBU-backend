package com.sebu.backend.mypage.moderation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IntroductionPolicyTest {

    private final IntroductionPolicy introductionPolicy =
            new IntroductionPolicy();

    @Test
    void 정상적인_자기소개는_허용한다() {
        boolean allowed =
                introductionPolicy.isAllowed(
                        "머신러닝과컴퓨터비전에관심이있습니다"
                );

        assertThat(allowed).isTrue();
    }

    @Test
    void 차단_표현이_포함되면_거부한다() {
        boolean allowed =
                introductionPolicy.isAllowed(
                        "저는차단테스트표현에관심이있습니다"
                );

        assertThat(allowed).isFalse();
    }

    @Test
    void 빈_문자열은_허용한다() {
        boolean allowed =
                introductionPolicy.isAllowed("");

        assertThat(allowed).isTrue();
    }

    @Test
    void null은_허용한다() {
        boolean allowed =
                introductionPolicy.isAllowed(null);

        assertThat(allowed).isTrue();
    }

    @Test
    void 허용_문맥에_해당하면_차단_표현이_있어도_허용한다() {
        boolean allowed =
                introductionPolicy.isAllowed(
                        "차단테스트표현연구를진행하고있습니다"
                );

        assertThat(allowed).isTrue();
    }

    @Test
    void 허용_문맥이_없으면_차단_표현을_거부한다() {
        boolean allowed =
                introductionPolicy.isAllowed(
                        "저는차단테스트표현을사용합니다"
                );

        assertThat(allowed).isFalse();
    }
}

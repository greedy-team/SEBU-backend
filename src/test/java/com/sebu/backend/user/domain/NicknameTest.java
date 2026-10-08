package com.sebu.backend.user.domain;

import com.sebu.backend.user.exception.InvalidNicknameException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NicknameTest {

    @Test
    void nfkc와_공백_대소문자를_정규화한다() {
        Nickname nickname = Nickname.from("  ＳｅＢｕ  ");

        assertThat(nickname.value()).isEqualTo("SeBu");
        assertThat(nickname.normalizedValue()).isEqualTo("sebu");
    }

    @Test
    void 빈_닉네임은_null_쌍으로_정규화한다() {
        Nickname nickname = Nickname.from("   ");

        assertThat(nickname.value()).isNull();
        assertThat(nickname.normalizedValue()).isNull();
    }

    @Test
    void 예약어는_거부한다() {
        assertThatThrownBy(() -> Nickname.from(" 익명 "))
                .isInstanceOf(InvalidNicknameException.class)
                .extracting("reason")
                .isEqualTo("RESERVED_WORD");
    }

    @Test
    void 제어문자와_제로폭_문자는_거부한다() {
        assertThatThrownBy(() -> Nickname.from("세부\n사용자"))
                .isInstanceOf(InvalidNicknameException.class);
        assertThatThrownBy(() -> Nickname.from("세부\u200B사용자"))
                .isInstanceOf(InvalidNicknameException.class);
        assertThatThrownBy(() -> Nickname.from("\n"))
                .isInstanceOf(InvalidNicknameException.class);
        assertThatThrownBy(() -> Nickname.from("익명\uFE0F"))
                .isInstanceOf(InvalidNicknameException.class);
        assertThatThrownBy(() -> Nickname.from("익명\u034F"))
                .isInstanceOf(InvalidNicknameException.class);
        assertThatThrownBy(() -> Nickname.from("익명\u3164"))
                .isInstanceOf(InvalidNicknameException.class);
    }

    @Test
    void 정규화된_닉네임이_30자를_넘으면_거부한다() {
        assertThatThrownBy(() -> Nickname.from("가".repeat(31)))
                .isInstanceOf(InvalidNicknameException.class)
                .extracting("reason")
                .isEqualTo("TOO_LONG");
    }
}

package com.sebu.backend.auth.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sebu.backend.auth.port.SejongAuthenticationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SejongUserInfoParserTest {
    private final SejongUserInfoParser parser = new SejongUserInfoParser(new ObjectMapper());

    @Test
    void parsesOnlyRequiredProfileFieldsWithoutDependingOnUnspecifiedMarkers() {
        var profile = parser.parse(response("홍길동", "길동", "21012345", "컴퓨터공학과", "3513"));

        assertThat(profile.studentId()).isEqualTo("21012345");
        assertThat(profile.name()).isEqualTo("홍길동");
        assertThat(profile.departmentName()).isEqualTo("컴퓨터공학과");
        assertThat(profile.toString()).doesNotContain("secret@example.com", "010-0000-0000", "192.0.2.1");
    }

    @Test
    void fallsBackToIntegratedUserNameWhenKoreanNameIsBlank() {
        var profile = parser.parse(response(" ", "대체이름", "21012345", "컴퓨터공학과", "3513"));

        assertThat(profile.name()).isEqualTo("대체이름");
    }

    @Test
    void rejectsEveryMissingRequiredProfileField() {
        assertInvalid(response("홍길동", "길동", "", "컴퓨터공학과", "3513"));
        assertInvalid(response("", "", "21012345", "컴퓨터공학과", "3513"));
        assertInvalid(response("홍길동", "길동", "21012345", "", "3513"));
    }

    @Test
    void rejectsStudentIdThatIsNotExactlyEightDigits() {
        assertInvalid(response("홍길동", "길동", "2101234", "컴퓨터공학과", "3513"));
        assertInvalid(response("홍길동", "길동", "abcdefgh", "컴퓨터공학과", "3513"));
    }

    @Test
    void ignoresDepartmentCodeBecauseItIsNotAStableServiceIdentifier() {
        var profile = parser.parse(response("홍길동", "길동", "21012345", "컴퓨터공학과", ""));

        assertThat(profile.departmentName()).isEqualTo("컴퓨터공학과");
    }

    private void assertInvalid(String response) {
        assertThatThrownBy(() -> parser.parse(response))
            .isInstanceOfSatisfying(SejongAuthenticationException.class, exception -> {
                assertThat(exception.getReason())
                    .isEqualTo(SejongAuthenticationException.Reason.RESPONSE_INVALID);
                assertThat(exception.getCause()).isNull();
                assertThat(exception.getMessage()).doesNotContain(response);
            });
    }

    private String response(String koreanName, String fallbackName, String studentId, String department, String code) {
        return """
            {
              "dm_UserInfo": {
                "INTG_USR_NO":"%s", "INTG_KOR_NM":"%s", "INTG_USR_NM":"%s",
                "EMAIL":"secret@example.com", "TEL":"010-0000-0000"
              },
              "dm_UserInfoGam": {
                "DEPT_NM":"%s", "DEPT_NO":"%s", "IP":"192.0.2.1"
              }
            }
            """.formatted(studentId, koreanName, fallbackName, department, code);
    }
}

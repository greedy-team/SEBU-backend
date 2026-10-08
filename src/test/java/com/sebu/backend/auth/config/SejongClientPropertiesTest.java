package com.sebu.backend.auth.config;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class SejongClientPropertiesTest {
    private static final String PORTAL_RETURN_URL =
        "portal.sejong.ac.kr/comm/member/user/ssoLoginProc.do";

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsOfficialHttpsEndpoints() {
        SejongClientProperties properties = officialProperties(
            URI.create("https://portal.sejong.ac.kr/jsp/login/login_action.jsp"),
            URI.create("https://portal.sejong.ac.kr/jsp/login/loginSSL.jsp?rtUrl=portal.sejong.ac.kr"),
            PORTAL_RETURN_URL,
            URI.create("https://portal.sejong.ac.kr/comm/member/user/ssoLoginProc.do"),
            URI.create("https://sjpt.sejong.ac.kr/main/view/Login/doSsoLogin.do"),
            URI.create("https://sjpt.sejong.ac.kr/main/sys/UserInfo/initUserInfo.do"),
            Duration.ofSeconds(5),
            Duration.ofSeconds(10)
        );

        assertThat(validator.validate(properties)).isEmpty();
    }

    @Test
    void rejectsPlainHttpUnapprovedHostsAndTamperedReturnUrl() {
        SejongClientProperties properties = officialProperties(
            URI.create("http://portal.sejong.ac.kr/jsp/login/login_action.jsp"),
            URI.create("https://portal.sejong.ac.kr/jsp/login/loginSSL.jsp"),
            "attacker.example/redirect",
            URI.create("https://portal.sejong.ac.kr/comm/member/user/ssoLoginProc.do"),
            URI.create("https://attacker.example/main/view/Login/doSsoLogin.do"),
            URI.create("https://sjpt.sejong.ac.kr/main/sys/UserInfo/initUserInfo.do"),
            Duration.ofSeconds(5),
            Duration.ofSeconds(10)
        );

        assertThat(validator.validate(properties))
            .extracting(violation -> violation.getMessage())
            .contains("sejong client endpoints must use the official HTTPS hosts");
    }

    @Test
    void rejectsZeroOrNegativeTimeouts() {
        SejongClientProperties properties = officialProperties(
            URI.create("https://portal.sejong.ac.kr/jsp/login/login_action.jsp"),
            URI.create("https://portal.sejong.ac.kr/jsp/login/loginSSL.jsp"),
            PORTAL_RETURN_URL,
            URI.create("https://portal.sejong.ac.kr/comm/member/user/ssoLoginProc.do"),
            URI.create("https://sjpt.sejong.ac.kr/main/view/Login/doSsoLogin.do"),
            URI.create("https://sjpt.sejong.ac.kr/main/sys/UserInfo/initUserInfo.do"),
            Duration.ZERO,
            Duration.ofSeconds(-1)
        );

        assertThat(validator.validate(properties))
            .extracting(violation -> violation.getMessage())
            .contains("sejong client timeouts must be positive");
    }

    private SejongClientProperties officialProperties(
        URI portalLoginUrl,
        URI portalLoginPageUrl,
        String portalReturnUrl,
        URI portalSsoLoginUrl,
        URI ssoLoginUrl,
        URI userInfoUrl,
        Duration connectTimeout,
        Duration requestTimeout
    ) {
        return new SejongClientProperties(
            portalLoginUrl,
            portalLoginPageUrl,
            portalReturnUrl,
            portalSsoLoginUrl,
            ssoLoginUrl,
            userInfoUrl,
            connectTimeout,
            requestTimeout
        );
    }
}

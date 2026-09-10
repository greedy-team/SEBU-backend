package com.sebu.backend.auth.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:cookie-local;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.flyway.locations=classpath:db/migration"
})
@ActiveProfiles("local")
class RefreshTokenCookieLocalProfileIntegrationTest {
    @Autowired
    AuthCookieFactory cookieFactory;

    @Test
    void createsNonSecureRefreshCookieForLocalHttpProfile() {
        assertThat(cookieFactory.refresh("local-refresh-token", 60).isSecure()).isFalse();
        assertThat(cookieFactory.access("local-access-token", 60).isSecure()).isFalse();
        assertThat(cookieFactory.deleteRefresh().isSecure()).isFalse();
        assertThat(cookieFactory.deleteAccess().isSecure()).isFalse();
    }
}

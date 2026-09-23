package com.sebu.backend.auth.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:cookie-local;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.flyway.locations=classpath:db/migration"
})
@AutoConfigureMockMvc
@ActiveProfiles("local")
class RefreshTokenCookieLocalProfileIntegrationTest {
    @Autowired
    AuthCookieFactory cookieFactory;

    @Autowired
    MockMvc mockMvc;

    @Test
    void createsNonSecureRefreshCookieForLocalHttpProfile() {
        assertThat(cookieFactory.refresh("local-refresh-token", 60).isSecure()).isFalse();
        assertThat(cookieFactory.access("local-access-token", 60).isSecure()).isFalse();
        assertThat(cookieFactory.deleteRefresh().isSecure()).isFalse();
        assertThat(cookieFactory.deleteAccess().isSecure()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost:5173", "http://localhost:8080"})
    void allowsLocalCorsPreflight(String origin) throws Exception {
        mockMvc.perform(options("/api/v1/laboratories")
                .header("Origin", origin)
                .header("Access-Control-Request-Method", "GET"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", origin))
            .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    void localProfileReplacesDeploymentCorsOrigins() throws Exception {
        mockMvc.perform(options("/api/v1/laboratories")
                .header("Origin", "https://sebu-frontend.vercel.app")
                .header("Access-Control-Request-Method", "GET"))
            .andExpect(status().isForbidden())
            .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}

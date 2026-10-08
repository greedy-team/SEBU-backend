package com.sebu.backend.global.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityPublicApiIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void allowsUnauthenticatedAccessToColleges() throws Exception {
        mockMvc.perform(get("/api/v1/colleges"))
                .andExpect(status().isOk());
    }
}

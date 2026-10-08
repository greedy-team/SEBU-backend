package com.sebu.backend.global.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.auth.transport.require-https=true")
@AutoConfigureMockMvc
class HttpsTransportIntegrationTest {
    @Autowired
    MockMvc mockMvc;

    @Test
    void redirectsInsecureRequestsAndAcceptsSecureRequests() throws Exception {
        mockMvc.perform(get("/api/v1/laboratories"))
            .andExpect(status().is3xxRedirection())
            .andExpect(header().string("Location", startsWith("https://")));

        mockMvc.perform(get("/api/v1/laboratories").with(request -> {
                request.setScheme("https");
                request.setSecure(true);
                request.setServerPort(443);
                return request;
            }))
            .andExpect(status().isOk());
    }
}

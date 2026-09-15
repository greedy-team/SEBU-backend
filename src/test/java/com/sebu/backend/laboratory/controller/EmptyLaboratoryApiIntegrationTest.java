package com.sebu.backend.laboratory.controller;

import com.sebu.backend.global.auth.CurrentUserProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EmptyLaboratoryApiIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @MockitoBean CurrentUserProvider currentUserProvider;

    @Test
    void returnsOkWithEmptyArray() throws Exception {
        // Explicit empty fixture; deployment catalogue rows are restored by rollback.
        jdbc.update("UPDATE laboratory SET deleted_at=CURRENT_TIMESTAMP WHERE deleted_at IS NULL");
        when(currentUserProvider.currentUserId()).thenReturn(Optional.empty());
        mockMvc.perform(get("/api/v1/laboratories"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.laboratories").isEmpty())
            .andExpect(jsonPath("$.error").doesNotExist());
    }
}

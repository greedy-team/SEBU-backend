package com.sebu.backend.laboratory;

import com.sebu.backend.global.auth.CurrentUserProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class LaboratoryReviewCountControllerIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    CurrentUserProvider currentUserProvider;

    @Test
    void rejectsNegativePage() throws Exception {
        when(currentUserProvider.currentUserId())
                .thenReturn(Optional.empty());

        mockMvc.perform(
                        get("/api/v1/laboratories")
                                .param("sort", "REVIEW_COUNT_DESC")
                                .param("page", "-1")
                                .param("size", "20")
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code")
                        .value("INVALID_LABORATORY_PAGE"));
    }

    @Test
    void rejectsZeroSize() throws Exception {
        when(currentUserProvider.currentUserId())
                .thenReturn(Optional.empty());

        mockMvc.perform(
                        get("/api/v1/laboratories")
                                .param("sort", "REVIEW_COUNT_DESC")
                                .param("page", "0")
                                .param("size", "0")
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code")
                        .value("INVALID_LABORATORY_SIZE"));
    }

    @Test
    void rejectsSizeGreaterThanFifty() throws Exception {
        when(currentUserProvider.currentUserId())
                .thenReturn(Optional.empty());

        mockMvc.perform(
                        get("/api/v1/laboratories")
                                .param("sort", "REVIEW_COUNT_DESC")
                                .param("page", "0")
                                .param("size", "51")
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code")
                        .value("INVALID_LABORATORY_SIZE"));
    }

    @Test
    void acceptsMinimumSize() throws Exception {
        when(currentUserProvider.currentUserId())
                .thenReturn(Optional.empty());

        mockMvc.perform(
                        get("/api/v1/laboratories")
                                .param("sort", "REVIEW_COUNT_DESC")
                                .param("page", "0")
                                .param("size", "1")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(1));
    }

    @Test
    void acceptsMaximumSize() throws Exception {
        when(currentUserProvider.currentUserId())
                .thenReturn(Optional.empty());

        mockMvc.perform(
                        get("/api/v1/laboratories")
                                .param("sort", "REVIEW_COUNT_DESC")
                                .param("page", "0")
                                .param("size", "50")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(50));
    }
}

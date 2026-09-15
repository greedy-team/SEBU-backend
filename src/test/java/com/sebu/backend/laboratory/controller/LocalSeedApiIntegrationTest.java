package com.sebu.backend.laboratory.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:seed-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.flyway.locations=classpath:db/migration,classpath:db/local"
})
@AutoConfigureMockMvc
class LocalSeedApiIntegrationTest {
    @Autowired MockMvc mockMvc;

    @Test
    void localSeedProducesTheSpecifiedLaboratoryResponse() throws Exception {
        mockMvc.perform(get("/api/v1/laboratories"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.error").doesNotExist())
            .andExpect(jsonPath("$.data.laboratories.length()").value(3))
            .andExpect(jsonPath("$.data.laboratories[0].name").value("인공지능연구실"))
            .andExpect(jsonPath("$.data.laboratories[0].nameSource").value("OFFICIAL"))
            .andExpect(jsonPath("$.data.laboratories[0].affiliations.length()").value(1))
            .andExpect(jsonPath("$.data.laboratories[0].affiliations[0].department.name").value("인공지능데이터사이언스학과"))
            .andExpect(jsonPath("$.data.laboratories[0].researchFields.length()").value(2))
            .andExpect(jsonPath("$.data.laboratories[0].researchFieldCategories.length()").value(1))
            .andExpect(jsonPath("$.data.laboratories[0].researchFieldCategories[0].code").value("AI_ML"))
            .andExpect(jsonPath("$.data.laboratories[0].recruitmentStatus").value("RECRUITING"))
            .andExpect(jsonPath("$.data.laboratories[0].bookmarkCount").value(2))
            .andExpect(jsonPath("$.data.laboratories[0].bookmarked").value(false))
            .andExpect(jsonPath("$.data.laboratories[1].researchFieldCategories.length()").value(2))
            .andExpect(jsonPath("$.data.laboratories[1].researchFieldCategories[0].code").value("AI_ML"))
            .andExpect(jsonPath("$.data.laboratories[1].researchFieldCategories[1].code").value("SIGNAL_MEDIA"))
            .andExpect(jsonPath("$.data.laboratories[1].bookmarkCount").value(1))
            .andExpect(jsonPath("$.data.laboratories[2].name").value("데이터사이언스랩"))
            .andExpect(jsonPath("$.data.laboratories[2].nameSource").value("OFFICIAL"))
            .andExpect(jsonPath("$.data.laboratories[2].recruitmentStatus").value("UNKNOWN"))
            .andExpect(jsonPath("$.data.laboratories[2].websiteUrl").doesNotExist())
            .andExpect(jsonPath("$.data.laboratories[2].professor.email").doesNotExist())
            .andExpect(jsonPath("$.data.laboratories[2].researchFields").isEmpty());
    }
}

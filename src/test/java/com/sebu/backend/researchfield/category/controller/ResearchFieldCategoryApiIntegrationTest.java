package com.sebu.backend.researchfield.category.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ResearchFieldCategoryApiIntegrationTest {
    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void anonymousUserCanReadAllCategoriesInDisplayOrder() throws Exception {
        mockMvc.perform(get("/api/v1/research-field-categories"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.error").doesNotExist())
            .andExpect(jsonPath("$.data.categories.length()").value(54))
            .andExpect(jsonPath("$.data.categories[0].code").value("AI_ML"))
            .andExpect(jsonPath("$.data.categories[0].name")
                .value("인공지능·기계학습"))
            .andExpect(jsonPath("$.data.categories[0].displayOrder").value(1))
            .andExpect(jsonPath("$.data.categories[0].description").isNotEmpty())
            .andExpect(jsonPath("$.data.categories[17].code")
                .value("POLICY_MANAGEMENT"))
            .andExpect(jsonPath("$.data.categories[17].displayOrder").value(18))
            .andExpect(jsonPath("$.data.categories[18].code")
                .value("MATH_STATISTICS"))
            .andExpect(jsonPath("$.data.categories[18].name").value("수학·통계"))
            .andExpect(jsonPath("$.data.categories[18].displayOrder").value(19))
            .andExpect(jsonPath("$.data.categories[19].code")
                .value("PHYSICS_ASTRONOMY"))
            .andExpect(jsonPath("$.data.categories[19].name").value("물리·천문"))
            .andExpect(jsonPath("$.data.categories[19].displayOrder").value(20))
            .andExpect(jsonPath("$.data.categories[20].code")
                .value("CHEMISTRY_MATERIALS"))
            .andExpect(jsonPath("$.data.categories[20].name").value("화학·소재"))
            .andExpect(jsonPath("$.data.categories[20].displayOrder").value(21))
            .andExpect(jsonPath("$.data.categories[21].code").value("FOOD_NUTRITION"))
            .andExpect(jsonPath("$.data.categories[21].name").value("식품·영양"))
            .andExpect(jsonPath("$.data.categories[21].displayOrder").value(22))
            .andExpect(jsonPath("$.data.categories[22].code").value("PLANT_AGRICULTURE"))
            .andExpect(jsonPath("$.data.categories[22].name").value("식물·농업생명과학"))
            .andExpect(jsonPath("$.data.categories[22].displayOrder").value(23))
            .andExpect(jsonPath("$.data.categories[23].code").value("MOLECULAR_BIOTECH"))
            .andExpect(jsonPath("$.data.categories[23].name")
                .value("분자·세포생물학·생명공학"))
            .andExpect(jsonPath("$.data.categories[23].displayOrder").value(24))
            .andExpect(jsonPath("$.data.categories[24].code").value("ARCHITECTURE_CIVIL"))
            .andExpect(jsonPath("$.data.categories[24].name").value("건축·토목·도시"))
            .andExpect(jsonPath("$.data.categories[24].displayOrder").value(25))
            .andExpect(jsonPath("$.data.categories[25].code").value("MECHANICAL_AEROSPACE"))
            .andExpect(jsonPath("$.data.categories[26].code").value("EARTH_GEOSPATIAL"))
            .andExpect(jsonPath("$.data.categories[27].code").value("LANGUAGE_LITERATURE"))
            .andExpect(jsonPath("$.data.categories[28].code").value("HISTORY_CULTURE"))
            .andExpect(jsonPath("$.data.categories[29].code").value("MEDIA_COMMUNICATION"))
            .andExpect(jsonPath("$.data.categories[30].code").value("DESIGN_ARTS"))
            .andExpect(jsonPath("$.data.categories[31].code").value("PSYCHOLOGY_BEHAVIOR"))
            .andExpect(jsonPath("$.data.categories[31].name").value("심리·인지·행동"))
            .andExpect(jsonPath("$.data.categories[31].displayOrder").value(32))
            .andExpect(jsonPath("$.data.categories[7].parentId").isEmpty())
            .andExpect(jsonPath("$.data.categories[32].code")
                .value("ROBOT_AUTONOMOUS_ROBOTICS"))
            .andExpect(jsonPath("$.data.categories[32].name")
                .value("로봇공학·메카트로닉스"))
            .andExpect(jsonPath("$.data.categories[32].parentId").value(8))
            .andExpect(jsonPath("$.data.categories[51].displayOrder").value(52))
            .andExpect(jsonPath("$.data.categories[51].parentId").value(8))
            .andExpect(jsonPath("$.data.categories[52].code").value("MUSIC_PERFORMING_ARTS"))
            .andExpect(jsonPath("$.data.categories[52].name").value("음악·공연예술"))
            .andExpect(jsonPath("$.data.categories[52].displayOrder").value(53))
            .andExpect(jsonPath("$.data.categories[52].parentId").isEmpty())
            .andExpect(jsonPath("$.data.categories[53].code").value("SPORTS_PHYSICAL_EDUCATION"))
            .andExpect(jsonPath("$.data.categories[53].name").value("체육·스포츠"))
            .andExpect(jsonPath("$.data.categories[53].displayOrder").value(54))
            .andExpect(jsonPath("$.data.categories[53].parentId").isEmpty());
    }

    @Test
    void robotFieldsAreMappedOnlyToSpecificChildCategories() {
        Integer parentMappings = jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM research_field_category_mapping mapping
            JOIN research_field_category category ON category.id = mapping.category_id
            WHERE category.code = 'ROBOT_AUTONOMOUS'
            """, Integer.class);
        assertThat(parentMappings).isZero();

        String fieldRobotsCode = jdbcTemplate.queryForObject("""
            SELECT category.code FROM research_field_category_mapping mapping
            JOIN research_field field ON field.id = mapping.research_field_id
            JOIN research_field_category category ON category.id = mapping.category_id
            WHERE field.name = '건설작업로봇' AND category.parent_id IS NOT NULL
            """, String.class);
        assertThat(fieldRobotsCode).isEqualTo("ROBOT_AUTONOMOUS_FIELD_ROBOTS");
    }
}

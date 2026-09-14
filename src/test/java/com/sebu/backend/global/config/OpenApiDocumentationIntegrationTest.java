package com.sebu.backend.global.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OpenApiDocumentationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void exposesOpenApiDocumentWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("SEBU API"))
                .andExpect(jsonPath("$.info.version").value("v1"))
                .andExpect(jsonPath("$.components.securitySchemes.cookieAuth.type").value("apiKey"))
                .andExpect(jsonPath("$.components.securitySchemes.cookieAuth.in").value("cookie"))
                .andExpect(jsonPath("$.components.securitySchemes.cookieAuth.name").value("access_token"))
                .andExpect(jsonPath("$.components.securitySchemes.recoveryCookie.type").value("apiKey"))
                .andExpect(jsonPath("$.components.securitySchemes.recoveryCookie.in").value("cookie"))
                .andExpect(jsonPath("$.components.securitySchemes.recoveryCookie.name").value("recovery_token"))
                .andExpect(jsonPath("$.components.securitySchemes.csrfHeader.name").value("X-XSRF-TOKEN"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/sejong/login'].post.security[0].csrfHeader").isArray())
                .andExpect(jsonPath("$.paths['/api/v1/auth/recovery'].post.security[0].recoveryCookie").isArray())
                .andExpect(jsonPath("$.paths['/api/v1/auth/recovery'].post.security[0].csrfHeader").isArray())
                .andExpect(jsonPath("$.paths['/api/v1/auth/sejong/login'].post.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/recovery'].post.responses['200']").exists())
                .andExpect(jsonPath("$.components.schemas.LoginResponse.oneOf.length()").value(2))
                .andExpect(jsonPath("$.paths['/api/v1/me/profile'].patch.security[0].cookieAuth").isArray())
                .andExpect(jsonPath("$.paths['/api/v1/me/profile'].patch.security[0].csrfHeader").isArray())
                .andExpect(jsonPath("$.paths['/api/v1/laboratories'].get.summary")
                        .value("연구실 목록 조회"))
                .andExpect(jsonPath("$.paths['/api/v1/laboratories'].get.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/laboratories'].get.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/laboratories'].get.responses['401']")
                        .doesNotExist())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/laboratories'].get.responses['200'].content['application/json'].schema.oneOf"
                ).isArray())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/laboratories'].get.responses['200'].content['application/json'].schema.oneOf.length()"
                ).value(2))
                .andExpect(jsonPath("$.paths['/api/v1/me'].get.security[0].cookieAuth").isArray())
                .andExpect(jsonPath("$.paths['/api/v1/me'].get.responses['401']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/posts'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/posts'].post.responses['200']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/posts/{postId}'].get.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/posts/{postId}'].put.responses['403']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/posts/{postId}'].put.responses['404']").exists())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/laboratories/{laboratoryId}/bookmark'].put.responses['204']"
                ).exists())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/laboratories/{laboratoryId}/bookmark'].put.responses['200']"
                ).doesNotExist())
                .andExpect(jsonPath("$.components.schemas.ErrorApiResponse").exists())
                .andExpect(jsonPath("$.components.schemas.LaboratoriesListApiResponse").exists())
                .andExpect(jsonPath("$.components.schemas.LaboratoriesPagedApiResponse").exists());
    }

    @Test
    void documentsResearchFieldDetailsOnBothLaboratoryResponses() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.LaboratoriesResponse.properties.laboratories.items['$ref']")
                        .value("#/components/schemas/LaboratoryResponse"))
                .andExpect(jsonPath("$.components.schemas.LaboratoriesPagedResponse.properties.laboratories.items['$ref']")
                        .value("#/components/schemas/LaboratoryResponse"))
                .andExpect(jsonPath("$.components.schemas.LaboratoryResponse.properties.researchFields.items.type")
                        .value("string"))
                .andExpect(jsonPath("$.components.schemas.LaboratoryResponse.properties.researchFieldDetails.type")
                        .value("array"))
                .andExpect(jsonPath("$.components.schemas.LaboratoryResponse.properties.researchFieldDetails.items['$ref']")
                        .value("#/components/schemas/LaboratoryResearchFieldDetailResponse"))
                .andExpect(jsonPath("$.components.schemas.LaboratoryResearchFieldDetailResponse.properties.researchFieldId.type")
                        .value("integer"))
                .andExpect(jsonPath("$.components.schemas.LaboratoryResearchFieldDetailResponse.properties.researchFieldId.format")
                        .value("int64"))
                .andExpect(jsonPath("$.components.schemas.LaboratoryResearchFieldDetailResponse.properties.name.type")
                        .value("string"))
                .andExpect(jsonPath("$.components.schemas.LaboratoryResearchFieldDetailResponse.properties.categoryIds.type")
                        .value("array"))
                .andExpect(jsonPath("$.components.schemas.LaboratoryResearchFieldDetailResponse.properties.categoryIds.items.type")
                        .value("integer"));
    }

    @Test
    void exposesSwaggerUiWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }
}

package com.sebu.backend.global.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sebu.backend.user.domain.AcademicField;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AcademicFieldOpenApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @Test
    void documentsRequiredInputEnumAndBothResponseLocations() throws Exception {
        JsonNode api = api();
        JsonNode operation = api.path("paths").path("/api/v1/users/me/profile").path("put");
        JsonNode request = resolve(api, operation.path("requestBody").path("content").path("application/json").path("schema"));
        assertThat(textValues(request.path("required"))).contains("academicField");
        assertEnum(api, request.path("properties").path("academicField"), false);

        JsonNode saved = resolve(api, responseData(api, operation).path("properties").path("academicField"));
        assertEnum(api, saved, false);
        assertDepartmentIdIsString(api, responseData(api, operation));
        JsonNode mypage = api.path("paths").path("/api/v1/users/me/mypage").path("get");
        JsonNode profile = resolve(api, responseData(api, mypage).path("properties").path("profile"));
        JsonNode field = resolve(api, profile.path("properties").path("academicField"));
        assertEnum(api, field, true);
        assertDepartmentIdIsString(api, profile);
    }

    @Test
    void providesCopyableRequestSuccessAndFieldErrorExamples() throws Exception {
        JsonNode api = api();
        JsonNode put = api.path("paths").path("/api/v1/users/me/profile").path("put");
        JsonNode request = put.path("requestBody").path("content").path("application/json")
            .path("examples").path("engineering").path("value");
        assertThat(request.path("academicField").asText()).isEqualTo("ENGINEERING");
        JsonNode saved = example(put, "200", "saved");
        assertThat(saved.path("success").asBoolean()).isTrue();
        assertThat(saved.path("data").path("academicField").asText()).isEqualTo("ENGINEERING");
        JsonNode error = example(put, "400", "invalidAcademicField");
        assertThat(error.path("success").asBoolean()).isFalse();
        assertThat(error.path("error").path("fieldErrors").get(0).path("field").asText()).isEqualTo("academicField");
        JsonNode mypage = example(api.path("paths").path("/api/v1/users/me/mypage").path("get"), "200", "mypage");
        assertThat(mypage.path("data").path("profile").path("academicField").asText()).isEqualTo("ENGINEERING");
    }

    private JsonNode api() throws Exception {
        return mapper.readTree(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsByteArray());
    }

    private JsonNode responseData(JsonNode api, JsonNode operation) {
        JsonNode wrapper = resolve(api, operation.path("responses").path("200")
            .path("content").path("application/json").path("schema"));
        return resolve(api, wrapper.path("properties").path("data"));
    }

    private JsonNode example(JsonNode operation, String status, String name) {
        return operation.path("responses").path(status).path("content").path("application/json")
            .path("examples").path(name).path("value");
    }

    private JsonNode resolve(JsonNode api, JsonNode schema) {
        return schema.has("$ref") ? api.at(schema.path("$ref").asText().substring(1)) : schema;
    }

    private void assertEnum(JsonNode api, JsonNode field, boolean nullable) {
        JsonNode resolved = resolve(api, field);
        if (nullable) {
            assertThat(textValues(resolved.path("type"))).containsExactlyInAnyOrder("string", "null");
        } else {
            assertThat(resolved.path("type").asText()).isEqualTo("string");
        }
        assertThat(StreamSupport.stream(resolved.path("enum").spliterator(), false).anyMatch(JsonNode::isNull))
            .isEqualTo(nullable);
        assertThat(StreamSupport.stream(resolved.path("enum").spliterator(), false)
                .filter(value -> !value.isNull()).map(JsonNode::asText).toList())
            .containsExactlyElementsOf(Arrays.stream(AcademicField.values()).map(Enum::name).toList());
    }

    private void assertDepartmentIdIsString(JsonNode api, JsonNode profile) {
        JsonNode department = resolve(api, profile.path("properties").path("department"));
        assertThat(department.path("properties").path("id").path("type").asText()).isEqualTo("string");
    }

    private java.util.List<String> textValues(JsonNode node) {
        return StreamSupport.stream(node.spliterator(), false).map(JsonNode::asText).toList();
    }
}

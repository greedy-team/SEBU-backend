package com.sebu.backend.mypage.config;

import io.swagger.v3.oas.models.media.Schema;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class MyPageOpenApiConfiguration {
    @Bean
    OpenApiCustomizer nullableAcademicFieldSchema() {
        return api -> {
            Schema<?> profile = api.getComponents().getSchemas().get("MyPageProfile");
            if (profile == null || profile.getProperties() == null) return;
            Schema<?> field = profile.getProperties().get("academicField");
            if (field != null && field.getEnum() != null && !field.getEnum().contains(null)) {
                // OpenAPI 3.1 requires null in the enum as well as in the type union.
                field.addEnumItemObject(null);
            }
        };
    }
}

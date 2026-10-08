package com.sebu.backend.auth.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sebu.backend.auth.port.SejongAuthenticationException;
import com.sebu.backend.auth.port.SejongUserProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SejongUserInfoParser {
    private static final String STUDENT_ID_PATTERN = "\\d{8}";

    private final ObjectMapper objectMapper;

    public SejongUserProfile parse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String koreanName = optionalText(root, "dm_UserInfo", "INTG_KOR_NM");
            String fallbackName = optionalText(root, "dm_UserInfo", "INTG_USR_NM");
            String name = firstNonBlank(koreanName, fallbackName);
            requireMaxLength(name, 30);
            String studentId = requiredText(root, "dm_UserInfo", "INTG_USR_NO", 8);
            if (!studentId.matches(STUDENT_ID_PATTERN)) {
                throw SejongAuthenticationException.responseInvalid();
            }
            return new SejongUserProfile(
                studentId,
                name,
                requiredText(root, "dm_UserInfoGam", "DEPT_NM", 100)
            );
        } catch (SejongAuthenticationException exception) {
            throw exception;
        } catch (JsonProcessingException | RuntimeException exception) {
            // Jackson exceptions can embed a source excerpt. Never retain the raw school response as a cause.
            throw SejongAuthenticationException.responseInvalid();
        }
    }

    private String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary.trim();
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback.trim();
        }
        throw SejongAuthenticationException.responseInvalid();
    }

    private String requiredText(JsonNode root, String objectName, String fieldName) {
        String value = optionalText(root, objectName, fieldName);
        if (value == null || value.isBlank()) {
            throw SejongAuthenticationException.responseInvalid();
        }
        return value.trim();
    }

    private String requiredText(JsonNode root, String objectName, String fieldName, int maxLength) {
        String value = requiredText(root, objectName, fieldName);
        requireMaxLength(value, maxLength);
        return value;
    }

    private void requireMaxLength(String value, int maxLength) {
        if (value.length() > maxLength) {
            throw SejongAuthenticationException.responseInvalid();
        }
    }

    private String optionalText(JsonNode root, String objectName, String fieldName) {
        JsonNode object = root == null ? null : root.get(objectName);
        JsonNode value = object == null || !object.isObject() ? null : object.get(fieldName);
        if (value == null || value.isNull() || !value.isValueNode()) {
            return null;
        }
        return value.asText();
    }
}

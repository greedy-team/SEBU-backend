package com.sebu.backend.mypage.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.sebu.backend.user.domain.AcademicField;

import java.io.IOException;

/** Only this request field uses strict enum parsing; other API input policies remain unchanged. */
public final class AcademicFieldDeserializer extends StdDeserializer<AcademicField> {
    public AcademicFieldDeserializer() {
        super(AcademicField.class);
    }

    @Override
    public AcademicField deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (!parser.hasToken(JsonToken.VALUE_STRING)) {
            return context.reportInputMismatch(AcademicField.class,
                "계열은 지정된 문자열 코드 하나로 전달해 주세요.");
        }
        String value = parser.getText();
        if (value.isBlank()) {
            return null;
        }
        try {
            return AcademicField.valueOf(value);
        } catch (IllegalArgumentException exception) {
            return context.reportInputMismatch(AcademicField.class,
                "지원하지 않는 계열입니다. 목록에서 다시 선택해 주세요.");
        }
    }
}

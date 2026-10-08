package com.sebu.backend.user.domain;

import com.sebu.backend.user.exception.InvalidNicknameException;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

public final class Nickname {
    private static final int MAX_LENGTH = 30;
    private static final int MAX_NORMALIZED_LENGTH = 100;
    private static final Set<String> RESERVED_NORMALIZED_VALUES = Set.of("익명");
    private final String value;
    private final String normalizedValue;

    private Nickname(String value, String normalizedValue) {
        this.value = value;
        this.normalizedValue = normalizedValue;
    }

    public static Nickname from(String input) {
        if (input == null) {
            return new Nickname(null, null);
        }
        if (containsForbiddenCharacter(input)) {
            throw InvalidNicknameException.invalidFormat();
        }

        String value = Normalizer.normalize(input, Normalizer.Form.NFKC).strip();
        if (value.isEmpty()) {
            return new Nickname(null, null);
        }
        if (containsForbiddenCharacter(value)) {
            throw InvalidNicknameException.invalidFormat();
        }
        if (value.length() > MAX_LENGTH) {
            throw InvalidNicknameException.tooLong(MAX_LENGTH);
        }

        String normalizedValue = value.toLowerCase(Locale.ROOT);
        if (normalizedValue.length() > MAX_NORMALIZED_LENGTH) {
            throw InvalidNicknameException.tooLong(MAX_LENGTH);
        }
        if (RESERVED_NORMALIZED_VALUES.contains(normalizedValue)) {
            throw InvalidNicknameException.reservedWord();
        }
        return new Nickname(value, normalizedValue);
    }

    private static boolean containsForbiddenCharacter(String value) {
        return value.codePoints().anyMatch(codePoint ->
                Character.isISOControl(codePoint)
                        || Character.getType(codePoint) == Character.FORMAT
                        || isDefaultIgnorableCodePoint(codePoint)
        );
    }

    private static boolean isDefaultIgnorableCodePoint(int codePoint) {
        return codePoint == 0x00AD
                || codePoint == 0x034F
                || codePoint == 0x061C
                || (codePoint >= 0x115F && codePoint <= 0x1160)
                || (codePoint >= 0x17B4 && codePoint <= 0x17B5)
                || (codePoint >= 0x180B && codePoint <= 0x180F)
                || (codePoint >= 0x200B && codePoint <= 0x200F)
                || (codePoint >= 0x202A && codePoint <= 0x202E)
                || (codePoint >= 0x2060 && codePoint <= 0x206F)
                || codePoint == 0x3164
                || (codePoint >= 0xFE00 && codePoint <= 0xFE0F)
                || codePoint == 0xFEFF
                || codePoint == 0xFFA0
                || (codePoint >= 0xFFF0 && codePoint <= 0xFFF8)
                || (codePoint >= 0x1BCA0 && codePoint <= 0x1BCA3)
                || (codePoint >= 0x1D173 && codePoint <= 0x1D17A)
                || (codePoint >= 0xE0000 && codePoint <= 0xE0FFF);
    }

    public String value() {
        return value;
    }

    public String normalizedValue() {
        return normalizedValue;
    }
}

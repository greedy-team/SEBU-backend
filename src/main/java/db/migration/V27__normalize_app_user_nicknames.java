package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class V27__normalize_app_user_nicknames extends BaseJavaMigration {
    private static final int MAX_DISPLAY_LENGTH = 30;
    private static final int MAX_NORMALIZED_LENGTH = 100;
    private static final Set<String> RESERVED_VALUES = Set.of("익명");

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        alignNormalizedNicknameCollation(connection);
        List<LegacyNickname> nicknames = readLegacyNicknames(connection);
        List<NormalizedNickname> normalizedNicknames = normalizeAndValidate(nicknames);

        updateNicknames(connection, normalizedNicknames);
        addNicknameConstraints(connection);
    }

    private void alignNormalizedNicknameCollation(Connection connection) throws SQLException {
        String databaseProduct = connection.getMetaData().getDatabaseProductName();
        if (!databaseProduct.toLowerCase(Locale.ROOT).contains("mysql")) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    ALTER TABLE app_user
                        MODIFY COLUMN nickname_normalized VARCHAR(100)
                        CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL
                    """);
        }
    }

    private List<LegacyNickname> readLegacyNicknames(Connection connection) throws SQLException {
        List<LegacyNickname> result = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id, nickname FROM app_user WHERE nickname IS NOT NULL ORDER BY id"
        ); ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                result.add(new LegacyNickname(rows.getLong("id"), rows.getString("nickname")));
            }
        }
        return result;
    }

    private List<NormalizedNickname> normalizeAndValidate(List<LegacyNickname> nicknames)
            throws SQLException {
        List<NormalizedNickname> result = new ArrayList<>();
        Map<String, Long> ownerByNormalizedValue = new HashMap<>();

        for (LegacyNickname nickname : nicknames) {
            NormalizedNickname normalized = normalize(nickname);
            if (normalized.normalizedValue() != null) {
                Long existingOwner = ownerByNormalizedValue.putIfAbsent(
                        normalized.normalizedValue(),
                        normalized.userId()
                );
                if (existingOwner != null) {
                    throw new SQLException(
                            "NICKNAME_NORMALIZATION_CONFLICT: user " + existingOwner
                                    + " and user " + normalized.userId()
                    );
                }
            }
            result.add(normalized);
        }
        return result;
    }

    private NormalizedNickname normalize(LegacyNickname nickname) throws SQLException {
        if (containsForbiddenCharacter(nickname.value())) {
            throw invalidNickname(nickname.userId(), "FORBIDDEN_CHARACTER");
        }

        String displayValue = Normalizer.normalize(nickname.value(), Normalizer.Form.NFKC).strip();
        if (displayValue.isEmpty()) {
            return new NormalizedNickname(nickname.userId(), null, null);
        }
        if (containsForbiddenCharacter(displayValue)) {
            throw invalidNickname(nickname.userId(), "FORBIDDEN_CHARACTER");
        }
        if (displayValue.length() > MAX_DISPLAY_LENGTH) {
            throw invalidNickname(nickname.userId(), "DISPLAY_VALUE_TOO_LONG");
        }

        String normalizedValue = displayValue.toLowerCase(Locale.ROOT);
        if (normalizedValue.length() > MAX_NORMALIZED_LENGTH) {
            throw invalidNickname(nickname.userId(), "NORMALIZED_VALUE_TOO_LONG");
        }
        if (RESERVED_VALUES.contains(normalizedValue)) {
            throw invalidNickname(nickname.userId(), "RESERVED_VALUE");
        }
        return new NormalizedNickname(nickname.userId(), displayValue, normalizedValue);
    }

    private boolean containsForbiddenCharacter(String value) {
        return value.codePoints().anyMatch(codePoint ->
                Character.isISOControl(codePoint)
                        || Character.getType(codePoint) == Character.FORMAT
                        || isDefaultIgnorableCodePoint(codePoint)
        );
    }

    private boolean isDefaultIgnorableCodePoint(int codePoint) {
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

    private SQLException invalidNickname(Long userId, String reason) {
        return new SQLException("INVALID_LEGACY_NICKNAME: user " + userId + ", reason " + reason);
    }

    private void updateNicknames(
            Connection connection,
            List<NormalizedNickname> nicknames
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE app_user SET nickname = ?, nickname_normalized = ? WHERE id = ?"
        )) {
            for (NormalizedNickname nickname : nicknames) {
                statement.setString(1, nickname.displayValue());
                statement.setString(2, nickname.normalizedValue());
                statement.setLong(3, nickname.userId());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void addNicknameConstraints(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    ALTER TABLE app_user
                        ADD CONSTRAINT ck_app_user_nickname_pair CHECK (
                            (nickname IS NULL AND nickname_normalized IS NULL)
                            OR (nickname IS NOT NULL AND nickname_normalized IS NOT NULL)
                        )
                    """);
            statement.execute("""
                    ALTER TABLE app_user
                        ADD CONSTRAINT uk_app_user_nickname_normalized
                        UNIQUE (nickname_normalized)
                    """);
        }
    }

    private record LegacyNickname(Long userId, String value) {
    }

    private record NormalizedNickname(
            Long userId,
            String displayValue,
            String normalizedValue
    ) {
    }
}

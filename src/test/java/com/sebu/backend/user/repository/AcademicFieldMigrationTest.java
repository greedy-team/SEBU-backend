package com.sebu.backend.user.repository;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

class AcademicFieldMigrationTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void migratesFreshAndExistingDatabases(boolean upgrade) throws Exception {
        String url = "jdbc:h2:mem:academic-field-" + UUID.randomUUID()
            + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE";
        AcademicFieldMigrationAssertions.verify(url, "sa", "", upgrade);
    }
}

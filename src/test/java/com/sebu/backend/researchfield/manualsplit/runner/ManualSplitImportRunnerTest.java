package com.sebu.backend.researchfield.manualsplit.runner;

import com.sebu.backend.researchfield.manualsplit.config.ManualSplitImportProfileGuard;
import com.sebu.backend.researchfield.manualsplit.config.ManualSplitImportProperties;
import com.sebu.backend.researchfield.manualsplit.dto.ManualSplitCsvRow;
import com.sebu.backend.researchfield.manualsplit.dto.ManualSplitImportResult;
import com.sebu.backend.researchfield.manualsplit.reader.ManualSplitCsvReader;
import com.sebu.backend.researchfield.manualsplit.service.ManualSplitImportService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ManualSplitImportRunnerTest {
    @Test
    void rejectsConflictingOneTimeProfiles() {
        ManualSplitImportProfileGuard guard = new ManualSplitImportProfileGuard();

        assertThatThrownBy(() -> guard.run(new DefaultApplicationArguments()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("MANUAL_SPLIT_IMPORT_PROFILE_CONFLICT");
    }

    @Test
    void readsTheConfiguredCsvAndDelegatesTheImport() {
        ManualSplitCsvReader reader = mock(ManualSplitCsvReader.class);
        ManualSplitImportService service = mock(ManualSplitImportService.class);
        ManualSplitImportProperties properties = new ManualSplitImportProperties();
        Path csvPath = Path.of("manual-split.csv");
        properties.setCsvPath(csvPath);
        properties.setReviewer("reviewer");
        List<ManualSplitCsvRow> rows = List.of(
            new ManualSplitCsvRow(1, 2, 0, "인공지능", 2)
        );
        when(reader.read(csvPath)).thenReturn(rows);
        when(service.importRows(rows, "reviewer")).thenReturn(
            new ManualSplitImportResult(1, 1, 1, 0, 1)
        );
        ManualSplitImportRunner runner = new ManualSplitImportRunner(
            reader,
            service,
            properties
        );

        runner.run(new DefaultApplicationArguments());

        verify(reader).read(csvPath);
        verify(service).importRows(rows, "reviewer");
    }
}

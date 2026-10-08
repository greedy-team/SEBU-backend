package com.sebu.backend.researchfield.manualsplit.reader;

import com.sebu.backend.researchfield.manualsplit.dto.ManualSplitCsvRow;
import com.sebu.backend.researchfield.manualsplit.exception.ManualSplitImportException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ManualSplitCsvReaderTest {
    private final ManualSplitCsvReader reader = new ManualSplitCsvReader();

    @TempDir
    Path tempDirectory;

    @Test
    void readsUtf8BomAndQuotedCandidateNames() throws IOException {
        Path csv = writeCsv(
            "\uFEFForiginal_candidate_id,laboratory_id,source_order,candidate_name\n"
                + "57,12,1,자율주행 인공지능\n"
                + "57,12,2,\"영상, 라이다 환경 인식\"\n"
        );

        List<ManualSplitCsvRow> rows = reader.read(csv);

        assertThat(rows).containsExactly(
            new ManualSplitCsvRow(57, 12, 1, "자율주행 인공지능", 2),
            new ManualSplitCsvRow(57, 12, 2, "영상, 라이다 환경 인식", 3)
        );
    }

    @Test
    void rejectsAnUnexpectedHeader() throws IOException {
        Path csv = writeCsv(
            "candidate_id,laboratory_id,source_order,candidate_name\n"
                + "57,12,1,인공지능\n"
        );

        assertThatThrownBy(() -> reader.read(csv))
            .isInstanceOf(ManualSplitImportException.class)
            .hasMessage("MANUAL_SPLIT_CSV_HEADER_INVALID");
    }

    @Test
    void rejectsAnUnclosedQuotedField() throws IOException {
        Path csv = writeCsv(
            "original_candidate_id,laboratory_id,source_order,candidate_name\n"
                + "57,12,1,\"인공지능\n"
        );

        assertThatThrownBy(() -> reader.read(csv))
            .isInstanceOf(ManualSplitImportException.class)
            .hasMessageContaining("MANUAL_SPLIT_CSV_MULTILINE_NOT_SUPPORTED")
            .hasMessageContaining("line=2");
    }

    private Path writeCsv(String content) throws IOException {
        Path csv = tempDirectory.resolve("manual-split.csv");
        Files.writeString(csv, content, StandardCharsets.UTF_8);
        return csv;
    }
}

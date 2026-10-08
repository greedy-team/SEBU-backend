package com.sebu.backend.researchfield.manualsplit.reader;

import com.sebu.backend.researchfield.manualsplit.dto.ManualSplitCsvRow;
import com.sebu.backend.researchfield.manualsplit.exception.ManualSplitImportException;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Component
public class ManualSplitCsvReader {
    private static final List<String> EXPECTED_HEADER = List.of(
        "original_candidate_id",
        "laboratory_id",
        "source_order",
        "candidate_name"
    );

    public List<ManualSplitCsvRow> read(Path csvPath) {
        if (csvPath == null || !Files.isRegularFile(csvPath)) {
            throw new ManualSplitImportException("MANUAL_SPLIT_CSV_FILE_NOT_FOUND");
        }
        try (BufferedReader reader = Files.newBufferedReader(
            csvPath,
            StandardCharsets.UTF_8
        )) {
            validateHeader(reader.readLine());
            List<ManualSplitCsvRow> rows = readRows(reader);
            if (rows.isEmpty()) {
                throw new ManualSplitImportException("MANUAL_SPLIT_CSV_EMPTY");
            }
            return List.copyOf(rows);
        } catch (IOException exception) {
            throw new ManualSplitImportException(
                "MANUAL_SPLIT_CSV_READ_FAILED",
                exception
            );
        }
    }

    private void validateHeader(String headerLine) {
        if (headerLine == null) {
            throw new ManualSplitImportException("MANUAL_SPLIT_CSV_HEADER_REQUIRED");
        }
        String normalizedHeader = headerLine.startsWith("\uFEFF")
            ? headerLine.substring(1)
            : headerLine;
        List<String> header = parseLine(normalizedHeader, 1).stream()
            .map(String::trim)
            .toList();
        if (!EXPECTED_HEADER.equals(header)) {
            throw new ManualSplitImportException("MANUAL_SPLIT_CSV_HEADER_INVALID");
        }
    }

    private List<ManualSplitCsvRow> readRows(BufferedReader reader) throws IOException {
        List<ManualSplitCsvRow> rows = new ArrayList<>();
        String line;
        int lineNumber = 1;
        while ((line = reader.readLine()) != null) {
            lineNumber++;
            if (line.isBlank()) {
                continue;
            }
            List<String> values = parseLine(line, lineNumber);
            if (values.size() != EXPECTED_HEADER.size()) {
                throw lineError("MANUAL_SPLIT_CSV_COLUMN_COUNT_INVALID", lineNumber);
            }
            rows.add(toRow(values, lineNumber));
        }
        return rows;
    }

    private ManualSplitCsvRow toRow(List<String> values, int lineNumber) {
        try {
            return new ManualSplitCsvRow(
                Long.parseLong(values.get(0).trim()),
                Long.parseLong(values.get(1).trim()),
                Integer.parseInt(values.get(2).trim()),
                values.get(3),
                lineNumber
            );
        } catch (RuntimeException exception) {
            throw new ManualSplitImportException(
                "MANUAL_SPLIT_CSV_ROW_INVALID: line=" + lineNumber,
                exception
            );
        }
    }

    private List<String> parseLine(String line, int lineNumber) {
        List<String> values = new ArrayList<>();
        StringBuilder value = new StringBuilder();
        boolean inQuotes = false;
        boolean quoteClosed = false;

        for (int index = 0; index < line.length(); index++) {
            char current = line.charAt(index);
            if (inQuotes) {
                if (current != '"') {
                    value.append(current);
                    continue;
                }
                if (index + 1 < line.length() && line.charAt(index + 1) == '"') {
                    value.append('"');
                    index++;
                    continue;
                }
                inQuotes = false;
                quoteClosed = true;
                continue;
            }
            if (current == ',') {
                values.add(value.toString());
                value.setLength(0);
                quoteClosed = false;
                continue;
            }
            if (current == '"') {
                if (!value.toString().isBlank() || quoteClosed) {
                    throw lineError("MANUAL_SPLIT_CSV_QUOTE_INVALID", lineNumber);
                }
                value.setLength(0);
                inQuotes = true;
                continue;
            }
            if (quoteClosed && !Character.isWhitespace(current)) {
                throw lineError("MANUAL_SPLIT_CSV_QUOTE_INVALID", lineNumber);
            }
            if (!quoteClosed) {
                value.append(current);
            }
        }
        if (inQuotes) {
            throw lineError("MANUAL_SPLIT_CSV_MULTILINE_NOT_SUPPORTED", lineNumber);
        }
        values.add(value.toString());
        return values;
    }

    private ManualSplitImportException lineError(String code, int lineNumber) {
        return new ManualSplitImportException(code + ": line=" + lineNumber);
    }
}

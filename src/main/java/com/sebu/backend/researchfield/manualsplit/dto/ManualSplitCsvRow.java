package com.sebu.backend.researchfield.manualsplit.dto;

public record ManualSplitCsvRow(
    long originalCandidateId,
    long laboratoryId,
    int sourceOrder,
    String candidateName,
    int lineNumber
) {
    private static final int MAX_CANDIDATE_NAME_LENGTH = 100;

    public ManualSplitCsvRow {
        if (originalCandidateId <= 0) {
            throw new IllegalArgumentException("ORIGINAL_CANDIDATE_ID_INVALID");
        }
        if (laboratoryId <= 0) {
            throw new IllegalArgumentException("LABORATORY_ID_INVALID");
        }
        if (sourceOrder < 0) {
            throw new IllegalArgumentException("SOURCE_ORDER_INVALID");
        }
        if (candidateName == null || candidateName.isBlank()) {
            throw new IllegalArgumentException("CANDIDATE_NAME_REQUIRED");
        }
        candidateName = candidateName.trim();
        if (candidateName.length() > MAX_CANDIDATE_NAME_LENGTH) {
            throw new IllegalArgumentException("CANDIDATE_NAME_TOO_LONG");
        }
        if (lineNumber < 2) {
            throw new IllegalArgumentException("CSV_LINE_NUMBER_INVALID");
        }
    }
}

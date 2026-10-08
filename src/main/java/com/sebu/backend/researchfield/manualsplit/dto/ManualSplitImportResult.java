package com.sebu.backend.researchfield.manualsplit.dto;

public record ManualSplitImportResult(
    int sourceCount,
    int rowCount,
    int createdCount,
    int unchangedCount,
    int rejectedSourceCount
) { }

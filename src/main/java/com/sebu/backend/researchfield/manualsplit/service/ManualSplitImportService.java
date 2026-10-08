package com.sebu.backend.researchfield.manualsplit.service;

import com.sebu.backend.researchfield.candidate.domain.LaboratoryResearchFieldCandidate;
import com.sebu.backend.researchfield.candidate.domain.ResearchFieldCandidateDraft;
import com.sebu.backend.researchfield.candidate.domain.ResearchFieldCandidateReviewStatus;
import com.sebu.backend.researchfield.candidate.domain.ResearchFieldExtractionMethod;
import com.sebu.backend.researchfield.candidate.repository.LaboratoryResearchFieldCandidateRepository;
import com.sebu.backend.researchfield.extraction.service.ResearchFieldTextHasher;
import com.sebu.backend.researchfield.manualsplit.dto.ManualSplitCsvRow;
import com.sebu.backend.researchfield.manualsplit.dto.ManualSplitImportResult;
import com.sebu.backend.researchfield.manualsplit.exception.ManualSplitImportException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ManualSplitImportService {
    static final String EXTRACTION_RULE_VERSION = "manual-split-csv-v1";
    private static final String SOURCE_REJECTION_NOTE =
        "수동 분리 후보 CSV 가져오기 완료";

    private final LaboratoryResearchFieldCandidateRepository candidateRepository;
    private final ResearchFieldTextHasher textHasher;
    private final Clock clock;

    public ManualSplitImportService(
        LaboratoryResearchFieldCandidateRepository candidateRepository,
        ResearchFieldTextHasher textHasher
    ) {
        this.candidateRepository = candidateRepository;
        this.textHasher = textHasher;
        this.clock = Clock.systemDefaultZone();
    }

    @Transactional
    public ManualSplitImportResult importRows(
        List<ManualSplitCsvRow> rows,
        String reviewer
    ) {
        List<ManualSplitCsvRow> normalizedRows = List.copyOf(
            Objects.requireNonNull(rows, "MANUAL_SPLIT_ROWS_REQUIRED")
        );
        if (normalizedRows.isEmpty()) {
            throw new ManualSplitImportException("MANUAL_SPLIT_ROWS_EMPTY");
        }
        String normalizedReviewer = requireReviewer(reviewer);
        validateFileDuplicates(normalizedRows);

        Map<Long, List<ManualSplitCsvRow>> rowsBySource = normalizedRows.stream()
            .collect(Collectors.groupingBy(
                ManualSplitCsvRow::originalCandidateId,
                LinkedHashMap::new,
                Collectors.toList()
            ));

        int createdCount = 0;
        int unchangedCount = 0;
        int rejectedSourceCount = 0;
        for (List<ManualSplitCsvRow> sourceRows : rowsBySource.values()) {
            SourceImportResult sourceResult = importSource(
                sourceRows,
                normalizedReviewer
            );
            createdCount += sourceResult.createdCount();
            unchangedCount += sourceResult.unchangedCount();
            rejectedSourceCount += sourceResult.rejectedSourceCount();
        }
        candidateRepository.flush();
        return new ManualSplitImportResult(
            rowsBySource.size(),
            normalizedRows.size(),
            createdCount,
            unchangedCount,
            rejectedSourceCount
        );
    }

    private SourceImportResult importSource(
        List<ManualSplitCsvRow> rows,
        String reviewer
    ) {
        ManualSplitCsvRow firstRow = rows.getFirst();
        ensureSameLaboratory(rows, firstRow.laboratoryId());
        LaboratoryResearchFieldCandidate source = candidateRepository
            .findByIdForUpdate(firstRow.originalCandidateId())
            .orElseThrow(() -> importError(
                "MANUAL_SPLIT_SOURCE_NOT_FOUND",
                firstRow.lineNumber()
            ));
        if (!Objects.equals(
            source.getLaboratory().getId(),
            firstRow.laboratoryId()
        )) {
            throw importError(
                "MANUAL_SPLIT_SOURCE_LABORATORY_MISMATCH",
                firstRow.lineNumber()
            );
        }

        List<LaboratoryResearchFieldCandidate> laboratoryCandidates =
            candidateRepository.findAllByLaboratoryIdForUpdate(
                firstRow.laboratoryId()
            );
        List<LaboratoryResearchFieldCandidate> existingSplits =
            laboratoryCandidates.stream()
                .filter(candidate -> candidate.getSplitFromCandidate() != null)
                .filter(candidate -> Objects.equals(
                    candidate.getSplitFromCandidate().getId(),
                    source.getId()
                ))
                .toList();

        if (!existingSplits.isEmpty()) {
            validateIdempotentReplay(source, rows, existingSplits);
            return new SourceImportResult(0, rows.size(), 0);
        }

        Map<String, LaboratoryResearchFieldCandidate> existingByFieldKey =
            laboratoryCandidates.stream().collect(Collectors.toMap(
                LaboratoryResearchFieldCandidate::getSourceFieldKey,
                candidate -> candidate,
                (first, duplicate) -> {
                    throw new ManualSplitImportException(
                        "DUPLICATE_RESEARCH_FIELD_CANDIDATE_KEY"
                    );
                }
            ));
        LocalDateTime importedAt = LocalDateTime.now(clock);
        List<LaboratoryResearchFieldCandidate> newCandidates = rows.stream()
            .map(row -> createCandidate(
                source,
                row,
                existingByFieldKey,
                importedAt
            ))
            .toList();
        candidateRepository.saveAll(newCandidates);
        source.rejectAfterManualSplit(
            reviewer,
            SOURCE_REJECTION_NOTE,
            importedAt
        );
        return new SourceImportResult(newCandidates.size(), 0, 1);
    }

    private LaboratoryResearchFieldCandidate createCandidate(
        LaboratoryResearchFieldCandidate source,
        ManualSplitCsvRow row,
        Map<String, LaboratoryResearchFieldCandidate> existingByFieldKey,
        LocalDateTime importedAt
    ) {
        String sourceFieldKey = textHasher.hashFieldIdentity(row.candidateName());
        if (existingByFieldKey.containsKey(sourceFieldKey)) {
            throw importError(
                "MANUAL_SPLIT_FIELD_ALREADY_EXISTS",
                row.lineNumber()
            );
        }
        ResearchFieldCandidateDraft draft = new ResearchFieldCandidateDraft(
            sourceFieldKey,
            row.candidateName(),
            row.candidateName(),
            ResearchFieldExtractionMethod.MANUAL_SPLIT,
            row.sourceOrder()
        );
        return LaboratoryResearchFieldCandidate.manualSplit(
            source,
            draft,
            EXTRACTION_RULE_VERSION,
            importedAt
        );
    }

    private void validateIdempotentReplay(
        LaboratoryResearchFieldCandidate source,
        List<ManualSplitCsvRow> rows,
        List<LaboratoryResearchFieldCandidate> existingSplits
    ) {
        if (source.isStale()
            || source.getReviewStatus()
            != ResearchFieldCandidateReviewStatus.REJECTED) {
            throw new ManualSplitImportException(
                "MANUAL_SPLIT_REPLAY_SOURCE_STATE_INVALID"
            );
        }
        if (existingSplits.stream().anyMatch(
            LaboratoryResearchFieldCandidate::isStale
        )) {
            throw new ManualSplitImportException(
                "MANUAL_SPLIT_REPLAY_CONTAINS_STALE_CANDIDATE"
            );
        }
        Map<String, LaboratoryResearchFieldCandidate> existingByFieldKey =
            existingSplits.stream().collect(Collectors.toMap(
                LaboratoryResearchFieldCandidate::getSourceFieldKey,
                candidate -> candidate,
                (first, duplicate) -> {
                    throw new ManualSplitImportException(
                        "DUPLICATE_MANUAL_SPLIT_FIELD_KEY"
                    );
                }
            ));
        if (existingByFieldKey.size() != rows.size()) {
            throw new ManualSplitImportException(
                "MANUAL_SPLIT_REPLAY_CONTENT_MISMATCH"
            );
        }
        for (ManualSplitCsvRow row : rows) {
            String sourceFieldKey = textHasher.hashFieldIdentity(
                row.candidateName()
            );
            LaboratoryResearchFieldCandidate existing = existingByFieldKey.get(
                sourceFieldKey
            );
            if (existing == null
                || !Objects.equals(existing.getCandidateName(), row.candidateName())
                || existing.getSourceOrder() != row.sourceOrder()) {
                throw importError(
                    "MANUAL_SPLIT_REPLAY_CONTENT_MISMATCH",
                    row.lineNumber()
                );
            }
        }
    }

    private void validateFileDuplicates(List<ManualSplitCsvRow> rows) {
        Set<String> sourceOrders = new HashSet<>();
        Set<String> laboratoryFields = new HashSet<>();
        for (ManualSplitCsvRow row : rows) {
            String sourceOrderKey = row.originalCandidateId()
                + ":" + row.sourceOrder();
            if (!sourceOrders.add(sourceOrderKey)) {
                throw importError(
                    "MANUAL_SPLIT_SOURCE_ORDER_DUPLICATED",
                    row.lineNumber()
                );
            }
            String laboratoryFieldKey = row.laboratoryId()
                + ":" + textHasher.hashFieldIdentity(row.candidateName());
            if (!laboratoryFields.add(laboratoryFieldKey)) {
                throw importError(
                    "MANUAL_SPLIT_LABORATORY_FIELD_DUPLICATED",
                    row.lineNumber()
                );
            }
        }
    }

    private void ensureSameLaboratory(
        List<ManualSplitCsvRow> rows,
        long laboratoryId
    ) {
        ManualSplitCsvRow mismatch = rows.stream()
            .filter(row -> row.laboratoryId() != laboratoryId)
            .findFirst()
            .orElse(null);
        if (mismatch != null) {
            throw importError(
                "MANUAL_SPLIT_SOURCE_HAS_MULTIPLE_LABORATORIES",
                mismatch.lineNumber()
            );
        }
    }

    private String requireReviewer(String reviewer) {
        if (reviewer == null || reviewer.isBlank()) {
            throw new ManualSplitImportException("MANUAL_SPLIT_REVIEWER_REQUIRED");
        }
        String normalized = reviewer.trim();
        if (normalized.length() > 100) {
            throw new ManualSplitImportException("MANUAL_SPLIT_REVIEWER_TOO_LONG");
        }
        return normalized;
    }

    private ManualSplitImportException importError(String code, int lineNumber) {
        return new ManualSplitImportException(code + ": line=" + lineNumber);
    }

    private record SourceImportResult(
        int createdCount,
        int unchangedCount,
        int rejectedSourceCount
    ) { }
}

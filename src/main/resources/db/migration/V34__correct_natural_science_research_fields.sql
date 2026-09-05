-- V33 may already have been applied, so correct the data forward without
-- changing the historical migration checksum. This table also records the
-- canonical category expected for each corrected field.
-- MySQL cannot reference one TEMPORARY table more than once in a statement.
-- Use migration-scoped staging tables instead; Flyway's schema lock prevents
-- concurrent migrations, and the boundary drops make a repaired retry safe.
DROP TABLE IF EXISTS v34_research_field_merge_stage;
DROP TABLE IF EXISTS v34_research_field_name_correction_stage;

CREATE TABLE v34_research_field_name_correction_stage (
    old_name VARCHAR(100) NOT NULL,
    corrected_name VARCHAR(100) NOT NULL,
    category_code VARCHAR(50) NOT NULL,
    PRIMARY KEY (old_name)
);

CREATE TABLE v34_research_field_merge_stage (
    source_id BIGINT NOT NULL,
    target_id BIGINT NOT NULL,
    PRIMARY KEY (source_id)
);

INSERT INTO v34_research_field_name_correction_stage (
    old_name,
    corrected_name,
    category_code
)
VALUES
    (
        '고분자 화학 (고분자 합성) 4',
        '고분자 화학 (고분자 합성)',
        'CHEMISTRY_MATERIALS'
    ),
    (
        '다양한 응용 분야를 위한 그래핀-고분자 복합재료 개발 2',
        '다양한 응용 분야를 위한 그래핀-고분자 복합재료 개발',
        'CHEMISTRY_MATERIALS'
    ),
    (
        '빌량분석법',
        '질량분석법',
        'CHEMISTRY_MATERIALS'
    ),
    (
        '원자력 현미경(AFM)을 이용한 접착력 연구 3',
        '원자힘 현미경(AFM)을 이용한 접착력 연구',
        'CHEMISTRY_MATERIALS'
    ),
    (
        '폐수 중금속 이온 흡착 및 약물 전달을 위한 젤라틴 기반 하이드로겔 입자 제조 5',
        '폐수 중금속 이온 흡착 및 약물 전달을 위한 젤라틴 기반 하이드로겔 입자 제조',
        'BIOMED_HEALTH'
    );

-- Capture name collisions before in-place renames remove the old spelling.
INSERT INTO v34_research_field_merge_stage (source_id, target_id)
SELECT source.id, target.id
FROM v34_research_field_name_correction_stage correction
JOIN research_field source
    ON source.name = correction.old_name
JOIN research_field target
    ON target.name = correction.corrected_name
WHERE source.id <> target.id;

-- candidate_name is the reviewed/canonical value. Keep raw_field_text and
-- source hashes unchanged so the original crawl provenance remains intact.
UPDATE laboratory_research_field_candidate
SET candidate_name = (
        SELECT correction.corrected_name
        FROM v34_research_field_name_correction_stage correction
        WHERE correction.old_name =
            laboratory_research_field_candidate.candidate_name
    ),
    version = version + 1,
    updated_at = CURRENT_TIMESTAMP
WHERE EXISTS (
    SELECT 1
    FROM v34_research_field_name_correction_stage correction
    WHERE correction.old_name =
        laboratory_research_field_candidate.candidate_name
);

-- Preserve the existing research_field id whenever the corrected name does
-- not already exist. All foreign-key relationships then remain untouched.
UPDATE research_field
SET name = (
        SELECT correction.corrected_name
        FROM v34_research_field_name_correction_stage correction
        WHERE correction.old_name = research_field.name
    ),
    updated_at = CURRENT_TIMESTAMP
WHERE EXISTS (
    SELECT 1
    FROM v34_research_field_name_correction_stage correction
    WHERE correction.old_name = research_field.name
)
AND id NOT IN (
    SELECT source_id
    FROM v34_research_field_merge_stage
);

-- If both spellings already exist, use the corrected row as the canonical
-- target and merge every relationship before deleting the obsolete row.
INSERT INTO laboratory_research_field (laboratory_id, research_field_id)
SELECT source_link.laboratory_id, merge_target.target_id
FROM laboratory_research_field source_link
JOIN v34_research_field_merge_stage merge_target
    ON merge_target.source_id = source_link.research_field_id
LEFT JOIN laboratory_research_field target_link
    ON target_link.laboratory_id = source_link.laboratory_id
   AND target_link.research_field_id = merge_target.target_id
WHERE target_link.laboratory_id IS NULL;

INSERT INTO research_field_category_mapping (research_field_id, category_id)
SELECT merge_target.target_id, source_mapping.category_id
FROM research_field_category_mapping source_mapping
JOIN v34_research_field_merge_stage merge_target
    ON merge_target.source_id = source_mapping.research_field_id
LEFT JOIN research_field_category_mapping target_mapping
    ON target_mapping.research_field_id = merge_target.target_id
   AND target_mapping.category_id = source_mapping.category_id
WHERE target_mapping.research_field_id IS NULL;

UPDATE laboratory_research_field_candidate
SET promoted_research_field_id = (
        SELECT merge_target.target_id
        FROM v34_research_field_merge_stage merge_target
        WHERE merge_target.source_id =
            laboratory_research_field_candidate.promoted_research_field_id
    ),
    version = version + 1,
    updated_at = CURRENT_TIMESTAMP
WHERE promoted_research_field_id IN (
    SELECT source_id
    FROM v34_research_field_merge_stage
);

DELETE FROM laboratory_research_field
WHERE research_field_id IN (
    SELECT source_id
    FROM v34_research_field_merge_stage
);

DELETE FROM research_field_category_mapping
WHERE research_field_id IN (
    SELECT source_id
    FROM v34_research_field_merge_stage
);

DELETE FROM research_field
WHERE id IN (
    SELECT source_id
    FROM v34_research_field_merge_stage
);

-- V33 was reference-only. Add the intended category only when the corrected
-- research field exists; never create a research field from classification data.
INSERT INTO research_field_category_mapping (research_field_id, category_id)
SELECT field.id, category.id
FROM v34_research_field_name_correction_stage correction
JOIN research_field field
    ON field.name = correction.corrected_name
JOIN research_field_category category
    ON category.code = correction.category_code
LEFT JOIN research_field_category_mapping existing
    ON existing.research_field_id = field.id
   AND existing.category_id = category.id
WHERE existing.research_field_id IS NULL;

-- '1' is a numbered-list parsing artifact rather than a research field.
-- A candidate may have been re-reviewed after the invalid promotion. Preserve
-- its current review when only the historical promotion target is invalid.
UPDATE laboratory_research_field_candidate
SET promoted_research_field_id = NULL,
    promoted_at = NULL,
    promoted_reviewed_at = NULL,
    promoted_review_revision = NULL,
    version = version + 1,
    updated_at = CURRENT_TIMESTAMP
WHERE promoted_research_field_id IN (
    SELECT id
    FROM research_field
    WHERE name = '1'
)
AND (
    candidate_name IS NULL
    OR candidate_name <> '1'
);

-- A current candidate value of '1' is itself invalid. Reject it with valid
-- review metadata, clear any promotion audit data, and retain raw crawl
-- provenance for later inspection.
UPDATE laboratory_research_field_candidate
SET candidate_name = NULL,
    review_status = 'REJECTED',
    review_note = LEFT(
        CONCAT_WS(
            ' | ',
            NULLIF(TRIM(review_note), ''),
            'V34: 숫자 목록 파싱 오류로 제외'
        ),
        1000
    ),
    reviewed_by = 'flyway-v34',
    reviewed_at = CURRENT_TIMESTAMP,
    review_revision = GREATEST(review_revision + 1, 1),
    promoted_research_field_id = NULL,
    promoted_at = NULL,
    promoted_reviewed_at = NULL,
    promoted_review_revision = NULL,
    version = version + 1,
    updated_at = CURRENT_TIMESTAMP
WHERE candidate_name = '1';

DELETE FROM laboratory_research_field
WHERE research_field_id IN (
    SELECT id
    FROM research_field
    WHERE name = '1'
);

DELETE FROM research_field_category_mapping
WHERE research_field_id IN (
    SELECT id
    FROM research_field
    WHERE name = '1'
);

DELETE FROM research_field
WHERE name = '1';

DROP TABLE IF EXISTS v34_research_field_merge_stage;
DROP TABLE IF EXISTS v34_research_field_name_correction_stage;

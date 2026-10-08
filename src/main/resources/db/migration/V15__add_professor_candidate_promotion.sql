ALTER TABLE laboratory
    ADD COLUMN name_source VARCHAR(20) NOT NULL DEFAULT 'OFFICIAL';

ALTER TABLE laboratory
    ADD CONSTRAINT ck_laboratory_name_source CHECK (
        name_source IN ('OFFICIAL', 'GENERATED')
    );

ALTER TABLE laboratory
    ALTER COLUMN name_source DROP DEFAULT;

ALTER TABLE professor_crawl_candidate
    ADD COLUMN review_revision BIGINT NOT NULL DEFAULT 0;

UPDATE professor_crawl_candidate
SET review_revision = 1
WHERE reviewed_at IS NOT NULL;

ALTER TABLE professor_crawl_candidate
    ADD COLUMN promoted_professor_id BIGINT NULL;

ALTER TABLE professor_crawl_candidate
    ADD COLUMN promoted_laboratory_id BIGINT NULL;

ALTER TABLE professor_crawl_candidate
    ADD COLUMN promoted_at TIMESTAMP NULL;

ALTER TABLE professor_crawl_candidate
    ADD COLUMN promoted_reviewed_at TIMESTAMP NULL;

ALTER TABLE professor_crawl_candidate
    ADD COLUMN promoted_review_revision BIGINT NULL;

ALTER TABLE professor_crawl_candidate
    ADD CONSTRAINT uk_professor_crawl_candidate_promoted_professor
        UNIQUE (promoted_professor_id);

ALTER TABLE professor_crawl_candidate
    ADD CONSTRAINT uk_professor_crawl_candidate_promoted_laboratory
        UNIQUE (promoted_laboratory_id);

ALTER TABLE professor_crawl_candidate
    ADD CONSTRAINT fk_professor_crawl_candidate_promoted_professor
        FOREIGN KEY (promoted_professor_id)
        REFERENCES professor (id) ON DELETE RESTRICT;

ALTER TABLE professor_crawl_candidate
    ADD CONSTRAINT fk_professor_crawl_candidate_promoted_laboratory
        FOREIGN KEY (promoted_laboratory_id)
        REFERENCES laboratory (id) ON DELETE SET NULL;

-- MySQL forbids CHECK constraints from referencing columns that participate in
-- foreign-key referential actions. Entity-ID consistency is therefore guarded
-- by foreign keys and the promotion service; this CHECK covers audit metadata.
ALTER TABLE professor_crawl_candidate
    ADD CONSTRAINT ck_professor_crawl_candidate_promotion_state CHECK (
        (
            promoted_at IS NULL
            AND promoted_reviewed_at IS NULL
            AND promoted_review_revision IS NULL
        )
        OR
        (
            promoted_at IS NOT NULL
            AND promoted_reviewed_at IS NOT NULL
            AND promoted_review_revision IS NOT NULL
        )
    );

ALTER TABLE professor_crawl_candidate
    ADD CONSTRAINT ck_professor_crawl_candidate_review_revision CHECK (
        review_revision >= 0
        AND (
            promoted_review_revision IS NULL
            OR (
                promoted_review_revision > 0
                AND promoted_review_revision <= review_revision
            )
        )
    );

CREATE INDEX idx_professor_crawl_candidate_promotion
    ON professor_crawl_candidate (source_id, review_status, is_stale, id);

ALTER TABLE laboratory_research_field_candidate
    ADD COLUMN promoted_research_field_id BIGINT NULL;

ALTER TABLE laboratory_research_field_candidate
    ADD COLUMN promoted_at TIMESTAMP NULL;

ALTER TABLE laboratory_research_field_candidate
    ADD COLUMN promoted_reviewed_at TIMESTAMP NULL;

ALTER TABLE laboratory_research_field_candidate
    ADD COLUMN promoted_review_revision BIGINT NULL;

-- Several candidates can resolve to the same canonical research field, so
-- this lookup index must remain non-unique.
CREATE INDEX idx_lrf_candidate_promoted_research_field
    ON laboratory_research_field_candidate (promoted_research_field_id);

ALTER TABLE laboratory_research_field_candidate
    ADD CONSTRAINT fk_lrf_candidate_promoted_research_field
        FOREIGN KEY (promoted_research_field_id)
        REFERENCES research_field (id) ON DELETE RESTRICT;

-- MySQL does not allow CHECK constraints to reference columns that
-- participate in foreign-key referential actions. The foreign key and the
-- promotion service guard the target field; this CHECK covers audit metadata.
ALTER TABLE laboratory_research_field_candidate
    ADD CONSTRAINT ck_lrf_candidate_promotion_state CHECK (
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

ALTER TABLE laboratory_research_field_candidate
    ADD CONSTRAINT ck_lrf_candidate_promotion_review_revision CHECK (
        promoted_review_revision IS NULL
        OR (
            promoted_review_revision > 0
            AND promoted_review_revision <= review_revision
        )
    );

ALTER TABLE laboratory_research_field_candidate
    ADD COLUMN split_from_candidate_id BIGINT NULL;

ALTER TABLE laboratory_research_field_candidate
    DROP CONSTRAINT ck_lrf_candidate_extraction_method;

ALTER TABLE laboratory_research_field_candidate
    ADD CONSTRAINT ck_lrf_candidate_extraction_method CHECK (
        extraction_method IN (
            'WHOLE_TEXT',
            'DELIMITED',
            'LONG_TEXT',
            'MANUAL_SPLIT'
        )
    );

-- MySQL does not allow CHECK constraints to reference AUTO_INCREMENT columns
-- or columns participating in foreign-key referential actions. The domain and
-- import service enforce the MANUAL_SPLIT/source relationship; this FK keeps
-- the persisted source reference valid and prevents source deletion.
ALTER TABLE laboratory_research_field_candidate
    ADD CONSTRAINT fk_lrf_candidate_split_origin
        FOREIGN KEY (split_from_candidate_id)
        REFERENCES laboratory_research_field_candidate (id)
        ON DELETE RESTRICT;

CREATE INDEX idx_lrf_candidate_split_origin
    ON laboratory_research_field_candidate (split_from_candidate_id, id);

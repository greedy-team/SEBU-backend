ALTER TABLE laboratory_research_field_candidate
    DROP FOREIGN KEY fk_lrf_candidate_split_origin;

ALTER TABLE laboratory_research_field_candidate
    ADD CONSTRAINT fk_lrf_candidate_split_origin
        FOREIGN KEY (split_from_candidate_id)
        REFERENCES laboratory_research_field_candidate (id)
        ON DELETE CASCADE;

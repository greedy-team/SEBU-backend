-- V40/V41 are reserved by the academic-field / graduate-grade pull requests.
-- Expand both the source setting and the candidate provenance constraints.
-- Existing values, review state and promotion history remain unchanged.
ALTER TABLE crawl_source
    DROP CONSTRAINT ck_crawl_source_parser_type;

ALTER TABLE crawl_source
    ADD CONSTRAINT ck_crawl_source_parser_type CHECK (
        parser_type IN ('SEJONG_STANDARD', 'SEJONG_QUANTUM', 'SEJONG_AEROSPACE')
    );

ALTER TABLE professor_crawl_candidate
    DROP CONSTRAINT ck_professor_crawl_candidate_parser_type_at_crawl;

ALTER TABLE professor_crawl_candidate
    ADD CONSTRAINT ck_professor_crawl_candidate_parser_type_at_crawl CHECK (
        parser_type_at_crawl IN ('SEJONG_STANDARD', 'SEJONG_QUANTUM', 'SEJONG_AEROSPACE')
    );

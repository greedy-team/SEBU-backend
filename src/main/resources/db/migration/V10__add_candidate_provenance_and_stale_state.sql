ALTER TABLE professor_crawl_candidate
    ADD COLUMN source_url_at_crawl VARCHAR(512) NOT NULL DEFAULT '';

ALTER TABLE professor_crawl_candidate
    ADD COLUMN parser_type_at_crawl VARCHAR(50) NOT NULL DEFAULT 'SEJONG_STANDARD';

ALTER TABLE professor_crawl_candidate
    ADD COLUMN is_stale BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE professor_crawl_candidate candidate
SET source_url_at_crawl = (
        SELECT source.source_url
        FROM crawl_source source
        WHERE source.id = candidate.source_id
    ),
    parser_type_at_crawl = (
        SELECT source.parser_type
        FROM crawl_source source
        WHERE source.id = candidate.source_id
    );

ALTER TABLE professor_crawl_candidate
    ALTER COLUMN source_url_at_crawl DROP DEFAULT;

ALTER TABLE professor_crawl_candidate
    ALTER COLUMN parser_type_at_crawl DROP DEFAULT;

ALTER TABLE professor_crawl_candidate
    ADD CONSTRAINT ck_professor_crawl_candidate_source_url_at_crawl CHECK (
        source_url_at_crawl <> ''
    );

ALTER TABLE professor_crawl_candidate
    ADD CONSTRAINT ck_professor_crawl_candidate_parser_type_at_crawl CHECK (
        parser_type_at_crawl IN ('SEJONG_STANDARD', 'SEJONG_QUANTUM')
    );

CREATE INDEX idx_professor_crawl_candidate_current_review
    ON professor_crawl_candidate (is_stale, review_status);

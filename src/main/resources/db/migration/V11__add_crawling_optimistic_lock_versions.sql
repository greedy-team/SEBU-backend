ALTER TABLE crawl_source
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE professor_crawl_candidate
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE crawl_source
    ALTER COLUMN version DROP DEFAULT;

ALTER TABLE professor_crawl_candidate
    ALTER COLUMN version DROP DEFAULT;

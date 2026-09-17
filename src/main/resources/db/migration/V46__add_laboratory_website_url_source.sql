ALTER TABLE laboratory
    ADD COLUMN website_url_source VARCHAR(20) NULL;

UPDATE laboratory
SET website_url_source = 'CRAWLED'
WHERE website_url IS NOT NULL;

-- The source faculty page exposes only the invalid value "http" for this
-- professor. The verified System Design Laboratory URL is maintained manually.
UPDATE laboratory
SET website_url = 'https://sdl.sejong.ac.kr/',
    website_url_source = 'MANUAL',
    updated_at = CURRENT_TIMESTAMP
WHERE professor_id IN (
    SELECT id
    FROM professor
    WHERE email = 'ghpark@sejong.ac.kr'
)
AND name = '박기호 교수님 연구실'
AND deleted_at IS NULL;

ALTER TABLE laboratory
    ADD CONSTRAINT ck_laboratory_website_url_source CHECK (
        website_url_source IS NULL
        OR website_url_source IN ('CRAWLED', 'MANUAL')
    );

ALTER TABLE laboratory
    ADD CONSTRAINT ck_laboratory_website_url_source_presence CHECK (
        (website_url IS NULL AND website_url_source IS NULL)
        OR (website_url IS NOT NULL AND website_url_source IS NOT NULL)
    );

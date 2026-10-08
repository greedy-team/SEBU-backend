ALTER TABLE professor
    ADD COLUMN position VARCHAR(100) NULL;

ALTER TABLE laboratory
    ADD COLUMN description VARCHAR(2000) NULL;

CREATE TABLE crawl_source (
    id BIGINT NOT NULL AUTO_INCREMENT,
    department_id BIGINT NOT NULL,
    source_name VARCHAR(150) NOT NULL,
    source_url VARCHAR(512) NOT NULL,
    parser_type VARCHAR(50) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    last_crawled_at TIMESTAMP NULL,
    last_crawl_status VARCHAR(30) NOT NULL DEFAULT 'NOT_STARTED',
    last_error_message VARCHAR(1000) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_crawl_source PRIMARY KEY (id),
    CONSTRAINT uk_crawl_source_url UNIQUE (source_url),
    CONSTRAINT ck_crawl_source_parser_type CHECK (
        parser_type IN ('SEJONG_STANDARD', 'SEJONG_QUANTUM')
    ),
    CONSTRAINT ck_crawl_source_status CHECK (
        last_crawl_status IN ('NOT_STARTED', 'SUCCESS', 'FAILED')
    ),
    CONSTRAINT fk_crawl_source_department FOREIGN KEY (department_id)
        REFERENCES department (id) ON DELETE RESTRICT
);

CREATE INDEX idx_crawl_source_department ON crawl_source (department_id);
CREATE INDEX idx_crawl_source_active ON crawl_source (is_active);

CREATE TABLE professor_crawl_candidate (
    id BIGINT NOT NULL AUTO_INCREMENT,
    source_id BIGINT NOT NULL,
    professor_name VARCHAR(100) NOT NULL,
    position VARCHAR(100) NULL,
    email VARCHAR(255) NULL,
    laboratory_name VARCHAR(150) NULL,
    research_introduction VARCHAR(2000) NULL,
    homepage_url VARCHAR(2048) NULL,
    review_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    review_note VARCHAR(1000) NULL,
    reviewed_by VARCHAR(100) NULL,
    reviewed_at TIMESTAMP NULL,
    crawled_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_professor_crawl_candidate PRIMARY KEY (id),
    CONSTRAINT uk_professor_crawl_candidate_source_name UNIQUE (source_id, professor_name),
    CONSTRAINT ck_professor_crawl_candidate_review_status CHECK (
        review_status IN ('PENDING', 'APPROVED', 'REJECTED')
    ),
    CONSTRAINT fk_professor_crawl_candidate_source FOREIGN KEY (source_id)
        REFERENCES crawl_source (id) ON DELETE RESTRICT
);

CREATE INDEX idx_professor_crawl_candidate_review_status
    ON professor_crawl_candidate (review_status);
CREATE INDEX idx_professor_crawl_candidate_email
    ON professor_crawl_candidate (email);

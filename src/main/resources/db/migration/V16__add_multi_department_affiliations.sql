CREATE TABLE professor_department (
    professor_id BIGINT NOT NULL,
    department_id BIGINT NOT NULL,
    position VARCHAR(100) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_professor_department PRIMARY KEY (professor_id, department_id),
    CONSTRAINT fk_professor_department_professor FOREIGN KEY (professor_id)
        REFERENCES professor (id) ON DELETE CASCADE,
    CONSTRAINT fk_professor_department_department FOREIGN KEY (department_id)
        REFERENCES department (id) ON DELETE RESTRICT
);

CREATE INDEX idx_professor_department_department
    ON professor_department (department_id, professor_id);

CREATE TABLE laboratory_department (
    laboratory_id BIGINT NOT NULL,
    department_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_laboratory_department PRIMARY KEY (laboratory_id, department_id),
    CONSTRAINT fk_laboratory_department_laboratory FOREIGN KEY (laboratory_id)
        REFERENCES laboratory (id) ON DELETE CASCADE,
    CONSTRAINT fk_laboratory_department_department FOREIGN KEY (department_id)
        REFERENCES department (id) ON DELETE RESTRICT
);

CREATE INDEX idx_laboratory_department_department
    ON laboratory_department (department_id, laboratory_id);

INSERT INTO professor_department (professor_id, department_id, position)
SELECT id, department_id, position
FROM professor;

INSERT INTO laboratory_department (laboratory_id, department_id)
SELECT id, department_id
FROM laboratory;

CREATE INDEX idx_professor_crawl_candidate_promoted_professor
    ON professor_crawl_candidate (promoted_professor_id);

CREATE INDEX idx_professor_crawl_candidate_promoted_laboratory
    ON professor_crawl_candidate (promoted_laboratory_id);

ALTER TABLE professor_crawl_candidate
    DROP CONSTRAINT fk_professor_crawl_candidate_promoted_professor;

ALTER TABLE professor_crawl_candidate
    DROP CONSTRAINT fk_professor_crawl_candidate_promoted_laboratory;

ALTER TABLE professor_crawl_candidate
    DROP CONSTRAINT uk_professor_crawl_candidate_promoted_professor;

ALTER TABLE professor_crawl_candidate
    DROP CONSTRAINT uk_professor_crawl_candidate_promoted_laboratory;

ALTER TABLE professor_crawl_candidate
    ADD CONSTRAINT fk_professor_crawl_candidate_promoted_professor
        FOREIGN KEY (promoted_professor_id)
        REFERENCES professor (id) ON DELETE RESTRICT;

ALTER TABLE professor_crawl_candidate
    ADD CONSTRAINT fk_professor_crawl_candidate_promoted_laboratory
        FOREIGN KEY (promoted_laboratory_id)
        REFERENCES laboratory (id) ON DELETE SET NULL;

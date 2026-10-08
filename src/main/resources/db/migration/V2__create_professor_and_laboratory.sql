CREATE TABLE professor (
    id BIGINT NOT NULL AUTO_INCREMENT,
    department_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(255) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_professor PRIMARY KEY (id),
    CONSTRAINT uk_professor_email UNIQUE (email),
    CONSTRAINT fk_professor_department FOREIGN KEY (department_id)
        REFERENCES department (id) ON DELETE RESTRICT
);
CREATE TABLE laboratory (
    id BIGINT NOT NULL AUTO_INCREMENT,
    professor_id BIGINT NOT NULL,
    department_id BIGINT NOT NULL,
    name VARCHAR(150) NOT NULL,
    website_url VARCHAR(2048) NULL,
    recruitment_status VARCHAR(30) NOT NULL,
    deleted_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_laboratory PRIMARY KEY (id),
    CONSTRAINT ck_laboratory_recruitment_status CHECK (
        recruitment_status IN ('RECRUITING', 'ALWAYS_OPEN', 'CLOSED')
    ),
    CONSTRAINT fk_laboratory_professor FOREIGN KEY (professor_id)
        REFERENCES professor (id) ON DELETE RESTRICT,
    CONSTRAINT fk_laboratory_department FOREIGN KEY (department_id)
        REFERENCES department (id) ON DELETE RESTRICT
);

CREATE TABLE research_field_category (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500) NOT NULL,
    display_order INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_research_field_category PRIMARY KEY (id),
    CONSTRAINT uk_research_field_category_code UNIQUE (code),
    CONSTRAINT uk_research_field_category_name UNIQUE (name),
    CONSTRAINT uk_research_field_category_display_order UNIQUE (display_order),
    CONSTRAINT ck_research_field_category_code CHECK (TRIM(code) <> ''),
    CONSTRAINT ck_research_field_category_name CHECK (TRIM(name) <> ''),
    CONSTRAINT ck_research_field_category_description CHECK (TRIM(description) <> ''),
    CONSTRAINT ck_research_field_category_display_order CHECK (display_order > 0)
);

CREATE TABLE research_field_category_mapping (
    research_field_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    CONSTRAINT pk_research_field_category_mapping
        PRIMARY KEY (research_field_id, category_id),
    CONSTRAINT fk_rfcm_research_field FOREIGN KEY (research_field_id)
        REFERENCES research_field (id) ON DELETE CASCADE,
    CONSTRAINT fk_rfcm_category FOREIGN KEY (category_id)
        REFERENCES research_field_category (id) ON DELETE RESTRICT
);

CREATE INDEX idx_rfcm_category_research_field
    ON research_field_category_mapping (category_id, research_field_id);

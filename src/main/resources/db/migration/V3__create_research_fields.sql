CREATE TABLE research_field (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_research_field PRIMARY KEY (id),
    CONSTRAINT uk_research_field_name UNIQUE (name)
);
CREATE TABLE laboratory_research_field (
    laboratory_id BIGINT NOT NULL,
    research_field_id BIGINT NOT NULL,
    CONSTRAINT pk_laboratory_research_field PRIMARY KEY (laboratory_id, research_field_id),
    CONSTRAINT fk_lrf_laboratory FOREIGN KEY (laboratory_id)
        REFERENCES laboratory (id) ON DELETE CASCADE,
    CONSTRAINT fk_lrf_research_field FOREIGN KEY (research_field_id)
        REFERENCES research_field (id) ON DELETE RESTRICT
);

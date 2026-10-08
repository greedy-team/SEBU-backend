CREATE TABLE college (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_college PRIMARY KEY (id),
    CONSTRAINT uk_college_name UNIQUE (name)
);
CREATE TABLE department (
    id BIGINT NOT NULL AUTO_INCREMENT,
    college_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_department PRIMARY KEY (id),
    CONSTRAINT uk_department_college_name UNIQUE (college_id, name),
    CONSTRAINT fk_department_college FOREIGN KEY (college_id)
        REFERENCES college (id) ON DELETE RESTRICT
);

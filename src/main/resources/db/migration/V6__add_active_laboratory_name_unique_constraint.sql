ALTER TABLE laboratory
    ADD COLUMN active_name VARCHAR(150)
        GENERATED ALWAYS AS (
            CASE WHEN deleted_at IS NULL THEN name ELSE NULL END
        );

ALTER TABLE laboratory
    ADD CONSTRAINT uk_laboratory_department_active_name
        UNIQUE (department_id, active_name);

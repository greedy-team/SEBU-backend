ALTER TABLE laboratory
    DROP CONSTRAINT ck_laboratory_recruitment_status;

ALTER TABLE laboratory
    ADD CONSTRAINT ck_laboratory_recruitment_status CHECK (
        recruitment_status IN ('RECRUITING', 'ALWAYS_OPEN', 'CLOSED', 'UNKNOWN')
    );

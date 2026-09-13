-- Keep existing years and NULL; 5 represents a user-selected graduate status.
ALTER TABLE app_user
    DROP CONSTRAINT ck_app_user_grade;

ALTER TABLE app_user
    ADD CONSTRAINT ck_app_user_grade CHECK (
        grade IS NULL OR grade BETWEEN 1 AND 5
    );

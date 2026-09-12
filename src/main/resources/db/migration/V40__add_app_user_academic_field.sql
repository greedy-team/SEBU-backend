ALTER TABLE app_user
    ADD COLUMN academic_field VARCHAR(32) NULL;

ALTER TABLE app_user
    ADD CONSTRAINT ck_app_user_academic_field CHECK (
        academic_field IS NULL
        OR academic_field IN (
            'HUMANITIES', 'SOCIAL_BUSINESS', 'EDUCATION', 'NATURAL_SCIENCE',
            'ENGINEERING', 'ARTS_SPORTS', 'OTHER_UNDECIDED'
        )
    );

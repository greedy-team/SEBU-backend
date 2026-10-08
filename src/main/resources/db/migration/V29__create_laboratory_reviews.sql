CREATE TABLE laboratory_review (
                                   id BIGINT NOT NULL AUTO_INCREMENT,
                                   laboratory_id BIGINT NOT NULL,
                                   author_id BIGINT NOT NULL,

                                   category VARCHAR(30) NOT NULL,
                                   research_intensity VARCHAR(30) NOT NULL,
                                   compensation VARCHAR(30) NOT NULL,
                                   atmosphere VARCHAR(30) NOT NULL,

                                   content VARCHAR(2000) NOT NULL,

                                   participation_year INT NOT NULL,
                                   participation_term VARCHAR(30) NOT NULL,

                                   created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                   updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                   deleted_at TIMESTAMP NULL,

                                   active_marker TINYINT
                                       GENERATED ALWAYS AS (
                                           CASE
                                               WHEN deleted_at IS NULL THEN 1
                                               ELSE NULL
                                               END
                                           ),

                                   CONSTRAINT pk_laboratory_review
                                       PRIMARY KEY (id),

                                   CONSTRAINT uk_laboratory_review_active_author_laboratory
                                       UNIQUE (author_id, laboratory_id, active_marker),

                                   CONSTRAINT ck_laboratory_review_category
                                       CHECK (
                                           category IN (
                                                        'ACCEPTANCE',
                                                        'RESEARCH_ENVIRONMENT',
                                                        'PROFESSOR_STYLE',
                                                        'COMPENSATION_WELFARE',
                                                        'OTHER'
                                               )
                                           ),

                                   CONSTRAINT ck_laboratory_review_research_intensity
                                       CHECK (
                                           research_intensity IN (
                                                                  'LOW',
                                                                  'MEDIUM',
                                                                  'HIGH'
                                               )
                                           ),

                                   CONSTRAINT ck_laboratory_review_compensation
                                       CHECK (
                                           compensation IN (
                                                            'NONE',
                                                            'SMALL_AMOUNT',
                                                            'SUFFICIENT'
                                               )
                                           ),

                                   CONSTRAINT ck_laboratory_review_atmosphere
                                       CHECK (
                                           atmosphere IN (
                                                          'COMPETITIVE',
                                                          'NORMAL',
                                                          'COOPERATIVE'
                                               )
                                           ),

                                   CONSTRAINT ck_laboratory_review_participation_term
                                       CHECK (
                                           participation_term IN (
                                                                  'FIRST_SEMESTER',
                                                                  'SUMMER_BREAK',
                                                                  'SECOND_SEMESTER',
                                                                  'WINTER_BREAK'
                                               )
                                           ),

                                   CONSTRAINT ck_laboratory_review_participation_year
                                       CHECK (
                                           participation_year >= 2000
                                           ),

                                   CONSTRAINT fk_laboratory_review_laboratory
                                       FOREIGN KEY (laboratory_id)
                                           REFERENCES laboratory (id)
                                           ON DELETE CASCADE,

                                   CONSTRAINT fk_laboratory_review_author
                                       FOREIGN KEY (author_id)
                                           REFERENCES app_user (id)
                                           ON DELETE CASCADE
);

CREATE TABLE laboratory_review_tag (
                                       review_id BIGINT NOT NULL,
                                       tag VARCHAR(50) NOT NULL,

                                       CONSTRAINT pk_laboratory_review_tag
                                           PRIMARY KEY (review_id, tag),

                                       CONSTRAINT ck_laboratory_review_tag
                                           CHECK (
                                               tag IN (
                                                       'RESEARCH_IMMERSION',
                                                       'STUDY_RESEARCH_BALANCE',
                                                       'FREE_ATMOSPHERE',
                                                       'STRUCTURED_RESEARCH_GUIDANCE',
                                                       'PROFESSOR_COMMUNICATION',
                                                       'ACTIVE_FEEDBACK',
                                                       'PROJECT_OPPORTUNITY',
                                                       'DIVERSE_RESEARCH_EXPERIENCE',
                                                       'INTEREST_FIELD_RESEARCH',
                                                       'CAREER_CONNECTION'
                                                   )
                                               ),

                                       CONSTRAINT fk_laboratory_review_tag_review
                                           FOREIGN KEY (review_id)
                                               REFERENCES laboratory_review (id)
                                               ON DELETE CASCADE
);

ALTER TABLE college ADD COLUMN community_group VARCHAR(32) NULL;

ALTER TABLE college ADD CONSTRAINT ck_college_community_group CHECK (
    community_group IS NULL OR community_group IN (
        'HUMANITIES_SOCIAL', 'BUSINESS_HOSPITALITY', 'NATURAL_LIFE',
        'AI_CONVERGENCE', 'ENGINEERING', 'ARTS_PHYSICAL'
    )
);

UPDATE college
SET community_group = CASE
    WHEN name IN ('인문과학대학', '사회과학대학') THEN 'HUMANITIES_SOCIAL'
    WHEN name IN ('경영경제대학', '호텔관광대학') THEN 'BUSINESS_HOSPITALITY'
    WHEN name IN ('자연과학대학', '생명과학대학') THEN 'NATURAL_LIFE'
    WHEN name = '인공지능융합대학' THEN 'AI_CONVERGENCE'
    WHEN name = '공과대학' THEN 'ENGINEERING'
    WHEN name = '예체능대학' THEN 'ARTS_PHYSICAL'
END
WHERE name IN (
    '인문과학대학', '사회과학대학', '경영경제대학', '호텔관광대학',
    '자연과학대학', '생명과학대학', '인공지능융합대학', '공과대학', '예체능대학'
);

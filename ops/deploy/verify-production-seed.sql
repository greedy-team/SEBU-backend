-- Read-only assertions for smoke-prod.sh's freshly migrated, disposable MySQL.
-- Do not use these empty-activity expectations against a live service database.
-- Output columns: check kind, label, value. Never output user data or tokens.
SELECT 'zero', 'app_user', COUNT(*) FROM app_user
UNION ALL SELECT 'zero', 'refresh_token', COUNT(*) FROM refresh_token
UNION ALL SELECT 'zero', 'account_recovery_token', COUNT(*) FROM account_recovery_token
UNION ALL SELECT 'zero', 'bookmark', COUNT(*) FROM bookmark
UNION ALL SELECT 'zero', 'community_post', COUNT(*) FROM community_post
UNION ALL SELECT 'zero', 'community_comment', COUNT(*) FROM community_comment
UNION ALL SELECT 'zero', 'community_post_like', COUNT(*) FROM community_post_like
UNION ALL SELECT 'zero', 'community_post_bookmark', COUNT(*) FROM community_post_bookmark
UNION ALL SELECT 'zero', 'laboratory_review', COUNT(*) FROM laboratory_review
UNION ALL SELECT 'zero', 'laboratory_review_tag', COUNT(*) FROM laboratory_review_tag
UNION ALL SELECT 'zero', 'professor_crawl_candidate', COUNT(*) FROM professor_crawl_candidate
UNION ALL SELECT 'zero', 'laboratory_research_field_candidate', COUNT(*) FROM laboratory_research_field_candidate;

-- These are reviewed deployment catalogues, not test users or posts.
SELECT 'nonempty', 'college', COUNT(*) FROM college
UNION ALL SELECT 'nonempty', 'department', COUNT(*) FROM department
UNION ALL SELECT 'nonempty', 'crawl_source', COUNT(*) FROM crawl_source
UNION ALL SELECT 'nonempty', 'professor', COUNT(*) FROM professor
UNION ALL SELECT 'nonempty', 'laboratory', COUNT(*) FROM laboratory
UNION ALL SELECT 'nonempty', 'professor_department', COUNT(*) FROM professor_department
UNION ALL SELECT 'nonempty', 'laboratory_department', COUNT(*) FROM laboratory_department
UNION ALL SELECT 'nonempty', 'research_field', COUNT(*) FROM research_field
UNION ALL SELECT 'nonempty', 'laboratory_research_field', COUNT(*) FROM laboratory_research_field
UNION ALL SELECT 'nonempty', 'research_field_category', COUNT(*) FROM research_field_category
UNION ALL SELECT 'nonempty', 'research_field_category_mapping', COUNT(*) FROM research_field_category_mapping;

SELECT 'zero', 'missing_required_colleges', 9 - COUNT(*)
FROM college
WHERE name IN ('인문과학대학', '사회과학대학', '경영경제대학', '호텔관광대학',
               '자연과학대학', '생명과학대학', '인공지능융합대학', '공과대학', '예체능대학');
SELECT 'zero', 'colleges_without_departments', COUNT(*)
FROM college c WHERE NOT EXISTS (SELECT 1 FROM department d WHERE d.college_id = c.id);
SELECT 'zero', 'leftover_migration_staging_tables', COUNT(*)
FROM information_schema.tables
WHERE table_schema = DATABASE() AND table_name REGEXP '^v[0-9]+_';
SELECT 'zero', 'failed_flyway_migrations', COUNT(*) FROM flyway_schema_history WHERE success = 0;
SELECT 'migration', version, success FROM flyway_schema_history
WHERE version IS NOT NULL ORDER BY installed_rank;

-- Show gaps in reviewed research data without inventing or importing extra labs.
SELECT 'college', c.name,
       CONCAT('departments=', (SELECT COUNT(*) FROM department d WHERE d.college_id = c.id),
              ', active_labs=', (SELECT COUNT(DISTINCT ld.laboratory_id)
                                FROM laboratory_department ld
                                JOIN department d ON d.id = ld.department_id
                                JOIN laboratory l ON l.id = ld.laboratory_id
                                WHERE d.college_id = c.id AND l.deleted_at IS NULL))
FROM college c ORDER BY c.name;
SELECT 'category', c.code,
       CONCAT(c.name, ', parent=', COALESCE(p.code, 'ROOT'))
FROM research_field_category c
LEFT JOIN research_field_category p ON p.id = c.parent_id
ORDER BY c.display_order, c.id;

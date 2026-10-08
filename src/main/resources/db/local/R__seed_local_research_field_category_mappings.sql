INSERT INTO research_field_category_mapping (research_field_id, category_id)
SELECT field.id, category.id
FROM (
    SELECT '인공지능' AS research_field_name, 'AI_ML' AS category_code
    UNION ALL SELECT '머신러닝', 'AI_ML'
    UNION ALL SELECT '컴퓨터비전', 'SIGNAL_MEDIA'
    UNION ALL SELECT '딥러닝', 'AI_ML'
) seed
JOIN research_field field
    ON field.name = seed.research_field_name
JOIN research_field_category category
    ON category.code = seed.category_code
LEFT JOIN research_field_category_mapping existing
    ON existing.research_field_id = field.id
   AND existing.category_id = category.id
WHERE existing.research_field_id IS NULL;

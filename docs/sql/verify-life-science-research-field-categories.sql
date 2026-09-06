-- 조회 전용. 생명과학대 source_id 16/17/18/19의 승격된 연구실 기준.
USE sebu;

-- 1. 전체 카테고리 (이번 추가 후 기존 21 + 신규 3 = 24개)
SELECT id, code, name, description, display_order
FROM research_field_category ORDER BY display_order, id;

-- 2. 생명과학대 분야별 카테고리 전체
SELECT f.id AS research_field_id, f.name AS research_field_name,
       GROUP_CONCAT(DISTINCT c.name ORDER BY c.display_order SEPARATOR ', ') AS categories
FROM research_field f
LEFT JOIN research_field_category_mapping m ON m.research_field_id=f.id
LEFT JOIN research_field_category c ON c.id=m.category_id
WHERE EXISTS (
    SELECT 1 FROM laboratory_research_field lf
    JOIN professor_crawl_candidate pc ON pc.promoted_laboratory_id=lf.laboratory_id
    WHERE lf.research_field_id=f.id AND pc.source_id IN (16,17,18,19)
      AND pc.is_stale=FALSE AND pc.review_status='APPROVED'
)
GROUP BY f.id,f.name ORDER BY f.name,f.id;

-- 3. 대상 분야 수 / 미매핑 수 (이번 검증 기준 193 / 0)
SELECT COUNT(*) AS field_count,
       COALESCE(SUM(NOT EXISTS (
           SELECT 1 FROM research_field_category_mapping m WHERE m.research_field_id=f.id
       )),0) AS unmapped_count
FROM research_field f
WHERE EXISTS (
    SELECT 1 FROM laboratory_research_field lf
    JOIN professor_crawl_candidate pc ON pc.promoted_laboratory_id=lf.laboratory_id
    WHERE lf.research_field_id=f.id AND pc.source_id IN (16,17,18,19)
      AND pc.is_stale=FALSE AND pc.review_status='APPROVED'
);

-- 4. 동일 분야-카테고리 쌍 중복 수 (0이어야 함; 서로 다른 복수 카테고리는 정상)
SELECT COUNT(*) AS duplicate_pair_count
FROM (
    SELECT research_field_id,category_id FROM research_field_category_mapping
    GROUP BY research_field_id,category_id HAVING COUNT(*)>1
) duplicated;

-- 5. 승인됐지만 승격/연구실 연결이 완성되지 않은 후보 (0건이어야 함)
SELECT c.id,c.laboratory_id,c.candidate_name
FROM laboratory_research_field_candidate c
LEFT JOIN research_field f ON f.id=c.promoted_research_field_id
LEFT JOIN laboratory_research_field lf
    ON lf.laboratory_id=c.laboratory_id AND lf.research_field_id=f.id
WHERE c.is_stale=FALSE AND c.review_status='APPROVED'
  AND EXISTS (
      SELECT 1 FROM professor_crawl_candidate pc
      WHERE pc.promoted_laboratory_id=c.laboratory_id AND pc.source_id IN (16,17,18,19)
        AND pc.is_stale=FALSE AND pc.review_status='APPROVED'
  )
  AND (c.promoted_at IS NULL OR c.promoted_review_revision IS NULL
       OR c.promoted_review_revision<>c.review_revision OR f.id IS NULL OR lf.laboratory_id IS NULL);

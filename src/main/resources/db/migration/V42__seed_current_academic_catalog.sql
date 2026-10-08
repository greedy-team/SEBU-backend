-- Current undergraduate catalog, checked 2026-09-15.
-- Source: https://www.sejong.ac.kr/kor/college/academics.do
-- Legacy mappings and special admission units are intentionally deferred.
-- Add missing rows only: preserve existing IDs, names, affiliations and timestamps.
-- Schools and their majors remain distinct Department rows in the existing flat schema.
-- Do not seed legacy aliases, special admission units, users or crawl sources here.

INSERT INTO college (name)
SELECT seed.name
FROM (
    SELECT '인문과학대학' AS name
    UNION ALL SELECT '사회과학대학'
    UNION ALL SELECT '경영경제대학'
    UNION ALL SELECT '호텔관광대학'
    UNION ALL SELECT '자연과학대학'
    UNION ALL SELECT '생명과학대학'
    UNION ALL SELECT '인공지능융합대학'
    UNION ALL SELECT '공과대학'
    UNION ALL SELECT '예체능대학'
) seed
WHERE NOT EXISTS (SELECT 1 FROM college c WHERE c.name = seed.name);

-- 인문과학대학
INSERT INTO department (college_id, name)
SELECT c.id, seed.name
FROM college c
CROSS JOIN (
    SELECT '국어국문학과' AS name
    UNION ALL SELECT '국제학부'
    UNION ALL SELECT '영어데이터융합전공'
    UNION ALL SELECT '국제일본학전공'
    UNION ALL SELECT '중국통상학전공'
    UNION ALL SELECT '역사학과'
    UNION ALL SELECT '교육학과'
    UNION ALL SELECT '글로벌인재학부'
    UNION ALL SELECT '한국언어문화전공'
    UNION ALL SELECT '국제통상전공'
    UNION ALL SELECT '국제협력전공'
) seed
WHERE c.name = '인문과학대학'
  AND NOT EXISTS (
      SELECT 1 FROM department d
      WHERE d.college_id = c.id AND d.name = seed.name
  );

-- 사회과학대학
INSERT INTO department (college_id, name)
SELECT c.id, seed.name
FROM college c
CROSS JOIN (
    SELECT '행정학과' AS name
    UNION ALL SELECT '미디어커뮤니케이션학과'
    UNION ALL SELECT '법학과'
) seed
WHERE c.name = '사회과학대학'
  AND NOT EXISTS (
      SELECT 1 FROM department d
      WHERE d.college_id = c.id AND d.name = seed.name
  );

-- 경영경제대학
INSERT INTO department (college_id, name)
SELECT c.id, seed.name
FROM college c
CROSS JOIN (
    SELECT '경영학부' AS name
    UNION ALL SELECT '경제학과'
) seed
WHERE c.name = '경영경제대학'
  AND NOT EXISTS (
      SELECT 1 FROM department d
      WHERE d.college_id = c.id AND d.name = seed.name
  );

-- 호텔관광대학
INSERT INTO department (college_id, name)
SELECT c.id, seed.name
FROM college c
CROSS JOIN (
    SELECT '호텔관광외식경영학부' AS name
    UNION ALL SELECT '호텔관광경영학전공'
    UNION ALL SELECT '외식경영학전공'
    UNION ALL SELECT '호텔외식관광프랜차이즈경영학과'
    UNION ALL SELECT '조리서비스경영학과'
    UNION ALL SELECT '호텔외식비즈니스학과'
) seed
WHERE c.name = '호텔관광대학'
  AND NOT EXISTS (
      SELECT 1 FROM department d
      WHERE d.college_id = c.id AND d.name = seed.name
  );

-- 자연과학대학
INSERT INTO department (college_id, name)
SELECT c.id, seed.name
FROM college c
CROSS JOIN (
    SELECT '수학통계학과' AS name
    UNION ALL SELECT '물리천문학과'
    UNION ALL SELECT '화학과'
) seed
WHERE c.name = '자연과학대학'
  AND NOT EXISTS (
      SELECT 1 FROM department d
      WHERE d.college_id = c.id AND d.name = seed.name
  );

-- 생명과학대학
INSERT INTO department (college_id, name)
SELECT c.id, seed.name
FROM college c
CROSS JOIN (
    SELECT '생명시스템학부' AS name
    UNION ALL SELECT '식품생명공학전공'
    UNION ALL SELECT '바이오융합공학전공'
    UNION ALL SELECT '바이오산업자원공학전공'
    UNION ALL SELECT '스마트생명산업융합학과'
) seed
WHERE c.name = '생명과학대학'
  AND NOT EXISTS (
      SELECT 1 FROM department d
      WHERE d.college_id = c.id AND d.name = seed.name
  );

-- 인공지능융합대학
INSERT INTO department (college_id, name)
SELECT c.id, seed.name
FROM college c
CROSS JOIN (
    SELECT 'AI융합전자공학과' AS name
    UNION ALL SELECT '반도체시스템공학과'
    UNION ALL SELECT '컴퓨터공학과'
    UNION ALL SELECT '정보보호학과'
    UNION ALL SELECT '양자지능정보학과'
    UNION ALL SELECT '창의소프트학부'
    UNION ALL SELECT '디자인이노베이션전공'
    UNION ALL SELECT '만화애니메이션텍전공'
    UNION ALL SELECT '지능IoT학과'
    UNION ALL SELECT '사이버국방학과'
    UNION ALL SELECT '국방AI로봇융합공학과'
    UNION ALL SELECT '인공지능데이터사이언스학과'
    UNION ALL SELECT 'AI로봇학과'
    UNION ALL SELECT '지능정보융합학과'
    UNION ALL SELECT '콘텐츠소프트웨어학과'
) seed
WHERE c.name = '인공지능융합대학'
  AND NOT EXISTS (
      SELECT 1 FROM department d
      WHERE d.college_id = c.id AND d.name = seed.name
  );

-- 공과대학
INSERT INTO department (college_id, name)
SELECT c.id, seed.name
FROM college c
CROSS JOIN (
    SELECT '건축공학과' AS name
    UNION ALL SELECT '건축학과'
    UNION ALL SELECT '건설환경공학과'
    UNION ALL SELECT '환경융합공학과'
    UNION ALL SELECT '에너지자원공학과'
    UNION ALL SELECT '기계공학과'
    UNION ALL SELECT '우주항공시스템공학부'
    UNION ALL SELECT '우주항공공학전공'
    UNION ALL SELECT '지능형드론융합전공'
    UNION ALL SELECT '항공시스템공학과'
    UNION ALL SELECT '나노신소재공학과'
    UNION ALL SELECT '양자원자력공학과'
    UNION ALL SELECT '국방AI융합시스템공학과'
) seed
WHERE c.name = '공과대학'
  AND NOT EXISTS (
      SELECT 1 FROM department d
      WHERE d.college_id = c.id AND d.name = seed.name
  );

-- 예체능대학
INSERT INTO department (college_id, name)
SELECT c.id, seed.name
FROM college c
CROSS JOIN (
    SELECT '회화과' AS name
    UNION ALL SELECT '패션디자인학과'
    UNION ALL SELECT '음악과'
    UNION ALL SELECT '체육학과'
    UNION ALL SELECT '무용과'
    UNION ALL SELECT '영화예술학과'
) seed
WHERE c.name = '예체능대학'
  AND NOT EXISTS (
      SELECT 1 FROM department d
      WHERE d.college_id = c.id AND d.name = seed.name
  );

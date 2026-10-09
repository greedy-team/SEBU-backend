-- Verified on 2026-10-09 against the Sejong Physics and Astronomy directories
-- and each linked page: 13 official faculty homepages and the user-supplied
-- RnDCircle profile for Kim Kyungho. These are faculty/lab information links;
-- not every official faculty homepage is a standalone laboratory website.
-- Match identity rather than environment-specific IDs. Fill only missing
-- URLs so existing or concurrently registered websites remain untouched.
-- MANUAL provenance preserves these verified links during later crawls.

-- 김용선 (yongsun@sejong.ac.kr)
UPDATE laboratory
SET website_url = 'https://prof.sejong.ac.kr/yongsun/index.do',
    website_url_source = 'MANUAL',
    updated_at = CURRENT_TIMESTAMP
WHERE professor_id IN (
    SELECT id
    FROM professor
    WHERE name = '김용선'
      AND email = 'yongsun@sejong.ac.kr'
)
AND department_id IN (
    SELECT d.id
    FROM department d
    JOIN college c ON c.id = d.college_id
    WHERE d.name = '물리천문학과'
      AND c.name = '자연과학대학'
)
AND name = '김용선 교수님 연구실'
AND deleted_at IS NULL
AND website_url IS NULL;

-- 김광희 (gkim@sejong.ac.kr)
UPDATE laboratory
SET website_url = 'https://prof.sejong.ac.kr/gkim/index.do',
    website_url_source = 'MANUAL',
    updated_at = CURRENT_TIMESTAMP
WHERE professor_id IN (
    SELECT id
    FROM professor
    WHERE name = '김광희'
      AND email = 'gkim@sejong.ac.kr'
)
AND department_id IN (
    SELECT d.id
    FROM department d
    JOIN college c ON c.id = d.college_id
    WHERE d.name = '물리천문학과'
      AND c.name = '자연과학대학'
)
AND name = '김광희 교수님 연구실'
AND deleted_at IS NULL
AND website_url IS NULL;

-- 김세용 (skim@sejong.ac.kr)
UPDATE laboratory
SET website_url = 'https://prof.sejong.ac.kr/skim/index.do',
    website_url_source = 'MANUAL',
    updated_at = CURRENT_TIMESTAMP
WHERE professor_id IN (
    SELECT id
    FROM professor
    WHERE name = '김세용'
      AND email = 'skim@sejong.ac.kr'
)
AND department_id IN (
    SELECT d.id
    FROM department d
    JOIN college c ON c.id = d.college_id
    WHERE d.name = '물리천문학과'
      AND c.name = '자연과학대학'
)
AND name = '김세용 교수님 연구실'
AND deleted_at IS NULL
AND website_url IS NULL;

-- 정형채 (hcj@sejong.ac.kr)
UPDATE laboratory
SET website_url = 'https://prof.sejong.ac.kr/hcj/index.do',
    website_url_source = 'MANUAL',
    updated_at = CURRENT_TIMESTAMP
WHERE professor_id IN (
    SELECT id
    FROM professor
    WHERE name = '정형채'
      AND email = 'hcj@sejong.ac.kr'
)
AND department_id IN (
    SELECT d.id
    FROM department d
    JOIN college c ON c.id = d.college_id
    WHERE d.name = '물리천문학과'
      AND c.name = '자연과학대학'
)
AND name = '정형채 교수님 연구실'
AND deleted_at IS NULL
AND website_url IS NULL;

-- 홍석륜 (hong@sejong.ac.kr)
UPDATE laboratory
SET website_url = 'https://prof.sejong.ac.kr/hong/index.do',
    website_url_source = 'MANUAL',
    updated_at = CURRENT_TIMESTAMP
WHERE professor_id IN (
    SELECT id
    FROM professor
    WHERE name = '홍석륜'
      AND email = 'hong@sejong.ac.kr'
)
AND department_id IN (
    SELECT d.id
    FROM department d
    JOIN college c ON c.id = d.college_id
    WHERE d.name = '물리천문학과'
      AND c.name = '자연과학대학'
)
AND name = '홍석륜 교수님 연구실'
AND deleted_at IS NULL
AND website_url IS NULL;

-- 천승현 (schun@sejong.ac.kr)
UPDATE laboratory
SET website_url = 'https://prof.sejong.ac.kr/schun/index.do',
    website_url_source = 'MANUAL',
    updated_at = CURRENT_TIMESTAMP
WHERE professor_id IN (
    SELECT id
    FROM professor
    WHERE name = '천승현'
      AND email = 'schun@sejong.ac.kr'
)
AND department_id IN (
    SELECT d.id
    FROM department d
    JOIN college c ON c.id = d.college_id
    WHERE d.name = '물리천문학과'
      AND c.name = '자연과학대학'
)
AND name = '천승현 교수님 연구실'
AND deleted_at IS NULL
AND website_url IS NULL;

-- 채규현 (chae@sejong.ac.kr)
UPDATE laboratory
SET website_url = 'https://prof.sejong.ac.kr/chae/index.do',
    website_url_source = 'MANUAL',
    updated_at = CURRENT_TIMESTAMP
WHERE professor_id IN (
    SELECT id
    FROM professor
    WHERE name = '채규현'
      AND email = 'chae@sejong.ac.kr'
)
AND department_id IN (
    SELECT d.id
    FROM department d
    JOIN college c ON c.id = d.college_id
    WHERE d.name = '물리천문학과'
      AND c.name = '자연과학대학'
)
AND name = '채규현 교수님 연구실'
AND deleted_at IS NULL
AND website_url IS NULL;

-- 이남경 (lee@sejong.ac.kr)
UPDATE laboratory
SET website_url = 'https://prof.sejong.ac.kr/lee/index.do',
    website_url_source = 'MANUAL',
    updated_at = CURRENT_TIMESTAMP
WHERE professor_id IN (
    SELECT id
    FROM professor
    WHERE name = '이남경'
      AND email = 'lee@sejong.ac.kr'
)
AND department_id IN (
    SELECT d.id
    FROM department d
    JOIN college c ON c.id = d.college_id
    WHERE d.name = '물리천문학과'
      AND c.name = '자연과학대학'
)
AND name = '이남경 교수님 연구실'
AND deleted_at IS NULL
AND website_url IS NULL;

-- 최희진 (hjchoi@sejong.ac.kr)
UPDATE laboratory
SET website_url = 'https://prof.sejong.ac.kr/hjchoi/index.do',
    website_url_source = 'MANUAL',
    updated_at = CURRENT_TIMESTAMP
WHERE professor_id IN (
    SELECT id
    FROM professor
    WHERE name = '최희진'
      AND email = 'hjchoi@sejong.ac.kr'
)
AND department_id IN (
    SELECT d.id
    FROM department d
    JOIN college c ON c.id = d.college_id
    WHERE d.name = '물리천문학과'
      AND c.name = '자연과학대학'
)
AND name = '최희진 교수님 연구실'
AND deleted_at IS NULL
AND website_url IS NULL;

-- 김근수 (kskim2676@sejong.ac.kr)
UPDATE laboratory
SET website_url = 'https://prof.sejong.ac.kr/kskim2676/index.do',
    website_url_source = 'MANUAL',
    updated_at = CURRENT_TIMESTAMP
WHERE professor_id IN (
    SELECT id
    FROM professor
    WHERE name = '김근수'
      AND email = 'kskim2676@sejong.ac.kr'
)
AND department_id IN (
    SELECT d.id
    FROM department d
    JOIN college c ON c.id = d.college_id
    WHERE d.name = '물리천문학과'
      AND c.name = '자연과학대학'
)
AND name = '김근수 교수님 연구실'
AND deleted_at IS NULL
AND website_url IS NULL;

-- 서순애 (sunaeseo@sejong.ac.kr)
UPDATE laboratory
SET website_url = 'https://prof.sejong.ac.kr/sunaeseo/index.do',
    website_url_source = 'MANUAL',
    updated_at = CURRENT_TIMESTAMP
WHERE professor_id IN (
    SELECT id
    FROM professor
    WHERE name = '서순애'
      AND email = 'sunaeseo@sejong.ac.kr'
)
AND department_id IN (
    SELECT d.id
    FROM department d
    JOIN college c ON c.id = d.college_id
    WHERE d.name = '물리천문학과'
      AND c.name = '자연과학대학'
)
AND name = '서순애 교수님 연구실'
AND deleted_at IS NULL
AND website_url IS NULL;

-- Maurice VanPutten (mvp@sejong.ac.kr)
UPDATE laboratory
SET website_url = 'https://prof.sejong.ac.kr/mvp/index.do',
    website_url_source = 'MANUAL',
    updated_at = CURRENT_TIMESTAMP
WHERE professor_id IN (
    SELECT id
    FROM professor
    WHERE name = 'Maurice VanPutten'
      AND email = 'mvp@sejong.ac.kr'
)
AND department_id IN (
    SELECT d.id
    FROM department d
    JOIN college c ON c.id = d.college_id
    WHERE d.name = '물리천문학과'
      AND c.name = '자연과학대학'
)
AND name = 'Maurice VanPutten 교수님 연구실'
AND deleted_at IS NULL
AND website_url IS NULL;

-- 오새한슬 (saehanseul.oh@sejong.ac.kr)
UPDATE laboratory
SET website_url = 'https://prof.sejong.ac.kr/saehanseul_oh/index.do',
    website_url_source = 'MANUAL',
    updated_at = CURRENT_TIMESTAMP
WHERE professor_id IN (
    SELECT id
    FROM professor
    WHERE name = '오새한슬'
      AND email = 'saehanseul.oh@sejong.ac.kr'
)
AND department_id IN (
    SELECT d.id
    FROM department d
    JOIN college c ON c.id = d.college_id
    WHERE d.name = '물리천문학과'
      AND c.name = '자연과학대학'
)
AND name = '오새한슬 교수님 연구실'
AND deleted_at IS NULL
AND website_url IS NULL;

-- 김경호 (kyungho@sejong.ac.kr)
UPDATE laboratory
SET website_url = 'https://app.rndcircle.io/lab/e514af30-b319-405d-a5cd-7ac0d197a25e',
    website_url_source = 'MANUAL',
    updated_at = CURRENT_TIMESTAMP
WHERE professor_id IN (
    SELECT id
    FROM professor
    WHERE name = '김경호'
      AND email = 'kyungho@sejong.ac.kr'
)
AND department_id IN (
    SELECT d.id
    FROM department d
    JOIN college c ON c.id = d.college_id
    WHERE d.name = '물리천문학과'
      AND c.name = '자연과학대학'
)
AND name = '김경호 교수님 연구실'
AND deleted_at IS NULL
AND website_url IS NULL;

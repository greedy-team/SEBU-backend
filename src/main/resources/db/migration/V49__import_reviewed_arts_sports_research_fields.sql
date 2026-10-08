-- Reviewed Arts and Physical Education research fields, captured 2026-10-06.
-- 15 laboratories / 55 distinct fields / 61 lab links / 69 category mappings.
-- 3 laboratories publish no research introduction; no fields or links are invented for them.
-- Relative to V48, this migration adds 54 fields and 68 category mappings; existing canonical names are reused.
-- 2 root categories are appended after the current maximum display order, including V47's child categories.
-- Staging ordinals are not local database IDs. Resolve professors, active labs, fields and categories by natural identity.
-- Existing profiles, descriptions, manual URLs, categories, mappings and candidate audit rows remain unchanged.
-- MySQL DDL is not transactional. Guards precede additive, replay-safe canonical writes; inspect failures before repair.

DROP TABLE IF EXISTS v49_field_guard;
DROP TABLE IF EXISTS v49_field_category;
DROP TABLE IF EXISTS v49_lab_field;
DROP TABLE IF EXISTS v49_reviewed_field;
DROP TABLE IF EXISTS v49_reviewed_lab;
DROP TABLE IF EXISTS v49_new_category;

CREATE TABLE v49_new_category (
    category_order INT NOT NULL,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500) NOT NULL,
    CONSTRAINT pk_v49_new_category PRIMARY KEY (code),
    CONSTRAINT uk_v49_new_category_name UNIQUE (name)
);
CREATE TABLE v49_reviewed_lab (
    record_key INT NOT NULL,
    college_name VARCHAR(100) NOT NULL,
    department_name VARCHAR(100) NOT NULL,
    professor_name VARCHAR(100) NOT NULL,
    email VARCHAR(255) NULL,
    professor_id BIGINT NULL,
    laboratory_id BIGINT NULL,
    CONSTRAINT pk_v49_reviewed_lab PRIMARY KEY (record_key)
);
CREATE TABLE v49_reviewed_field (
    field_key INT NOT NULL,
    name VARCHAR(100) NOT NULL,
    research_field_id BIGINT NULL,
    CONSTRAINT pk_v49_reviewed_field PRIMARY KEY (field_key),
    CONSTRAINT uk_v49_reviewed_field_name UNIQUE (name)
);
CREATE TABLE v49_lab_field (
    record_key INT NOT NULL,
    field_key INT NOT NULL,
    CONSTRAINT pk_v49_lab_field PRIMARY KEY (record_key, field_key)
);
CREATE TABLE v49_field_category (
    field_key INT NOT NULL,
    category_code VARCHAR(50) NOT NULL,
    CONSTRAINT pk_v49_field_category PRIMARY KEY (field_key, category_code)
);
CREATE TABLE v49_field_guard (
    reason VARCHAR(100) NOT NULL,
    violations INT NOT NULL,
    CONSTRAINT ck_v49_field_no_conflicts CHECK (violations = 0)
);

INSERT INTO v49_new_category (category_order, code, name, description) VALUES
    (1, 'MUSIC_PERFORMING_ARTS', '음악·공연예술', '기악·성악·실내악, 지휘·음악문헌, 오페라, 무용·연기 및 공연예술 교육'),
    (2, 'SPORTS_PHYSICAL_EDUCATION', '체육·스포츠', '체육학, 체육원리, 스포츠철학과 신체문화 및 체육교육');

INSERT INTO v49_reviewed_lab (record_key, college_name, department_name, professor_name, email) VALUES
    (1, '예체능대학', '회화과', '정재호', 'runner23@sejong.ac.kr'),
    (2, '예체능대학', '패션디자인학과', '정재윤', 'jychung@sejong.ac.kr'),
    (3, '예체능대학', '패션디자인학과', '김숙진', 'ksjina@sejong.ac.kr'),
    (4, '예체능대학', '패션디자인학과', '한혜리', 'hrhan@sejong.ac.kr'),
    (5, '예체능대학', '음악과', '이기정', 'kclee@sejong.ac.kr'),
    (6, '예체능대학', '음악과', '윤경희', 'yoonkh@sejong.ac.kr'),
    (7, '예체능대학', '음악과', '오은경', 'ekoh@sejong.ac.kr'),
    (8, '예체능대학', '음악과', '김나영', 'naykim@sejong.ac.kr'),
    (9, '예체능대학', '음악과', '위정민', 'weejeongmin@sejong.ac.kr'),
    (10, '예체능대학', '체육학과', '김병민', 'kimbm@sejong.ac.kr'),
    (11, '예체능대학', '체육학과', '강유원', 'ywkang@sejong.ac.kr'),
    (12, '예체능대학', '무용과', '김형남', 'hyoungnam@sejong.ac.kr'),
    (13, '예체능대학', '영화예술학과', '라경민', 'actingart@sejong.ac.kr'),
    (14, '예체능대학', '영화예술학과', '박철', 'cpyun@sejong.ac.kr'),
    (15, '예체능대학', '영화예술학과', '최두영', 'filmdoo@sejong.ac.kr');

INSERT INTO v49_reviewed_field (field_key, name) VALUES
    (1, '한국화'),
    (2, '퓨처텍스타일디자인'),
    (3, '소재정보디자인'),
    (4, '예술/디자인사'),
    (5, '패션디자인'),
    (6, '입체재단'),
    (7, '디지털 패션'),
    (8, '메타버스 디자인'),
    (9, '인공지능과 패션 디자인'),
    (10, '조형예술학'),
    (11, '의류학'),
    (12, '인공지능 패션 디자인'),
    (13, '첨단 패션'),
    (14, 'piano performance'),
    (15, 'piano literature'),
    (16, 'collaborative piano'),
    (17, '바이올린'),
    (18, '실내악'),
    (19, '오케스트라 지휘'),
    (20, '합창 지휘'),
    (21, '관현악 문헌'),
    (22, '오페라'),
    (23, '오라토리오'),
    (24, '예술가곡'),
    (25, '성악문헌'),
    (26, '성악 레퍼토리'),
    (27, '영어딕션'),
    (28, '피아노 전공실기'),
    (29, '피아노 문헌'),
    (30, '피아노 교수법'),
    (31, '영미 가곡'),
    (32, '성악 페다고지'),
    (33, '오페라 연기'),
    (34, '체육학'),
    (35, '체육철학'),
    (36, '스포츠철학'),
    (37, '체육원리'),
    (38, '현상학'),
    (39, '신체문화'),
    (40, '미학'),
    (41, '가치론'),
    (42, '질적연구'),
    (43, '무용교육'),
    (44, '예술교육'),
    (45, '융합예술콘텐츠'),
    (46, '연기예술'),
    (47, '연기교육'),
    (48, '배우훈련'),
    (49, '뉴미디어 공연/영상콘텐츠'),
    (50, '영화연출'),
    (51, '제작'),
    (52, '시나리오'),
    (53, '영화'),
    (54, '드라마 기획'),
    (55, '영화 미학 연구');

INSERT INTO v49_lab_field (record_key, field_key) VALUES
    (1, 1),
    (2, 2),
    (2, 3),
    (2, 4),
    (3, 5),
    (3, 6),
    (3, 7),
    (3, 8),
    (3, 9),
    (3, 10),
    (4, 11),
    (4, 12),
    (4, 13),
    (5, 14),
    (5, 15),
    (5, 16),
    (6, 17),
    (6, 18),
    (6, 19),
    (6, 20),
    (6, 21),
    (7, 22),
    (7, 23),
    (7, 24),
    (7, 25),
    (7, 26),
    (7, 27),
    (8, 28),
    (8, 29),
    (8, 30),
    (8, 18),
    (9, 22),
    (9, 25),
    (9, 31),
    (9, 32),
    (9, 27),
    (9, 33),
    (10, 34),
    (11, 35),
    (11, 36),
    (11, 37),
    (11, 38),
    (11, 39),
    (11, 40),
    (11, 41),
    (11, 42),
    (12, 43),
    (12, 44),
    (12, 45),
    (13, 46),
    (13, 47),
    (13, 48),
    (13, 49),
    (14, 50),
    (14, 51),
    (14, 52),
    (15, 53),
    (15, 54),
    (15, 52),
    (15, 51),
    (15, 55);

INSERT INTO v49_field_category (field_key, category_code) VALUES
    (1, 'DESIGN_ARTS'),
    (2, 'DESIGN_ARTS'),
    (3, 'DESIGN_ARTS'),
    (4, 'DESIGN_ARTS'),
    (4, 'HISTORY_CULTURE'),
    (5, 'DESIGN_ARTS'),
    (6, 'DESIGN_ARTS'),
    (7, 'DESIGN_ARTS'),
    (8, 'DESIGN_ARTS'),
    (8, 'HCI_XR'),
    (9, 'AI_ML'),
    (9, 'DESIGN_ARTS'),
    (10, 'DESIGN_ARTS'),
    (11, 'DESIGN_ARTS'),
    (12, 'AI_ML'),
    (12, 'DESIGN_ARTS'),
    (13, 'DESIGN_ARTS'),
    (14, 'MUSIC_PERFORMING_ARTS'),
    (15, 'MUSIC_PERFORMING_ARTS'),
    (16, 'MUSIC_PERFORMING_ARTS'),
    (17, 'MUSIC_PERFORMING_ARTS'),
    (18, 'MUSIC_PERFORMING_ARTS'),
    (19, 'MUSIC_PERFORMING_ARTS'),
    (20, 'MUSIC_PERFORMING_ARTS'),
    (21, 'MUSIC_PERFORMING_ARTS'),
    (22, 'MUSIC_PERFORMING_ARTS'),
    (23, 'MUSIC_PERFORMING_ARTS'),
    (24, 'MUSIC_PERFORMING_ARTS'),
    (25, 'MUSIC_PERFORMING_ARTS'),
    (26, 'MUSIC_PERFORMING_ARTS'),
    (27, 'MUSIC_PERFORMING_ARTS'),
    (28, 'MUSIC_PERFORMING_ARTS'),
    (29, 'MUSIC_PERFORMING_ARTS'),
    (30, 'MUSIC_PERFORMING_ARTS'),
    (30, 'POLICY_MANAGEMENT'),
    (31, 'MUSIC_PERFORMING_ARTS'),
    (32, 'MUSIC_PERFORMING_ARTS'),
    (32, 'POLICY_MANAGEMENT'),
    (33, 'MUSIC_PERFORMING_ARTS'),
    (34, 'SPORTS_PHYSICAL_EDUCATION'),
    (35, 'HISTORY_CULTURE'),
    (35, 'SPORTS_PHYSICAL_EDUCATION'),
    (36, 'HISTORY_CULTURE'),
    (36, 'SPORTS_PHYSICAL_EDUCATION'),
    (37, 'SPORTS_PHYSICAL_EDUCATION'),
    (38, 'HISTORY_CULTURE'),
    (39, 'HISTORY_CULTURE'),
    (39, 'SPORTS_PHYSICAL_EDUCATION'),
    (40, 'HISTORY_CULTURE'),
    (41, 'HISTORY_CULTURE'),
    (42, 'HISTORY_CULTURE'),
    (43, 'MUSIC_PERFORMING_ARTS'),
    (43, 'POLICY_MANAGEMENT'),
    (44, 'DESIGN_ARTS'),
    (44, 'POLICY_MANAGEMENT'),
    (45, 'DESIGN_ARTS'),
    (46, 'MUSIC_PERFORMING_ARTS'),
    (47, 'MUSIC_PERFORMING_ARTS'),
    (47, 'POLICY_MANAGEMENT'),
    (48, 'MUSIC_PERFORMING_ARTS'),
    (49, 'DESIGN_ARTS'),
    (49, 'MUSIC_PERFORMING_ARTS'),
    (50, 'DESIGN_ARTS'),
    (51, 'DESIGN_ARTS'),
    (52, 'DESIGN_ARTS'),
    (53, 'DESIGN_ARTS'),
    (54, 'DESIGN_ARTS'),
    (55, 'DESIGN_ARTS'),
    (55, 'HISTORY_CULTURE');

-- Stop on missing/ambiguous identities, including NULL-email professors and soft-deleted labs.
INSERT INTO v49_field_guard
SELECT 'professor identity missing or ambiguous', COUNT(*) FROM v49_reviewed_lab s
WHERE (SELECT COUNT(*) FROM professor p
    JOIN department d ON d.id=p.department_id JOIN college c ON c.id=d.college_id
    WHERE (s.email IS NOT NULL AND p.email=s.email)
       OR (s.email IS NULL AND p.email IS NULL AND p.name=s.professor_name
           AND d.name=s.department_name AND c.name=s.college_name)) <> 1;

UPDATE v49_reviewed_lab SET professor_id = (
    SELECT p.id FROM professor p
    JOIN department d ON d.id=p.department_id JOIN college c ON c.id=d.college_id
    WHERE (v49_reviewed_lab.email IS NOT NULL AND p.email=v49_reviewed_lab.email)
       OR (v49_reviewed_lab.email IS NULL AND p.email IS NULL
           AND p.name=v49_reviewed_lab.professor_name
           AND d.name=v49_reviewed_lab.department_name AND c.name=v49_reviewed_lab.college_name)
);

INSERT INTO v49_field_guard
SELECT 'professor email belongs to a different name', COUNT(*)
FROM v49_reviewed_lab s JOIN professor p ON p.id=s.professor_id
WHERE CAST(p.name AS BINARY(400)) <> CAST(s.professor_name AS BINARY(400));

INSERT INTO v49_field_guard
SELECT 'active laboratory missing or ambiguous', COUNT(*) FROM v49_reviewed_lab s
WHERE (SELECT COUNT(*) FROM laboratory l WHERE l.professor_id=s.professor_id AND l.deleted_at IS NULL) <> 1;

UPDATE v49_reviewed_lab SET laboratory_id = (
    SELECT l.id FROM laboratory l WHERE l.professor_id=v49_reviewed_lab.professor_id AND l.deleted_at IS NULL
);

INSERT INTO v49_field_guard
SELECT 'category code has another name', COUNT(*)
FROM v49_new_category s JOIN research_field_category c ON c.code=s.code
WHERE CAST(c.name AS BINARY(400)) <> CAST(s.name AS BINARY(400));

INSERT INTO v49_field_guard
SELECT 'root category unexpectedly has a parent', COUNT(*)
FROM v49_new_category s JOIN research_field_category c ON c.code=s.code
WHERE c.parent_id IS NOT NULL;

INSERT INTO v49_field_guard
SELECT 'category name has another code', COUNT(*)
FROM v49_new_category s JOIN research_field_category c ON c.name=s.name WHERE c.code<>s.code;

INSERT INTO v49_field_guard
SELECT 'referenced category is missing', COUNT(*) FROM v49_field_category m
WHERE NOT EXISTS (SELECT 1 FROM research_field_category c WHERE c.code=m.category_code)
  AND NOT EXISTS (SELECT 1 FROM v49_new_category c WHERE c.code=m.category_code);

INSERT INTO v49_field_guard
SELECT 'canonical field name is ambiguous', COUNT(*) FROM v49_reviewed_field s
WHERE (SELECT COUNT(*) FROM research_field f WHERE LOWER(f.name)=LOWER(s.name))>1;

INSERT INTO v49_field_guard
SELECT 'staged link has no source', COUNT(*) FROM v49_lab_field m
WHERE NOT EXISTS (SELECT 1 FROM v49_reviewed_lab l WHERE l.record_key=m.record_key)
   OR NOT EXISTS (SELECT 1 FROM v49_reviewed_field f WHERE f.field_key=m.field_key);

INSERT INTO v49_field_guard
SELECT 'staged category mapping has no field', COUNT(*) FROM v49_field_category m
WHERE NOT EXISTS (SELECT 1 FROM v49_reviewed_field f WHERE f.field_key=m.field_key);

INSERT INTO v49_field_guard
SELECT 'staged field has no category', COUNT(*) FROM v49_reviewed_field f
WHERE NOT EXISTS (SELECT 1 FROM v49_field_category m WHERE m.field_key=f.field_key);

-- Only additive canonical writes below. Existing names, IDs, descriptions and mappings survive.
INSERT INTO research_field_category (code, name, description, display_order, parent_id)
SELECT s.code, s.name, s.description, existing.maximum_order+s.category_order, NULL
FROM v49_new_category s
CROSS JOIN (SELECT COALESCE(MAX(display_order),0) AS maximum_order FROM research_field_category) existing
WHERE NOT EXISTS (SELECT 1 FROM research_field_category c WHERE c.code=s.code);

INSERT INTO research_field (name)
SELECT s.name FROM v49_reviewed_field s
WHERE NOT EXISTS (SELECT 1 FROM research_field f WHERE LOWER(f.name)=LOWER(s.name));

UPDATE v49_reviewed_field SET research_field_id = (
    SELECT f.id FROM research_field f WHERE LOWER(f.name)=LOWER(v49_reviewed_field.name)
);

INSERT INTO laboratory_research_field (laboratory_id, research_field_id)
SELECT l.laboratory_id, f.research_field_id
FROM v49_lab_field m JOIN v49_reviewed_lab l ON l.record_key=m.record_key
JOIN v49_reviewed_field f ON f.field_key=m.field_key
WHERE NOT EXISTS (SELECT 1 FROM laboratory_research_field existing
    WHERE existing.laboratory_id=l.laboratory_id AND existing.research_field_id=f.research_field_id);

INSERT INTO research_field_category_mapping (research_field_id, category_id)
SELECT f.research_field_id, c.id FROM v49_field_category m
JOIN v49_reviewed_field f ON f.field_key=m.field_key
JOIN research_field_category c ON c.code=m.category_code
WHERE NOT EXISTS (SELECT 1 FROM research_field_category_mapping existing
    WHERE existing.research_field_id=f.research_field_id AND existing.category_id=c.id);

INSERT INTO v49_field_guard
SELECT 'unresolved field or missing category mapping', COUNT(*) FROM v49_reviewed_field f
WHERE f.research_field_id IS NULL OR NOT EXISTS (
    SELECT 1 FROM research_field_category_mapping m WHERE m.research_field_id=f.research_field_id
);

INSERT INTO v49_field_guard
SELECT 'missing expected laboratory field link', COUNT(*)
FROM v49_lab_field m JOIN v49_reviewed_lab l ON l.record_key=m.record_key
JOIN v49_reviewed_field f ON f.field_key=m.field_key
WHERE NOT EXISTS (SELECT 1 FROM laboratory_research_field existing
    WHERE existing.laboratory_id=l.laboratory_id AND existing.research_field_id=f.research_field_id);

INSERT INTO v49_field_guard
SELECT 'missing expected category mapping', COUNT(*) FROM v49_field_category m
JOIN v49_reviewed_field f ON f.field_key=m.field_key
JOIN research_field_category c ON c.code=m.category_code
WHERE NOT EXISTS (SELECT 1 FROM research_field_category_mapping existing
    WHERE existing.research_field_id=f.research_field_id AND existing.category_id=c.id);

DROP TABLE v49_field_guard;
DROP TABLE v49_field_category;
DROP TABLE v49_lab_field;
DROP TABLE v49_reviewed_field;
DROP TABLE v49_reviewed_lab;
DROP TABLE v49_new_category;

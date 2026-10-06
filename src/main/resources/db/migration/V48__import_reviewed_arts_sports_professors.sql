-- Reviewed public Arts and Physical Education faculty catalogue, captured 2026-10-06.
-- 6 sources / 18 department memberships / 18 distinct professors and labs.
-- Deployment data, not a local/test seed. No accounts, credentials or candidate review history.
-- record_key is an internal staging ordinal, never a local database primary key.
-- Resolve IDs by email (or NULL-email + college/department/name). Add only missing records
-- and affiliations; preserve all existing profiles, manual URLs, official names and recruitment state.
-- Faculty pages do not publish laboratory names: generated names are explicitly marked GENERATED.
-- MySQL DDL is not transactional. Inspect conflicts and partial writes before retry/repair.

DROP TABLE IF EXISTS v48_catalog_guard;
DROP TABLE IF EXISTS v48_catalog_affiliation;
DROP TABLE IF EXISTS v48_catalog_professor;
DROP TABLE IF EXISTS v48_catalog_source;

CREATE TABLE v48_catalog_source (
    college_name VARCHAR(100) NOT NULL,
    department_name VARCHAR(100) NOT NULL,
    source_name VARCHAR(150) NOT NULL,
    source_url VARCHAR(512) NOT NULL PRIMARY KEY,
    parser_type VARCHAR(50) NOT NULL,
    department_id BIGINT NULL
);
CREATE TABLE v48_catalog_professor (
    record_key INT NOT NULL PRIMARY KEY,
    college_name VARCHAR(100) NOT NULL,
    department_name VARCHAR(100) NOT NULL,
    professor_name VARCHAR(100) NOT NULL,
    email VARCHAR(255) NULL,
    position VARCHAR(100) NULL,
    laboratory_name VARCHAR(150) NOT NULL,
    name_source VARCHAR(20) NOT NULL,
    website_url VARCHAR(2048) NULL,
    description VARCHAR(2000) NULL,
    department_id BIGINT NULL,
    professor_id BIGINT NULL,
    laboratory_id BIGINT NULL
);
CREATE TABLE v48_catalog_affiliation (
    record_key INT NOT NULL,
    college_name VARCHAR(100) NOT NULL,
    department_name VARCHAR(100) NOT NULL,
    position VARCHAR(100) NULL,
    department_id BIGINT NULL,
    PRIMARY KEY (record_key, college_name, department_name)
);
CREATE TABLE v48_catalog_guard (
    reason VARCHAR(100) NOT NULL,
    violations INT NOT NULL,
    CONSTRAINT ck_v48_catalog_no_conflicts CHECK (violations = 0)
);

INSERT INTO v48_catalog_source (college_name, department_name, source_name, source_url, parser_type)
VALUES
    ('예체능대학', '회화과', '회화과 교수진', 'https://dept.sejong.ac.kr/picdpt/intro/professor.do', 'SEJONG_STANDARD'),
    ('예체능대학', '패션디자인학과', '패션디자인학과 교수진', 'https://www.sejong.ac.kr/kor/college/fashion-design.do', 'SEJONG_STANDARD'),
    ('예체능대학', '음악과', '음악과 교수진', 'https://dept.sejong.ac.kr/musicdpt/intro/professor.do', 'SEJONG_STANDARD'),
    ('예체능대학', '체육학과', '체육학과 교수진', 'https://www.sejong.ac.kr/kor/college/physical-education.do', 'SEJONG_STANDARD'),
    ('예체능대학', '무용과', '무용과 교수진', 'https://www.sejong.ac.kr/kor/college/dance.do', 'SEJONG_STANDARD'),
    ('예체능대학', '영화예술학과', '영화예술학과 교수진', 'https://www.sejong.ac.kr/kor/college/film-art.do', 'SEJONG_STANDARD');

INSERT INTO v48_catalog_professor (record_key, college_name, department_name, professor_name, email, position, laboratory_name, name_source, website_url, description)
VALUES
    (1, '예체능대학', '회화과', '정재호', 'runner23@sejong.ac.kr', '부교수', '정재호 교수님 연구실', 'GENERATED', NULL, '한국화'),
    (2, '예체능대학', '패션디자인학과', '정재윤', 'jychung@sejong.ac.kr', '교수', '정재윤 교수님 연구실', 'GENERATED', NULL, '퓨처텍스타일디자인, 소재정보디자인, 예술/디자인사'),
    (3, '예체능대학', '패션디자인학과', '김숙진', 'ksjina@sejong.ac.kr', '교수', '김숙진 교수님 연구실', 'GENERATED', 'https://prof.sejong.ac.kr/ksjina/', '패션디자인, 입체재단, 디지털 패션, 메타버스 디자인, 인공지능과 패션 디자인, 조형예술학'),
    (4, '예체능대학', '패션디자인학과', '한혜리', 'hrhan@sejong.ac.kr', '조교수', '한혜리 교수님 연구실', 'GENERATED', 'https://prof.sejong.ac.kr/hrhan/index.do', '의류학, 인공지능 패션 디자인, 첨단 패션 등'),
    (5, '예체능대학', '음악과', '이기정', 'kclee@sejong.ac.kr', '교수', '이기정 교수님 연구실', 'GENERATED', NULL, 'piano performance piano literature collaborative piano'),
    (6, '예체능대학', '음악과', '윤경희', 'yoonkh@sejong.ac.kr', '교수', '윤경희 교수님 연구실', 'GENERATED', NULL, '바이올린, 실내악, 오케스트라 지휘, 합창 지휘, 관현악 문헌,'),
    (7, '예체능대학', '음악과', '오은경', 'ekoh@sejong.ac.kr', '교수', '오은경 교수님 연구실', 'GENERATED', NULL, '오페라, 오라토리오, 예술가곡, 성악문헌, 성악 레퍼토리, 영어딕션'),
    (8, '예체능대학', '음악과', '김나영', 'naykim@sejong.ac.kr', '교수', '김나영 교수님 연구실', 'GENERATED', 'https://home.sejong.ac.kr/~naykim/1.html', '피아노 전공실기, 피아노 문헌, 피아노 교수법, 실내악'),
    (9, '예체능대학', '음악과', '위정민', 'weejeongmin@sejong.ac.kr', '조교수', '위정민 교수님 연구실', 'GENERATED', NULL, '오페라 성악문헌 영미 가곡 성악 페다고지 영어딕션 오페라 연기'),
    (10, '예체능대학', '체육학과', '김병민', 'kimbm@sejong.ac.kr', '초빙교수', '김병민 교수님 연구실', 'GENERATED', NULL, '체육학'),
    (11, '예체능대학', '체육학과', '강유원', 'ywkang@sejong.ac.kr', '교수', '강유원 교수님 연구실', 'GENERATED', 'https://prof.sejong.ac.kr/ywkang/index.do', '체육철학, 스포츠철학, 체육원리 현상학, 신체문화, 미학, 가치론, 질적연구'),
    (12, '예체능대학', '무용과', '김형남', 'hyoungnam@sejong.ac.kr', '교수', '김형남 교수님 연구실', 'GENERATED', NULL, '무용교육 예술교육 융합예술콘텐츠'),
    (13, '예체능대학', '영화예술학과', '라경민', 'actingart@sejong.ac.kr', '조교수', '라경민 교수님 연구실', 'GENERATED', 'https://prof.sejong.ac.kr/actingart/index.do', '연기예술, 연기교육, 배우훈련, 뉴미디어 공연/영상콘텐츠'),
    (14, '예체능대학', '영화예술학과', '박철', 'cpyun@sejong.ac.kr', '교수', '박철 교수님 연구실', 'GENERATED', NULL, '영화연출, 제작, 시나리오'),
    (15, '예체능대학', '영화예술학과', '최두영', 'filmdoo@sejong.ac.kr', '교수', '최두영 교수님 연구실', 'GENERATED', NULL, '1). 영화, 드라마 기획, 시나리오, 제작 2). 영화 미학 연구'),
    (16, '예체능대학', '영화예술학과', '김기훈', 'actscene@naver.com', '초빙교수', '김기훈 교수님 연구실', 'GENERATED', NULL, NULL),
    (17, '예체능대학', '영화예술학과', '이은경', 'zungbu@daum.net', '초빙교수', '이은경 교수님 연구실', 'GENERATED', NULL, NULL),
    (18, '예체능대학', '영화예술학과', '안연석', 'ysahn@sejong.ac.kr', '특임교수', '안연석 교수님 연구실', 'GENERATED', NULL, NULL);

INSERT INTO v48_catalog_affiliation (record_key, college_name, department_name, position)
VALUES
    (1, '예체능대학', '회화과', '부교수'),
    (2, '예체능대학', '패션디자인학과', '교수'),
    (3, '예체능대학', '패션디자인학과', '교수'),
    (4, '예체능대학', '패션디자인학과', '조교수'),
    (5, '예체능대학', '음악과', '교수'),
    (6, '예체능대학', '음악과', '교수'),
    (7, '예체능대학', '음악과', '교수'),
    (8, '예체능대학', '음악과', '교수'),
    (9, '예체능대학', '음악과', '조교수'),
    (10, '예체능대학', '체육학과', '초빙교수'),
    (11, '예체능대학', '체육학과', '교수'),
    (12, '예체능대학', '무용과', '교수'),
    (13, '예체능대학', '영화예술학과', '조교수'),
    (14, '예체능대학', '영화예술학과', '교수'),
    (15, '예체능대학', '영화예술학과', '교수'),
    (16, '예체능대학', '영화예술학과', '초빙교수'),
    (17, '예체능대학', '영화예술학과', '초빙교수'),
    (18, '예체능대학', '영화예술학과', '특임교수');

-- V42 already registers these six departments. Do not silently create a misspelled affiliation.
INSERT INTO v48_catalog_guard
SELECT 'missing or ambiguous academic department', COUNT(*)
FROM v48_catalog_source s
WHERE (SELECT COUNT(*) FROM department d JOIN college c ON c.id = d.college_id
       WHERE c.name = s.college_name AND d.name = s.department_name) <> 1;

UPDATE v48_catalog_source SET department_id = (
    SELECT d.id FROM department d JOIN college c ON c.id = d.college_id
    WHERE c.name = v48_catalog_source.college_name AND d.name = v48_catalog_source.department_name
);
UPDATE v48_catalog_professor SET department_id = (
    SELECT d.id FROM department d JOIN college c ON c.id = d.college_id
    WHERE c.name = v48_catalog_professor.college_name AND d.name = v48_catalog_professor.department_name
);
UPDATE v48_catalog_affiliation SET department_id = (
    SELECT d.id FROM department d JOIN college c ON c.id = d.college_id
    WHERE c.name = v48_catalog_affiliation.college_name AND d.name = v48_catalog_affiliation.department_name
);

INSERT INTO v48_catalog_guard
SELECT 'source department/parser mismatch', COUNT(*)
FROM v48_catalog_source s JOIN crawl_source existing ON existing.source_url = s.source_url
WHERE existing.department_id <> s.department_id OR existing.parser_type <> s.parser_type;

INSERT INTO v48_catalog_guard
SELECT 'same email, different professor name', COUNT(*)
FROM v48_catalog_professor s JOIN professor p ON p.email = s.email
WHERE CAST(p.name AS BINARY(400)) <> CAST(s.professor_name AS BINARY(400));

INSERT INTO v48_catalog_guard
SELECT 'same department/name, different or missing email', COUNT(*)
FROM v48_catalog_professor s JOIN professor p
    ON p.department_id = s.department_id AND p.name = s.professor_name
WHERE p.email <> s.email OR (p.email IS NULL AND s.email IS NOT NULL)
    OR (p.email IS NOT NULL AND s.email IS NULL);

INSERT INTO v48_catalog_guard
SELECT 'ambiguous NULL-email professor', COUNT(*) FROM v48_catalog_professor s
WHERE s.email IS NULL AND (SELECT COUNT(*) FROM professor p
    WHERE p.email IS NULL AND p.department_id = s.department_id AND p.name = s.professor_name) > 1;

UPDATE v48_catalog_professor SET professor_id = (
    SELECT p.id FROM professor p
    WHERE (v48_catalog_professor.email IS NOT NULL AND p.email = v48_catalog_professor.email)
       OR (v48_catalog_professor.email IS NULL AND p.email IS NULL
           AND p.department_id = v48_catalog_professor.department_id AND p.name = v48_catalog_professor.professor_name)
);

INSERT INTO v48_catalog_guard
SELECT 'ambiguous active laboratory', COUNT(*) FROM v48_catalog_professor s
WHERE (SELECT COUNT(*) FROM laboratory l WHERE l.professor_id = s.professor_id AND l.deleted_at IS NULL) > 1;

INSERT INTO v48_catalog_guard
SELECT 'existing professor has only deleted laboratories; do not resurrect', COUNT(*)
FROM v48_catalog_professor s
WHERE EXISTS (SELECT 1 FROM laboratory l WHERE l.professor_id = s.professor_id)
  AND NOT EXISTS (SELECT 1 FROM laboratory l WHERE l.professor_id = s.professor_id AND l.deleted_at IS NULL);

INSERT INTO v48_catalog_guard
SELECT 'laboratory name belongs to another professor', COUNT(*)
FROM v48_catalog_professor s LEFT JOIN professor p ON p.id = s.professor_id
JOIN laboratory l ON l.department_id = COALESCE(p.department_id, s.department_id)
    AND l.name = s.laboratory_name AND l.deleted_at IS NULL
WHERE s.professor_id IS NULL OR l.professor_id <> s.professor_id;

-- All ambiguity checks precede canonical writes. Existing profiles are never overwritten.
INSERT INTO crawl_source (department_id, source_name, source_url, parser_type, version)
SELECT s.department_id, s.source_name, s.source_url, s.parser_type, 0 FROM v48_catalog_source s
WHERE NOT EXISTS (SELECT 1 FROM crawl_source existing WHERE existing.source_url = s.source_url);

INSERT INTO professor (department_id, name, email, position)
SELECT department_id, professor_name, email, position FROM v48_catalog_professor WHERE professor_id IS NULL;

UPDATE v48_catalog_professor SET professor_id = (
    SELECT p.id FROM professor p
    WHERE (v48_catalog_professor.email IS NOT NULL AND p.email = v48_catalog_professor.email)
       OR (v48_catalog_professor.email IS NULL AND p.email IS NULL
           AND p.department_id = v48_catalog_professor.department_id AND p.name = v48_catalog_professor.professor_name)
);

INSERT INTO laboratory (professor_id, department_id, name, website_url, website_url_source, description, recruitment_status, name_source)
SELECT s.professor_id, p.department_id, s.laboratory_name, s.website_url,
    CASE WHEN s.website_url IS NULL THEN NULL ELSE 'CRAWLED' END, s.description, 'UNKNOWN', s.name_source
FROM v48_catalog_professor s JOIN professor p ON p.id = s.professor_id
WHERE NOT EXISTS (SELECT 1 FROM laboratory l WHERE l.professor_id = s.professor_id AND l.deleted_at IS NULL);

UPDATE v48_catalog_professor SET laboratory_id = (
    SELECT l.id FROM laboratory l WHERE l.professor_id = v48_catalog_professor.professor_id AND l.deleted_at IS NULL
);

INSERT INTO professor_department (professor_id, department_id, position)
SELECT s.professor_id, a.department_id, a.position
FROM v48_catalog_affiliation a JOIN v48_catalog_professor s ON s.record_key = a.record_key
WHERE NOT EXISTS (SELECT 1 FROM professor_department existing
    WHERE existing.professor_id = s.professor_id AND existing.department_id = a.department_id);

INSERT INTO laboratory_department (laboratory_id, department_id)
SELECT s.laboratory_id, a.department_id
FROM v48_catalog_affiliation a JOIN v48_catalog_professor s ON s.record_key = a.record_key
WHERE NOT EXISTS (SELECT 1 FROM laboratory_department existing
    WHERE existing.laboratory_id = s.laboratory_id AND existing.department_id = a.department_id);

INSERT INTO v48_catalog_guard
SELECT 'unresolved professor or laboratory', COUNT(*) FROM v48_catalog_professor
WHERE professor_id IS NULL OR laboratory_id IS NULL;

INSERT INTO v48_catalog_guard
SELECT 'missing professor affiliation', COUNT(*)
FROM v48_catalog_affiliation a JOIN v48_catalog_professor s ON s.record_key = a.record_key
WHERE NOT EXISTS (SELECT 1 FROM professor_department pd
    WHERE pd.professor_id = s.professor_id AND pd.department_id = a.department_id);

INSERT INTO v48_catalog_guard
SELECT 'missing laboratory affiliation', COUNT(*)
FROM v48_catalog_affiliation a JOIN v48_catalog_professor s ON s.record_key = a.record_key
WHERE NOT EXISTS (SELECT 1 FROM laboratory_department ld
    WHERE ld.laboratory_id = s.laboratory_id AND ld.department_id = a.department_id);

DROP TABLE v48_catalog_guard;
DROP TABLE v48_catalog_affiliation;
DROP TABLE v48_catalog_professor;
DROP TABLE v48_catalog_source;

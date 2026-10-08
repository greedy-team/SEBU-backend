INSERT INTO college (name)
SELECT '인공지능융합대학'
WHERE NOT EXISTS (
    SELECT 1 FROM college WHERE name = '인공지능융합대학'
);

INSERT INTO department (college_id, name)
SELECT c.id, '컴퓨터공학과'
FROM college c
WHERE c.name = '인공지능융합대학'
  AND NOT EXISTS (
      SELECT 1 FROM department d
      WHERE d.college_id = c.id AND d.name = '컴퓨터공학과'
  );

INSERT INTO department (college_id, name)
SELECT c.id, 'AI융합전자공학과'
FROM college c
WHERE c.name = '인공지능융합대학'
  AND NOT EXISTS (
      SELECT 1 FROM department d
      WHERE d.college_id = c.id AND d.name = 'AI융합전자공학과'
  );

INSERT INTO department (college_id, name)
SELECT c.id, '정보보호학과'
FROM college c
WHERE c.name = '인공지능융합대학'
  AND NOT EXISTS (
      SELECT 1 FROM department d
      WHERE d.college_id = c.id AND d.name = '정보보호학과'
  );

INSERT INTO department (college_id, name)
SELECT c.id, 'AI로봇학과'
FROM college c
WHERE c.name = '인공지능융합대학'
  AND NOT EXISTS (
      SELECT 1 FROM department d
      WHERE d.college_id = c.id AND d.name = 'AI로봇학과'
  );

INSERT INTO department (college_id, name)
SELECT c.id, '반도체시스템공학과'
FROM college c
WHERE c.name = '인공지능융합대학'
  AND NOT EXISTS (
      SELECT 1 FROM department d
      WHERE d.college_id = c.id AND d.name = '반도체시스템공학과'
  );

INSERT INTO department (college_id, name)
SELECT c.id, '지능IoT학과'
FROM college c
WHERE c.name = '인공지능융합대학'
  AND NOT EXISTS (
      SELECT 1 FROM department d
      WHERE d.college_id = c.id AND d.name = '지능IoT학과'
  );

INSERT INTO department (college_id, name)
SELECT c.id, '사이버국방학과'
FROM college c
WHERE c.name = '인공지능융합대학'
  AND NOT EXISTS (
      SELECT 1 FROM department d
      WHERE d.college_id = c.id AND d.name = '사이버국방학과'
  );

INSERT INTO department (college_id, name)
SELECT c.id, '국방AI로봇융합공학과'
FROM college c
WHERE c.name = '인공지능융합대학'
  AND NOT EXISTS (
      SELECT 1 FROM department d
      WHERE d.college_id = c.id AND d.name = '국방AI로봇융합공학과'
  );

INSERT INTO department (college_id, name)
SELECT c.id, '인공지능데이터사이언스학과'
FROM college c
WHERE c.name = '인공지능융합대학'
  AND NOT EXISTS (
      SELECT 1 FROM department d
      WHERE d.college_id = c.id AND d.name = '인공지능데이터사이언스학과'
  );

INSERT INTO department (college_id, name)
SELECT c.id, '지능정보융합학과'
FROM college c
WHERE c.name = '인공지능융합대학'
  AND NOT EXISTS (
      SELECT 1 FROM department d
      WHERE d.college_id = c.id AND d.name = '지능정보융합학과'
  );

INSERT INTO department (college_id, name)
SELECT c.id, '콘텐츠소프트웨어학과'
FROM college c
WHERE c.name = '인공지능융합대학'
  AND NOT EXISTS (
      SELECT 1 FROM department d
      WHERE d.college_id = c.id AND d.name = '콘텐츠소프트웨어학과'
  );

INSERT INTO department (college_id, name)
SELECT c.id, '양자지능정보학과'
FROM college c
WHERE c.name = '인공지능융합대학'
  AND NOT EXISTS (
      SELECT 1 FROM department d
      WHERE d.college_id = c.id AND d.name = '양자지능정보학과'
  );

INSERT INTO crawl_source (department_id, source_name, source_url, parser_type)
SELECT d.id, '컴퓨터공학과 교수진',
       'https://dept.sejong.ac.kr/cedpt/intro/professor.do', 'SEJONG_STANDARD'
FROM department d
JOIN college c ON c.id = d.college_id
WHERE c.name = '인공지능융합대학'
  AND d.name = '컴퓨터공학과'
  AND NOT EXISTS (
      SELECT 1 FROM crawl_source s
      WHERE s.source_url = 'https://dept.sejong.ac.kr/cedpt/intro/professor.do'
  );

INSERT INTO crawl_source (department_id, source_name, source_url, parser_type)
SELECT d.id, 'AI융합전자공학과 교수진',
       'https://dept.sejong.ac.kr/aixee/intro/professor.do', 'SEJONG_STANDARD'
FROM department d
JOIN college c ON c.id = d.college_id
WHERE c.name = '인공지능융합대학'
  AND d.name = 'AI융합전자공학과'
  AND NOT EXISTS (
      SELECT 1 FROM crawl_source s
      WHERE s.source_url = 'https://dept.sejong.ac.kr/aixee/intro/professor.do'
  );

INSERT INTO crawl_source (department_id, source_name, source_url, parser_type)
SELECT d.id, '정보보호학과 교수진',
       'https://dept.sejong.ac.kr/isdpt/intro/professor.do', 'SEJONG_STANDARD'
FROM department d
JOIN college c ON c.id = d.college_id
WHERE c.name = '인공지능융합대학'
  AND d.name = '정보보호학과'
  AND NOT EXISTS (
      SELECT 1 FROM crawl_source s
      WHERE s.source_url = 'https://dept.sejong.ac.kr/isdpt/intro/professor.do'
  );

INSERT INTO crawl_source (department_id, source_name, source_url, parser_type)
SELECT d.id, 'AI로봇학과 교수진',
       'https://dept.sejong.ac.kr/soime/intro/professor.do', 'SEJONG_STANDARD'
FROM department d
JOIN college c ON c.id = d.college_id
WHERE c.name = '인공지능융합대학'
  AND d.name = 'AI로봇학과'
  AND NOT EXISTS (
      SELECT 1 FROM crawl_source s
      WHERE s.source_url = 'https://dept.sejong.ac.kr/soime/intro/professor.do'
  );

INSERT INTO crawl_source (department_id, source_name, source_url, parser_type)
SELECT d.id, '반도체시스템공학과 교수진',
       'https://dept.sejong.ac.kr/ssedpt/intro/professor.do', 'SEJONG_STANDARD'
FROM department d
JOIN college c ON c.id = d.college_id
WHERE c.name = '인공지능융합대학'
  AND d.name = '반도체시스템공학과'
  AND NOT EXISTS (
      SELECT 1 FROM crawl_source s
      WHERE s.source_url = 'https://dept.sejong.ac.kr/ssedpt/intro/professor.do'
  );

INSERT INTO crawl_source (department_id, source_name, source_url, parser_type)
SELECT d.id, '지능IoT학과 교수진',
       'https://dept.sejong.ac.kr/inteliotdpt/intro/professor.do', 'SEJONG_STANDARD'
FROM department d
JOIN college c ON c.id = d.college_id
WHERE c.name = '인공지능융합대학'
  AND d.name = '지능IoT학과'
  AND NOT EXISTS (
      SELECT 1 FROM crawl_source s
      WHERE s.source_url = 'https://dept.sejong.ac.kr/inteliotdpt/intro/professor.do'
  );

INSERT INTO crawl_source (department_id, source_name, source_url, parser_type)
SELECT d.id, '사이버국방학과 교수진',
       'https://dept.sejong.ac.kr/cddpt/intro/professor.do', 'SEJONG_STANDARD'
FROM department d
JOIN college c ON c.id = d.college_id
WHERE c.name = '인공지능융합대학'
  AND d.name = '사이버국방학과'
  AND NOT EXISTS (
      SELECT 1 FROM crawl_source s
      WHERE s.source_url = 'https://dept.sejong.ac.kr/cddpt/intro/professor.do'
  );

INSERT INTO crawl_source (department_id, source_name, source_url, parser_type)
SELECT d.id, '국방AI로봇융합공학과 교수진',
       'https://dept.sejong.ac.kr/dairedpt/intro/professor.do', 'SEJONG_STANDARD'
FROM department d
JOIN college c ON c.id = d.college_id
WHERE c.name = '인공지능융합대학'
  AND d.name = '국방AI로봇융합공학과'
  AND NOT EXISTS (
      SELECT 1 FROM crawl_source s
      WHERE s.source_url = 'https://dept.sejong.ac.kr/dairedpt/intro/professor.do'
  );

INSERT INTO crawl_source (department_id, source_name, source_url, parser_type)
SELECT d.id, '인공지능데이터사이언스학과 교수진',
       'https://dept.sejong.ac.kr/aidsdpt/intro/professor.do', 'SEJONG_STANDARD'
FROM department d
JOIN college c ON c.id = d.college_id
WHERE c.name = '인공지능융합대학'
  AND d.name = '인공지능데이터사이언스학과'
  AND NOT EXISTS (
      SELECT 1 FROM crawl_source s
      WHERE s.source_url = 'https://dept.sejong.ac.kr/aidsdpt/intro/professor.do'
  );

INSERT INTO crawl_source (department_id, source_name, source_url, parser_type)
SELECT d.id, '지능정보융합학과 교수진',
       'https://dept.sejong.ac.kr/aiitdpt/intro/professor.do', 'SEJONG_STANDARD'
FROM department d
JOIN college c ON c.id = d.college_id
WHERE c.name = '인공지능융합대학'
  AND d.name = '지능정보융합학과'
  AND NOT EXISTS (
      SELECT 1 FROM crawl_source s
      WHERE s.source_url = 'https://dept.sejong.ac.kr/aiitdpt/intro/professor.do'
  );

INSERT INTO crawl_source (department_id, source_name, source_url, parser_type)
SELECT d.id, '콘텐츠소프트웨어학과 교수진',
       'https://dept.sejong.ac.kr/softwaredpt/intro/professor.do', 'SEJONG_STANDARD'
FROM department d
JOIN college c ON c.id = d.college_id
WHERE c.name = '인공지능융합대학'
  AND d.name = '콘텐츠소프트웨어학과'
  AND NOT EXISTS (
      SELECT 1 FROM crawl_source s
      WHERE s.source_url = 'https://dept.sejong.ac.kr/softwaredpt/intro/professor.do'
  );

INSERT INTO crawl_source (department_id, source_name, source_url, parser_type)
SELECT d.id, '양자지능정보학과 교수진',
       'https://dept.sejong.ac.kr/qisedpt/intro/professor-introduction.do', 'SEJONG_QUANTUM'
FROM department d
JOIN college c ON c.id = d.college_id
WHERE c.name = '인공지능융합대학'
  AND d.name = '양자지능정보학과'
  AND NOT EXISTS (
      SELECT 1 FROM crawl_source s
      WHERE s.source_url = 'https://dept.sejong.ac.kr/qisedpt/intro/professor-introduction.do'
  );

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
SELECT c.id, '인공지능데이터사이언스학과'
FROM college c
WHERE c.name = '인공지능융합대학'
  AND NOT EXISTS (
      SELECT 1 FROM department d
      WHERE d.college_id = c.id AND d.name = '인공지능데이터사이언스학과'
  );

INSERT INTO professor (department_id, name, email)
SELECT id, '김민준', 'minjun.kim@example.ac.kr' FROM department WHERE name = '인공지능데이터사이언스학과';

INSERT INTO professor (department_id, name, email)
SELECT id, '박지훈', 'jihun.park@example.ac.kr' FROM department WHERE name = '컴퓨터공학과';

INSERT INTO professor (department_id, name, email)
SELECT id, '이서연', NULL FROM department WHERE name = '인공지능데이터사이언스학과';

INSERT INTO laboratory (professor_id, department_id, name, name_source, website_url, website_url_source, recruitment_status)
SELECT p.id, d.id, '인공지능연구실', 'OFFICIAL', 'https://ai-lab.example.ac.kr', 'MANUAL', 'RECRUITING'
FROM professor p JOIN department d ON d.id = p.department_id
WHERE p.name = '김민준';

INSERT INTO laboratory (professor_id, department_id, name, name_source, website_url, website_url_source, recruitment_status)
SELECT p.id, d.id, '컴퓨터비전연구실', 'OFFICIAL', 'https://vision-lab.example.ac.kr', 'MANUAL', 'ALWAYS_OPEN'
FROM professor p JOIN department d ON d.id = p.department_id
WHERE p.name = '박지훈';

INSERT INTO laboratory (professor_id, department_id, name, name_source, website_url, website_url_source, recruitment_status)
SELECT p.id, d.id, '데이터사이언스랩', 'OFFICIAL', NULL, NULL, 'UNKNOWN'
FROM professor p JOIN department d ON d.id = p.department_id
WHERE p.name = '이서연';

INSERT INTO laboratory (professor_id, department_id, name, name_source, website_url, website_url_source, recruitment_status, deleted_at)
SELECT p.id, d.id, '삭제된연구실', 'OFFICIAL', NULL, NULL, 'CLOSED', CURRENT_TIMESTAMP
FROM professor p JOIN department d ON d.id = p.department_id
WHERE p.name = '박지훈';

INSERT INTO research_field (name)
SELECT '인공지능'
WHERE NOT EXISTS (SELECT 1 FROM research_field WHERE name = '인공지능');

INSERT INTO research_field (name)
SELECT '머신러닝'
WHERE NOT EXISTS (SELECT 1 FROM research_field WHERE name = '머신러닝');

INSERT INTO research_field (name)
SELECT '컴퓨터비전'
WHERE NOT EXISTS (SELECT 1 FROM research_field WHERE name = '컴퓨터비전');

INSERT INTO research_field (name)
SELECT '딥러닝'
WHERE NOT EXISTS (SELECT 1 FROM research_field WHERE name = '딥러닝');

INSERT INTO laboratory_research_field (laboratory_id, research_field_id)
SELECT l.id, rf.id
FROM laboratory l CROSS JOIN research_field rf
WHERE l.name = '인공지능연구실' AND rf.name IN ('인공지능', '머신러닝');

INSERT INTO laboratory_research_field (laboratory_id, research_field_id)
SELECT l.id, rf.id
FROM laboratory l CROSS JOIN research_field rf
WHERE l.name = '컴퓨터비전연구실' AND rf.name IN ('컴퓨터비전', '딥러닝');

INSERT INTO app_user (email)
VALUES ('student1@example.com'), ('student2@example.com');

INSERT INTO bookmark (user_id, laboratory_id)
SELECT u.id, l.id
FROM app_user u CROSS JOIN laboratory l
WHERE u.email IN ('student1@example.com', 'student2@example.com')
  AND l.name = '인공지능연구실';

INSERT INTO bookmark (user_id, laboratory_id)
SELECT u.id, l.id
FROM app_user u CROSS JOIN laboratory l
WHERE u.email = 'student1@example.com'
  AND l.name = '컴퓨터비전연구실';

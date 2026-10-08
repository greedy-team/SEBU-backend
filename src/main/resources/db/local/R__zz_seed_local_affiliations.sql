INSERT INTO professor_department (professor_id, department_id, position)
SELECT p.id, p.department_id, p.position
FROM professor p
WHERE NOT EXISTS (
    SELECT 1
    FROM professor_department pd
    WHERE pd.professor_id = p.id
      AND pd.department_id = p.department_id
);

INSERT INTO laboratory_department (laboratory_id, department_id)
SELECT l.id, l.department_id
FROM laboratory l
WHERE NOT EXISTS (
    SELECT 1
    FROM laboratory_department ld
    WHERE ld.laboratory_id = l.id
      AND ld.department_id = l.department_id
);

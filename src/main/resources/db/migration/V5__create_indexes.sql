CREATE INDEX idx_department_college ON department (college_id);
CREATE INDEX idx_professor_department ON professor (department_id);
CREATE INDEX idx_laboratory_department ON laboratory (department_id);
CREATE INDEX idx_laboratory_professor ON laboratory (professor_id);
CREATE INDEX idx_laboratory_deleted_at ON laboratory (deleted_at);
CREATE INDEX idx_lrf_research_field ON laboratory_research_field (research_field_id);
CREATE INDEX idx_bookmark_laboratory ON bookmark (laboratory_id);

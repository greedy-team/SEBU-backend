CREATE INDEX idx_laboratory_review_laboratory_active_created
    ON laboratory_review (laboratory_id, deleted_at, created_at DESC, id DESC);

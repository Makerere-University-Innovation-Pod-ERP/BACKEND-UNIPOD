CREATE EXTENSION IF NOT EXISTS pg_trgm;

ALTER TABLE submissions
    ADD COLUMN submitter_name VARCHAR(255);

UPDATE submissions
SET submitter_name = 'Demo Submitter ' || substr(submitter_id, length('demo-submitter-') + 1)
WHERE submitter_id LIKE 'demo-submitter-%'
  AND submitter_name IS NULL;

CREATE INDEX IF NOT EXISTS ix_submissions_received_at
    ON submissions (received_at DESC, id);

CREATE INDEX IF NOT EXISTS ix_submissions_code_trgm
    ON submissions USING gin (lower(code) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS ix_submissions_title_trgm
    ON submissions USING gin (lower(title) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS ix_submissions_submitter_name_trgm
    ON submissions USING gin (lower(submitter_name) gin_trgm_ops);

ALTER TABLE submissions
    ADD COLUMN submitter_id VARCHAR(255);

UPDATE submissions
SET submitter_id = registered_by
WHERE submitter_id IS NULL;

ALTER TABLE submissions
    ALTER COLUMN submitter_id SET NOT NULL;

CREATE INDEX ix_submissions_submitter_updated_at
    ON submissions (submitter_id, updated_at DESC, id ASC);

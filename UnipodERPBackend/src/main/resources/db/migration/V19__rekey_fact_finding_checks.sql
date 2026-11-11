-- Re-key fact_finding_checks to the case identity used by the rest of the schema.
-- The table has no write path yet and holds no real data, so deleting every row here
-- is safe: brief_id was a placeholder BIGINT and officer_id a placeholder BIGINT, and
-- neither maps to submissions.id (UUID) or to the JWT subject (the user's email).
DELETE FROM fact_finding_checks;

ALTER TABLE fact_finding_checks
    DROP CONSTRAINT uq_fact_finding_checks_brief_round;

DROP INDEX ix_fact_finding_checks_brief_id;

ALTER TABLE fact_finding_checks
    DROP COLUMN brief_id,
    DROP COLUMN officer_id;

ALTER TABLE fact_finding_checks
    ADD COLUMN brief_id UUID NOT NULL,
    ADD COLUMN officer_id VARCHAR(100) NOT NULL;

ALTER TABLE fact_finding_checks
    ADD CONSTRAINT fk_fact_finding_checks_submission
        FOREIGN KEY (brief_id) REFERENCES submissions (id) ON DELETE CASCADE,
    ADD CONSTRAINT uq_fact_finding_checks_brief_round UNIQUE (brief_id, round);
CREATE INDEX ix_fact_finding_checks_brief_id ON fact_finding_checks (brief_id);

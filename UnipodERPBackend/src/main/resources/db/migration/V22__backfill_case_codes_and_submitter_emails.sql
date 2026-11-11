ALTER TABLE submissions
    ADD COLUMN submitter_email VARCHAR(254);

UPDATE submissions
SET submitter_email = api_users.email
FROM api_users
WHERE api_users.role = 'SUBMITTER'
  AND lower(api_users.email) = lower(submissions.submitter_id);

WITH legacy_drafts AS (
    SELECT
        CASE category WHEN 'STUDENT' THEN 'STU' ELSE 'GEN' END AS prefix,
        EXTRACT(YEAR FROM created_at AT TIME ZONE 'Africa/Kampala')::SMALLINT AS code_year
    FROM submissions
    WHERE stage = 'DRAFT'
      AND code IS NULL
), draft_counts AS (
    SELECT prefix, code_year, COUNT(*) AS draft_count
    FROM legacy_drafts
    GROUP BY prefix, code_year
)
INSERT INTO code_sequences (prefix, code_year, last_value)
SELECT prefix, code_year, draft_count
FROM draft_counts
ON CONFLICT (prefix, code_year)
DO UPDATE SET last_value = code_sequences.last_value + EXCLUDED.last_value;

WITH legacy_drafts AS (
    SELECT
        id,
        created_at,
        CASE category WHEN 'STUDENT' THEN 'STU' ELSE 'GEN' END AS prefix,
        EXTRACT(YEAR FROM created_at AT TIME ZONE 'Africa/Kampala')::SMALLINT AS code_year
    FROM submissions
    WHERE stage = 'DRAFT'
      AND code IS NULL
), ranked_drafts AS (
    SELECT
        id,
        prefix,
        code_year,
        ROW_NUMBER() OVER (PARTITION BY prefix, code_year ORDER BY created_at, id) AS sequence_number,
        COUNT(*) OVER (PARTITION BY prefix, code_year) AS draft_count
    FROM legacy_drafts
)
UPDATE submissions submission
SET code = ranked.prefix || '-' || ranked.code_year || '-' ||
           LPAD(sequence_value.value::TEXT, GREATEST(6, LENGTH(sequence_value.value::TEXT)), '0')
FROM ranked_drafts ranked
JOIN code_sequences codes
  ON codes.prefix = ranked.prefix
 AND codes.code_year = ranked.code_year
CROSS JOIN LATERAL (
    SELECT codes.last_value - ranked.draft_count + ranked.sequence_number AS value
) sequence_value
WHERE submission.id = ranked.id;

ALTER TABLE submissions
    DROP CONSTRAINT ck_submission_receipt_state,
    ADD CONSTRAINT ck_submission_receipt_state CHECK (
        (stage = 'DRAFT' AND code IS NOT NULL AND received_at IS NULL AND receiving_officer IS NULL
            AND responsible_sector_id IS NULL AND forwarded_at IS NULL)
        OR
        (stage = 'RECEIVED' AND code IS NOT NULL AND received_at IS NOT NULL AND receiving_officer IS NOT NULL
            AND responsible_sector_id IS NULL AND forwarded_at IS NULL)
        OR
        (stage IN ('FORWARDED', 'IN_PROGRESS')
            AND code IS NOT NULL AND received_at IS NOT NULL AND receiving_officer IS NOT NULL
            AND responsible_sector_id IS NOT NULL AND forwarded_at IS NOT NULL)
        OR
        (stage IN (
                'REGISTERED', 'FACT_FINDING', 'UNDER_REVIEW',
                'REFINEMENT_REQUESTED', 'FURTHER_HANDLING', 'COMPLETED'
            )
            AND code IS NOT NULL AND received_at IS NOT NULL AND receiving_officer IS NOT NULL
            AND (
                (responsible_sector_id IS NULL AND forwarded_at IS NULL)
                OR
                (responsible_sector_id IS NOT NULL AND forwarded_at IS NOT NULL)
            ))
    );

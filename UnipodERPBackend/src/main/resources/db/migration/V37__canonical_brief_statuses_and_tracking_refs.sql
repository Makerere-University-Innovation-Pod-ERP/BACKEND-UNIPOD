-- Canonical brief statuses and BRF tracking references.

ALTER TABLE submissions
    DROP CONSTRAINT ck_submissions_stage,
    DROP CONSTRAINT ck_submission_receipt_state;

ALTER TABLE case_stage_history
    DROP CONSTRAINT ck_case_stage_history_to_stage,
    DROP CONSTRAINT ck_case_stage_history_from_stage;

ALTER TABLE code_sequences
    DROP CONSTRAINT ck_code_sequences_prefix;

UPDATE submissions
SET stage = CASE stage
    WHEN 'RECEIVED' THEN 'SUBMITTED'
    WHEN 'REGISTERED' THEN 'SUBMITTED'
    WHEN 'FACT_FINDING' THEN 'IN_FACT_FINDING'
    WHEN 'UNDER_REVIEW' THEN 'IN_REVIEW'
    WHEN 'REFINEMENT_REQUESTED' THEN 'IMPROVEMENT_REQUESTED'
    WHEN 'FURTHER_HANDLING' THEN 'IN_REVIEW'
    WHEN 'REJECTED' THEN 'CLOSED'
    ELSE stage
END
WHERE stage IN (
    'RECEIVED', 'REGISTERED', 'FACT_FINDING', 'UNDER_REVIEW',
    'REFINEMENT_REQUESTED', 'FURTHER_HANDLING', 'REJECTED'
);

UPDATE case_stage_history
SET from_stage = CASE from_stage
        WHEN 'RECEIVED' THEN 'SUBMITTED'
        WHEN 'REGISTERED' THEN 'SUBMITTED'
        WHEN 'FACT_FINDING' THEN 'IN_FACT_FINDING'
        WHEN 'UNDER_REVIEW' THEN 'IN_REVIEW'
        WHEN 'REFINEMENT_REQUESTED' THEN 'IMPROVEMENT_REQUESTED'
        WHEN 'FURTHER_HANDLING' THEN 'IN_REVIEW'
        WHEN 'REJECTED' THEN 'CLOSED'
        ELSE from_stage
    END,
    to_stage = CASE to_stage
        WHEN 'RECEIVED' THEN 'SUBMITTED'
        WHEN 'REGISTERED' THEN 'SUBMITTED'
        WHEN 'FACT_FINDING' THEN 'IN_FACT_FINDING'
        WHEN 'UNDER_REVIEW' THEN 'IN_REVIEW'
        WHEN 'REFINEMENT_REQUESTED' THEN 'IMPROVEMENT_REQUESTED'
        WHEN 'FURTHER_HANDLING' THEN 'IN_REVIEW'
        WHEN 'REJECTED' THEN 'CLOSED'
        ELSE to_stage
    END
WHERE from_stage IN (
        'RECEIVED', 'REGISTERED', 'FACT_FINDING', 'UNDER_REVIEW',
        'REFINEMENT_REQUESTED', 'FURTHER_HANDLING', 'REJECTED'
    )
    OR to_stage IN (
        'RECEIVED', 'REGISTERED', 'FACT_FINDING', 'UNDER_REVIEW',
        'REFINEMENT_REQUESTED', 'FURTHER_HANDLING', 'REJECTED'
    );

WITH ranked AS (
    SELECT id,
        EXTRACT(YEAR FROM COALESCE(submitted_at, created_at))::int AS code_year,
        row_number() OVER (
            PARTITION BY EXTRACT(YEAR FROM COALESCE(submitted_at, created_at))
            ORDER BY COALESCE(submitted_at, created_at), id
        ) AS sequence
    FROM submissions
    WHERE code IS NOT NULL
)
UPDATE submissions submission
SET code = 'BRF-' || ranked.code_year || '-' || lpad(ranked.sequence::text, 6, '0')
FROM ranked
WHERE submission.id = ranked.id;

WITH maximums AS (
    SELECT EXTRACT(YEAR FROM COALESCE(submitted_at, created_at))::int AS code_year,
        count(*) AS last_value
    FROM submissions
    WHERE code IS NOT NULL
    GROUP BY EXTRACT(YEAR FROM COALESCE(submitted_at, created_at))
)
INSERT INTO code_sequences (prefix, code_year, last_value)
SELECT 'BRF', code_year, last_value
FROM maximums
ON CONFLICT (prefix, code_year)
DO UPDATE SET last_value = GREATEST(code_sequences.last_value, EXCLUDED.last_value);

ALTER TABLE code_sequences
    ADD CONSTRAINT ck_code_sequences_prefix
        CHECK (prefix IN ('BRF', 'STU', 'GEN', 'SRV', 'PRJ'));

ALTER TABLE submissions
    ADD CONSTRAINT ck_submissions_stage CHECK (stage IN (
        'DRAFT', 'SUBMITTED', 'AWAITING_INFORMATION', 'FORWARDED',
        'IN_FACT_FINDING', 'READY_FOR_REVIEW', 'IN_REVIEW',
        'IMPROVEMENT_REQUESTED', 'RESUBMITTED', 'CONFIRMED_AS_PROJECT',
        'AWAITING_PAYMENT', 'IN_PROGRESS', 'COMPLETED', 'CLOSED'
    )),
    ADD CONSTRAINT ck_submission_receipt_state CHECK (
        (stage = 'DRAFT'
            AND code IS NULL
            AND received_at IS NULL
            AND receiving_officer IS NULL
            AND responsible_sector_id IS NULL
            AND forwarded_at IS NULL)
        OR
        (stage <> 'DRAFT'
            AND code IS NOT NULL
            AND received_at IS NOT NULL)
    );

ALTER TABLE case_stage_history
    ADD CONSTRAINT ck_case_stage_history_to_stage CHECK (to_stage IN (
        'DRAFT', 'SUBMITTED', 'AWAITING_INFORMATION', 'FORWARDED',
        'IN_FACT_FINDING', 'READY_FOR_REVIEW', 'IN_REVIEW',
        'IMPROVEMENT_REQUESTED', 'RESUBMITTED', 'CONFIRMED_AS_PROJECT',
        'AWAITING_PAYMENT', 'IN_PROGRESS', 'COMPLETED', 'CLOSED'
    )),
    ADD CONSTRAINT ck_case_stage_history_from_stage
        CHECK (from_stage IS NULL OR from_stage IN (
            'DRAFT', 'SUBMITTED', 'AWAITING_INFORMATION', 'FORWARDED',
            'IN_FACT_FINDING', 'READY_FOR_REVIEW', 'IN_REVIEW',
            'IMPROVEMENT_REQUESTED', 'RESUBMITTED', 'CONFIRMED_AS_PROJECT',
            'AWAITING_PAYMENT', 'IN_PROGRESS', 'COMPLETED', 'CLOSED'
        ));

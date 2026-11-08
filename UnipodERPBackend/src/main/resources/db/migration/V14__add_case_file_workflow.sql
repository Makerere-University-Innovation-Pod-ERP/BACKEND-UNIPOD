ALTER TABLE submissions
    ADD COLUMN outcome_status VARCHAR(24) NOT NULL DEFAULT 'NOT_STARTED',
    ADD COLUMN outcome_decision VARCHAR(120),
    ADD COLUMN outcome_note TEXT,
    ADD COLUMN outcome_decided_by VARCHAR(255),
    ADD COLUMN outcome_decided_at TIMESTAMPTZ,
    ADD CONSTRAINT ck_submissions_outcome_status
        CHECK (outcome_status IN ('NOT_STARTED', 'IN_PROGRESS', 'DECIDED')),
    ADD CONSTRAINT ck_submissions_outcome_decision
        CHECK (
            (outcome_status = 'NOT_STARTED'
                AND outcome_decision IS NULL
                AND outcome_note IS NULL
                AND outcome_decided_by IS NULL
                AND outcome_decided_at IS NULL)
            OR (outcome_status = 'IN_PROGRESS'
                AND outcome_decision IS NULL
                AND outcome_decided_by IS NULL
                AND outcome_decided_at IS NULL)
            OR (outcome_status = 'DECIDED'
                AND outcome_decision IS NOT NULL
                AND length(btrim(outcome_decision)) > 0
                AND outcome_decided_by IS NOT NULL
                AND outcome_decided_at IS NOT NULL)
        );

ALTER TABLE submissions
    DROP CONSTRAINT ck_submissions_stage,
    DROP CONSTRAINT ck_submission_receipt_state;

ALTER TABLE submissions
    ADD CONSTRAINT ck_submissions_stage
        CHECK (stage IN (
            'DRAFT', 'RECEIVED', 'REGISTERED', 'FACT_FINDING', 'UNDER_REVIEW',
            'REFINEMENT_REQUESTED', 'FURTHER_HANDLING', 'FORWARDED', 'IN_PROGRESS', 'COMPLETED'
        )),
    ADD CONSTRAINT ck_submission_receipt_state CHECK (
        (stage = 'DRAFT' AND code IS NULL AND received_at IS NULL
            AND receiving_officer IS NULL AND responsible_sector_id IS NULL AND forwarded_at IS NULL)
        OR
        (stage <> 'DRAFT' AND code IS NOT NULL AND received_at IS NOT NULL
            AND receiving_officer IS NOT NULL)
    );

ALTER TABLE case_stage_history
    ADD COLUMN note TEXT;

ALTER TABLE case_stage_history
    DROP CONSTRAINT ck_case_stage_history_to_stage,
    DROP CONSTRAINT ck_case_stage_history_from_stage;

ALTER TABLE case_stage_history
    ADD CONSTRAINT ck_case_stage_history_to_stage
        CHECK (to_stage IN (
            'DRAFT', 'RECEIVED', 'REGISTERED', 'FACT_FINDING', 'UNDER_REVIEW',
            'REFINEMENT_REQUESTED', 'FURTHER_HANDLING', 'FORWARDED', 'IN_PROGRESS', 'COMPLETED'
        )),
    ADD CONSTRAINT ck_case_stage_history_from_stage
        CHECK (from_stage IS NULL OR from_stage IN (
            'DRAFT', 'RECEIVED', 'REGISTERED', 'FACT_FINDING', 'UNDER_REVIEW',
            'REFINEMENT_REQUESTED', 'FURTHER_HANDLING', 'FORWARDED', 'IN_PROGRESS', 'COMPLETED'
        ));

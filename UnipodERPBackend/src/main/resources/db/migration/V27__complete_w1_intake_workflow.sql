-- W1 intake completion: service requests, submitter submission/reply,
-- project confirmation, richer missing-item requests and versioned documents.

ALTER TABLE submissions
    DROP CONSTRAINT ck_submissions_category;

ALTER TABLE submissions
    ADD CONSTRAINT ck_submissions_category
        CHECK (category IN ('STUDENT', 'GENERAL', 'SERVICE_REQUEST'));

ALTER TABLE code_sequences
    DROP CONSTRAINT ck_code_sequences_prefix;

ALTER TABLE code_sequences
    ADD CONSTRAINT ck_code_sequences_prefix
        CHECK (prefix IN ('STU', 'GEN', 'SRV', 'PRJ'));

ALTER TABLE submissions
    ADD COLUMN submitter_phone VARCHAR(40),
    ADD COLUMN service_requested VARCHAR(255),
    ADD COLUMN service_requester VARCHAR(32),
    ADD COLUMN department VARCHAR(255),
    ADD COLUMN expected_result TEXT,
    ADD COLUMN project_code VARCHAR(32),
    ADD COLUMN brief_version INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN accepted_rules_version VARCHAR(32),
    ADD COLUMN forward_note TEXT,
    ADD COLUMN forwarded_by VARCHAR(255),
    ADD COLUMN rejection_reason TEXT,
    ADD COLUMN submitted_at TIMESTAMPTZ,
    ADD COLUMN confirmed_at TIMESTAMPTZ,
    ADD CONSTRAINT uq_submissions_project_code UNIQUE (project_code),
    ADD CONSTRAINT ck_submissions_brief_version CHECK (brief_version > 0),
    ADD CONSTRAINT ck_submissions_service_requester CHECK (
        service_requester IS NULL
        OR service_requester IN ('STUDENT', 'STAFF', 'EXTERNAL', 'OTHER')
    ),
    ADD CONSTRAINT ck_submissions_service_request_details CHECK (
        category = 'SERVICE_REQUEST'
        OR (
            service_requested IS NULL
            AND service_requester IS NULL
            AND expected_result IS NULL
        )
    );

UPDATE submissions
SET submitted_at = COALESCE(received_at, created_at)
WHERE stage <> 'DRAFT'
  AND submitted_at IS NULL;

ALTER TABLE submissions
    DROP CONSTRAINT ck_submissions_stage,
    DROP CONSTRAINT ck_submission_receipt_state,
    DROP CONSTRAINT ck_submission_information_request;

ALTER TABLE submissions
    ADD CONSTRAINT ck_submissions_stage CHECK (stage IN (
        'DRAFT', 'SUBMITTED', 'AWAITING_INFORMATION', 'RECEIVED', 'REGISTERED',
        'FACT_FINDING', 'UNDER_REVIEW', 'REFINEMENT_REQUESTED', 'FURTHER_HANDLING',
        'FORWARDED', 'IN_PROGRESS', 'CONFIRMED_AS_PROJECT', 'REJECTED', 'COMPLETED'
    )),
    ADD CONSTRAINT ck_submission_information_request CHECK (
        (information_requested_note IS NULL
            AND information_requested_by IS NULL
            AND information_requested_at IS NULL)
        OR
        (stage IN ('DRAFT', 'AWAITING_INFORMATION')
            AND information_requested_note IS NOT NULL
            AND length(btrim(information_requested_note)) > 0
            AND information_requested_by IS NOT NULL
            AND information_requested_at IS NOT NULL)
    ),
    ADD CONSTRAINT ck_submission_receipt_state CHECK (
        (stage = 'DRAFT'
            AND received_at IS NULL
            AND receiving_officer IS NULL
            AND responsible_sector_id IS NULL
            AND forwarded_at IS NULL)
        OR
        (stage IN ('SUBMITTED', 'AWAITING_INFORMATION')
            AND code IS NOT NULL
            AND received_at IS NOT NULL
            AND responsible_sector_id IS NULL
            AND forwarded_at IS NULL)
        OR
        (stage = 'REJECTED'
            AND code IS NOT NULL
            AND received_at IS NOT NULL)
        OR
        (stage = 'RECEIVED'
            AND code IS NOT NULL
            AND received_at IS NOT NULL
            AND receiving_officer IS NOT NULL
            AND responsible_sector_id IS NULL
            AND forwarded_at IS NULL)
        OR
        (stage IN ('FORWARDED', 'IN_PROGRESS')
            AND code IS NOT NULL
            AND received_at IS NOT NULL
            AND responsible_sector_id IS NOT NULL
            AND forwarded_at IS NOT NULL)
        OR
        (stage = 'CONFIRMED_AS_PROJECT'
            AND code IS NOT NULL
            AND received_at IS NOT NULL
            AND (responsible_sector_id IS NULL OR forwarded_at IS NOT NULL))
        OR
        (stage IN (
                'REGISTERED', 'FACT_FINDING', 'UNDER_REVIEW',
                'REFINEMENT_REQUESTED', 'FURTHER_HANDLING', 'COMPLETED'
            )
            AND code IS NOT NULL
            AND received_at IS NOT NULL
            AND receiving_officer IS NOT NULL
            AND (
                (responsible_sector_id IS NULL AND forwarded_at IS NULL)
                OR
                (responsible_sector_id IS NOT NULL AND forwarded_at IS NOT NULL)
            ))
    );

ALTER TABLE case_stage_history
    DROP CONSTRAINT ck_case_stage_history_to_stage,
    DROP CONSTRAINT ck_case_stage_history_from_stage;

ALTER TABLE case_stage_history
    ADD CONSTRAINT ck_case_stage_history_to_stage CHECK (to_stage IN (
        'DRAFT', 'SUBMITTED', 'AWAITING_INFORMATION', 'RECEIVED', 'REGISTERED',
        'FACT_FINDING', 'UNDER_REVIEW', 'REFINEMENT_REQUESTED', 'FURTHER_HANDLING',
        'FORWARDED', 'IN_PROGRESS', 'CONFIRMED_AS_PROJECT', 'REJECTED', 'COMPLETED'
    )),
    ADD CONSTRAINT ck_case_stage_history_from_stage
        CHECK (from_stage IS NULL OR from_stage IN (
            'DRAFT', 'SUBMITTED', 'AWAITING_INFORMATION', 'RECEIVED', 'REGISTERED',
            'FACT_FINDING', 'UNDER_REVIEW', 'REFINEMENT_REQUESTED', 'FURTHER_HANDLING',
            'FORWARDED', 'IN_PROGRESS', 'CONFIRMED_AS_PROJECT', 'REJECTED', 'COMPLETED'
        ));

ALTER TABLE documents
    DROP CONSTRAINT ck_documents_status;

ALTER TABLE documents
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_documents_status CHECK (status IN (
        'REQUIRED', 'OPTIONAL', 'MISSING', 'SUBMITTED', 'VERIFIED', 'ONE_FILE', 'NOT_REQUIRED'
    ));

ALTER TABLE missing_items
    ADD COLUMN reason VARCHAR(255),
    ADD COLUMN due_at TIMESTAMPTZ,
    ADD COLUMN message TEXT;

ALTER TABLE api_users
    ADD COLUMN password_setup_token_hash VARCHAR(64),
    ADD COLUMN password_setup_expires_at TIMESTAMPTZ;

CREATE INDEX ix_submissions_project_code
    ON submissions (project_code)
    WHERE project_code IS NOT NULL;

CREATE INDEX ix_submissions_stage_submitted_at
    ON submissions (stage, submitted_at DESC, id);

CREATE INDEX ix_api_users_password_setup_token
    ON api_users (password_setup_token_hash)
    WHERE password_setup_token_hash IS NOT NULL;

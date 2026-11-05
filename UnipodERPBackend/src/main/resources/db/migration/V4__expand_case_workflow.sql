ALTER TABLE submissions
    ADD COLUMN forwarded_at TIMESTAMPTZ;

ALTER TABLE submissions
    DROP CONSTRAINT ck_submissions_stage,
    DROP CONSTRAINT ck_submission_receipt_state,
    DROP CONSTRAINT ck_student_submission_details;

ALTER TABLE submissions
    ADD CONSTRAINT ck_submissions_stage
        CHECK (stage IN ('DRAFT', 'RECEIVED', 'FORWARDED', 'IN_PROGRESS', 'COMPLETED')),
    ADD CONSTRAINT ck_submission_receipt_state CHECK (
        (stage = 'DRAFT' AND code IS NULL AND received_at IS NULL
            AND receiving_officer IS NULL AND responsible_sector_id IS NULL AND forwarded_at IS NULL)
        OR
        (stage = 'RECEIVED' AND code IS NOT NULL AND received_at IS NOT NULL
            AND receiving_officer IS NOT NULL AND responsible_sector_id IS NULL AND forwarded_at IS NULL)
        OR
        (stage IN ('FORWARDED', 'IN_PROGRESS', 'COMPLETED')
            AND code IS NOT NULL AND received_at IS NOT NULL
            AND receiving_officer IS NOT NULL AND responsible_sector_id IS NOT NULL AND forwarded_at IS NOT NULL)
    ),
    ADD CONSTRAINT ck_student_submission_details CHECK (
        stage = 'DRAFT' OR category <> 'STUDENT' OR (
            student_number IS NOT NULL AND length(btrim(student_number)) > 0
            AND programme IS NOT NULL AND length(btrim(programme)) > 0
            AND supervisor IS NOT NULL AND length(btrim(supervisor)) > 0
            AND recommendation_letter_status IS NOT NULL
            AND recommendation_letter_status IN ('VERIFIED', 'SUBMITTED', 'ONE_FILE', 'NOT_REQUIRED')
        )
    );

ALTER TABLE case_stage_history
    DROP CONSTRAINT ck_case_stage_history_to_stage,
    DROP CONSTRAINT ck_case_stage_history_from_stage;

ALTER TABLE case_stage_history
    ADD CONSTRAINT ck_case_stage_history_to_stage
        CHECK (to_stage IN ('DRAFT', 'RECEIVED', 'FORWARDED', 'IN_PROGRESS', 'COMPLETED')),
    ADD CONSTRAINT ck_case_stage_history_from_stage
        CHECK (from_stage IS NULL OR from_stage IN ('DRAFT', 'RECEIVED', 'FORWARDED', 'IN_PROGRESS', 'COMPLETED'));

CREATE TABLE documents (
    id UUID NOT NULL,
    submission_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    status VARCHAR(24) NOT NULL,
    file_reference VARCHAR(256),
    created_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_documents PRIMARY KEY (id),
    CONSTRAINT fk_documents_submission
        FOREIGN KEY (submission_id) REFERENCES submissions (id) ON DELETE CASCADE,
    CONSTRAINT ck_documents_status
        CHECK (status IN ('VERIFIED', 'SUBMITTED', 'ONE_FILE', 'NOT_REQUIRED')),
    CONSTRAINT ck_documents_name CHECK (length(btrim(name)) > 0),
    CONSTRAINT ck_documents_file_reference
        CHECK (file_reference IS NULL OR file_reference ~ '^[A-Za-z0-9_-]+$')
);

CREATE INDEX ix_documents_submission_created_at
    ON documents (submission_id, created_at, id);

CREATE TABLE notification_outbox (
    id UUID NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    payload JSONB NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT pk_notification_outbox PRIMARY KEY (id),
    CONSTRAINT ck_notification_outbox_attempt_count CHECK (attempt_count >= 0)
);

CREATE INDEX ix_notification_outbox_pending
    ON notification_outbox (occurred_at, id)
    WHERE published_at IS NULL;

CREATE INDEX ix_submissions_category_stage_updated_at
    ON submissions (category, stage, updated_at DESC);

CREATE INDEX ix_submissions_stage_updated_at
    ON submissions (stage, updated_at DESC, id ASC);

CREATE INDEX ix_submissions_registered_by_updated_at
    ON submissions (registered_by, updated_at DESC, id ASC);

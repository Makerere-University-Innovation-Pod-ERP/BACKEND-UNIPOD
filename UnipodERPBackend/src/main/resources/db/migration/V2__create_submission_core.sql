CREATE TABLE code_sequences (
    prefix VARCHAR(3) NOT NULL,
    code_year SMALLINT NOT NULL,
    last_value BIGINT NOT NULL CHECK (last_value > 0),
    CONSTRAINT pk_code_sequences PRIMARY KEY (prefix, code_year),
    CONSTRAINT ck_code_sequences_prefix CHECK (prefix IN ('STU', 'GEN')),
    CONSTRAINT ck_code_sequences_year CHECK (code_year BETWEEN 2000 AND 9999)
);

CREATE TABLE submissions (
    id UUID NOT NULL,
    code VARCHAR(32),
    category VARCHAR(16) NOT NULL,
    title VARCHAR(240) NOT NULL,
    purpose TEXT NOT NULL,
    problem_addressed TEXT NOT NULL,
    registered_by VARCHAR(255) NOT NULL,
    receiving_officer VARCHAR(255),
    student_number VARCHAR(80),
    programme VARCHAR(200),
    supervisor VARCHAR(255),
    recommendation_letter_status VARCHAR(24),
    responsible_sector_id UUID,
    stage VARCHAR(32) NOT NULL,
    received_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_submissions PRIMARY KEY (id),
    CONSTRAINT uq_submissions_code UNIQUE (code),
    CONSTRAINT ck_submissions_category CHECK (category IN ('STUDENT', 'GENERAL')),
    CONSTRAINT ck_submissions_stage CHECK (stage IN ('DRAFT', 'RECEIVED')),
    CONSTRAINT ck_student_submission_details CHECK (
        stage <> 'RECEIVED' OR category <> 'STUDENT' OR (
            student_number IS NOT NULL AND length(btrim(student_number)) > 0
            AND programme IS NOT NULL AND length(btrim(programme)) > 0
            AND supervisor IS NOT NULL AND length(btrim(supervisor)) > 0
            AND recommendation_letter_status IN ('VERIFIED', 'SUBMITTED', 'ONE_FILE', 'NOT_REQUIRED')
        )
    ),
    CONSTRAINT ck_general_submission_has_no_student_details CHECK (
        category <> 'GENERAL' OR (
            student_number IS NULL
            AND programme IS NULL
            AND supervisor IS NULL
            AND recommendation_letter_status IS NULL
        )
    ),
    CONSTRAINT ck_submission_receipt_state CHECK (
        (stage = 'DRAFT' AND code IS NULL AND received_at IS NULL AND receiving_officer IS NULL)
        OR
        (stage = 'RECEIVED' AND code IS NOT NULL AND received_at IS NOT NULL AND receiving_officer IS NOT NULL)
    ),
    CONSTRAINT ck_submission_title CHECK (length(btrim(title)) > 0),
    CONSTRAINT ck_submission_purpose CHECK (length(btrim(purpose)) > 0),
    CONSTRAINT ck_submission_problem CHECK (length(btrim(problem_addressed)) > 0)
);

CREATE INDEX ix_submissions_stage_received_at ON submissions (stage, received_at DESC);
CREATE INDEX ix_submissions_registered_by_created_at ON submissions (registered_by, created_at DESC);
CREATE INDEX ix_submissions_updated_at ON submissions (updated_at DESC);

CREATE TABLE case_stage_history (
    id UUID NOT NULL,
    submission_id UUID NOT NULL,
    from_stage VARCHAR(32),
    to_stage VARCHAR(32) NOT NULL,
    changed_by VARCHAR(255) NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_case_stage_history PRIMARY KEY (id),
    CONSTRAINT fk_case_stage_history_submission
        FOREIGN KEY (submission_id) REFERENCES submissions (id) ON DELETE CASCADE,
    CONSTRAINT ck_case_stage_history_to_stage CHECK (to_stage IN ('DRAFT', 'RECEIVED')),
    CONSTRAINT ck_case_stage_history_from_stage CHECK (from_stage IS NULL OR from_stage IN ('DRAFT', 'RECEIVED'))
);

CREATE INDEX ix_case_stage_history_submission_changed_at
    ON case_stage_history (submission_id, changed_at DESC);

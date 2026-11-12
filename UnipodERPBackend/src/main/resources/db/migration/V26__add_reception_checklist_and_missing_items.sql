CREATE TABLE reception_checks (
    id UUID NOT NULL,
    submission_id UUID NOT NULL,
    item_key VARCHAR(64) NOT NULL,
    label VARCHAR(255) NOT NULL,
    checked BOOLEAN NOT NULL DEFAULT FALSE,
    note TEXT,
    checked_by VARCHAR(255),
    checked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_reception_checks PRIMARY KEY (id),
    CONSTRAINT fk_reception_checks_submission
        FOREIGN KEY (submission_id) REFERENCES submissions (id) ON DELETE CASCADE,
    CONSTRAINT uq_reception_checks_item UNIQUE (submission_id, item_key),
    CONSTRAINT ck_reception_checks_label CHECK (length(btrim(label)) > 0),
    CONSTRAINT ck_reception_checks_note CHECK (note IS NULL OR length(btrim(note)) > 0),
    CONSTRAINT ck_reception_checks_checked
        CHECK ((checked = FALSE) OR (checked_by IS NOT NULL AND checked_at IS NOT NULL))
);

CREATE INDEX ix_reception_checks_submission
    ON reception_checks (submission_id, item_key);

CREATE TABLE missing_items (
    id UUID NOT NULL,
    submission_id UUID NOT NULL,
    kind VARCHAR(16) NOT NULL,
    label VARCHAR(255) NOT NULL,
    note TEXT,
    document_type VARCHAR(32),
    status VARCHAR(16) NOT NULL,
    requested_by VARCHAR(255) NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL,
    last_reminded_at TIMESTAMPTZ,
    resolved_by VARCHAR(255),
    resolved_at TIMESTAMPTZ,
    resolution_note TEXT,
    CONSTRAINT pk_missing_items PRIMARY KEY (id),
    CONSTRAINT fk_missing_items_submission
        FOREIGN KEY (submission_id) REFERENCES submissions (id) ON DELETE CASCADE,
    CONSTRAINT ck_missing_items_kind CHECK (kind IN ('INFORMATION', 'DOCUMENT')),
    CONSTRAINT ck_missing_items_status CHECK (status IN ('OPEN', 'RESOLVED')),
    CONSTRAINT ck_missing_items_label CHECK (length(btrim(label)) > 0),
    CONSTRAINT ck_missing_items_document_type CHECK (
        document_type IS NULL OR document_type IN (
            'PROJECT_BRIEF', 'RECOMMENDATION_LETTER', 'SUPPORTING_EVIDENCE', 'OTHER'
        )
    ),
    CONSTRAINT ck_missing_items_document_kind CHECK (
        (kind = 'DOCUMENT' AND document_type IS NOT NULL)
        OR (kind = 'INFORMATION' AND document_type IS NULL)
    ),
    CONSTRAINT ck_missing_items_resolution CHECK (
        (status = 'OPEN' AND resolved_by IS NULL AND resolved_at IS NULL)
        OR (status = 'RESOLVED' AND resolved_by IS NOT NULL AND resolved_at IS NOT NULL)
    )
);

CREATE INDEX ix_missing_items_submission_status
    ON missing_items (submission_id, status, requested_at, id);

CREATE INDEX ix_missing_items_open
    ON missing_items (submission_id)
    WHERE status = 'OPEN';

ALTER TABLE api_users
    ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE;

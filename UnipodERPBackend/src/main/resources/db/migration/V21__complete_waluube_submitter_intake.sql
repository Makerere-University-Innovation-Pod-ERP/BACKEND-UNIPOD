ALTER TABLE submissions
    DROP CONSTRAINT ck_submission_receipt_state,
    ADD COLUMN information_requested_note TEXT,
    ADD COLUMN information_requested_by VARCHAR(255),
    ADD COLUMN information_requested_at TIMESTAMPTZ,
    ADD CONSTRAINT ck_submission_information_request CHECK (
        (information_requested_note IS NULL
            AND information_requested_by IS NULL
            AND information_requested_at IS NULL)
        OR
        (stage = 'DRAFT'
            AND information_requested_note IS NOT NULL
            AND length(btrim(information_requested_note)) > 0
            AND information_requested_by IS NOT NULL
            AND information_requested_at IS NOT NULL)
    ),
    ADD CONSTRAINT ck_submission_receipt_state CHECK (
        (stage = 'DRAFT' AND received_at IS NULL AND receiving_officer IS NULL
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

ALTER TABLE documents
    ADD COLUMN document_type VARCHAR(32) NOT NULL DEFAULT 'OTHER',
    ADD CONSTRAINT ck_documents_document_type CHECK (
        document_type IN ('PROJECT_BRIEF', 'RECOMMENDATION_LETTER', 'SUPPORTING_EVIDENCE', 'OTHER')
    );

CREATE TABLE audit_events (
    id UUID NOT NULL,
    actor VARCHAR(320) NOT NULL,
    http_method VARCHAR(10) NOT NULL,
    request_path VARCHAR(1024) NOT NULL,
    response_status SMALLINT NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_audit_events PRIMARY KEY (id),
    CONSTRAINT ck_audit_events_status CHECK (response_status BETWEEN 100 AND 599)
);

CREATE INDEX ix_audit_events_occurred_at ON audit_events (occurred_at DESC, id);

CREATE TABLE sectors (
    id UUID NOT NULL,
    name VARCHAR(120) NOT NULL,
    notification_email VARCHAR(320) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_sectors PRIMARY KEY (id),
    CONSTRAINT ck_sectors_name CHECK (length(btrim(name)) > 0),
    CONSTRAINT ck_sectors_email CHECK (
        length(btrim(notification_email)) > 0
        AND notification_email ~* '^[A-Z0-9.!#$%&''*+/=?^_`{|}~-]+@[A-Z0-9.-]+\.[A-Z]{2,}$'
    )
);

CREATE UNIQUE INDEX uq_sectors_name_ci ON sectors (lower(name));

ALTER TABLE submissions
    ADD CONSTRAINT fk_submissions_responsible_sector
        FOREIGN KEY (responsible_sector_id) REFERENCES sectors (id) NOT VALID;

ALTER TABLE documents
    ADD COLUMN content_type VARCHAR(255),
    ADD COLUMN file_size BIGINT,
    ADD CONSTRAINT ck_documents_file_size CHECK (file_size IS NULL OR file_size >= 0);

ALTER TABLE notification_outbox
    ADD COLUMN next_attempt_at TIMESTAMPTZ,
    ADD COLUMN locked_until TIMESTAMPTZ,
    ADD COLUMN last_error VARCHAR(120);

UPDATE notification_outbox
SET next_attempt_at = occurred_at
WHERE next_attempt_at IS NULL;

ALTER TABLE notification_outbox
    ALTER COLUMN next_attempt_at SET NOT NULL;

CREATE INDEX ix_notification_outbox_ready
    ON notification_outbox (next_attempt_at, occurred_at, id)
    WHERE published_at IS NULL;

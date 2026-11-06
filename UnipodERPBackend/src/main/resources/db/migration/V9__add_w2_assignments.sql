ALTER TABLE submissions
    ADD COLUMN assigned_reviewer_subject VARCHAR(255);

CREATE INDEX ix_submissions_assigned_reviewer_updated_at
    ON submissions (assigned_reviewer_subject, received_at DESC, id ASC);

CREATE TABLE sector_members (
    id UUID NOT NULL,
    sector_id UUID NOT NULL,
    subject VARCHAR(255) NOT NULL,
    CONSTRAINT pk_sector_members PRIMARY KEY (id),
    CONSTRAINT fk_sector_members_sector
        FOREIGN KEY (sector_id) REFERENCES sectors (id) ON DELETE CASCADE,
    CONSTRAINT uq_sector_members_sector_subject UNIQUE (sector_id, subject),
    CONSTRAINT ck_sector_members_subject CHECK (length(btrim(subject)) > 0)
);

CREATE INDEX ix_sector_members_subject_sector
    ON sector_members (subject, sector_id);

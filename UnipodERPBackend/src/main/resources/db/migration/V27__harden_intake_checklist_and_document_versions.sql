-- Follow-up hardening for the W1 intake tables. These statements cannot live in
-- V25/V26 because those migrations are already applied in deployed databases.

-- A checklist line that is not checked must not retain the actor/timestamp of a
-- previous check. Clear stale values, then enforce the invariant.
UPDATE reception_checks
SET checked_by = NULL,
    checked_at = NULL
WHERE checked = FALSE
  AND (checked_by IS NOT NULL OR checked_at IS NOT NULL);

ALTER TABLE reception_checks
    ADD CONSTRAINT ck_reception_checks_unchecked CHECK (
        checked = TRUE OR (checked_by IS NULL AND checked_at IS NULL)
    );

-- ix_reception_checks_submission duplicates the index backing
-- uq_reception_checks_item, so only the unique constraint needs to remain.
DROP INDEX IF EXISTS ix_reception_checks_submission;

-- Documents created before versioning existed were backfilled with version 0.
-- Give each document type per submission a gap-free sequence, continuing from
-- any versions created after V26 was applied.
WITH unversioned AS (
    SELECT id,
           submission_id,
           document_type,
           row_number() OVER (
               PARTITION BY submission_id, document_type
               ORDER BY created_at, id
           ) AS position
    FROM documents
    WHERE version = 0
), existing AS (
    SELECT submission_id, document_type, max(version) AS max_version
    FROM documents
    WHERE version > 0
    GROUP BY submission_id, document_type
)
UPDATE documents document
SET version = coalesce(existing.max_version, 0) + unversioned.position
FROM unversioned
LEFT JOIN existing
    ON existing.submission_id = unversioned.submission_id
   AND existing.document_type = unversioned.document_type
WHERE document.id = unversioned.id;

ALTER TABLE documents
    ALTER COLUMN version SET DEFAULT 1;

CREATE UNIQUE INDEX uq_documents_submission_type_version
    ON documents (submission_id, document_type, version);

ALTER TABLE notification_outbox
    ADD COLUMN dead_lettered_at TIMESTAMPTZ;

DROP INDEX IF EXISTS ix_notification_outbox_pending;

UPDATE notification_outbox
SET dead_lettered_at = occurred_at,
    last_error = 'Incompatible legacy notification payload'
WHERE published_at IS NULL
  AND dead_lettered_at IS NULL
  AND event_type = 'submission.forwarded'
  AND NOT (payload ? 'notificationEmail');

CREATE INDEX ix_notification_outbox_ready_v2
    ON notification_outbox (next_attempt_at, occurred_at, id)
    WHERE published_at IS NULL AND dead_lettered_at IS NULL;

ALTER TABLE documents
    ADD COLUMN original_filename VARCHAR(255);

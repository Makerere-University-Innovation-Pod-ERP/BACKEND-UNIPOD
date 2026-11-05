ALTER TABLE notification_outbox
    ADD COLUMN locked_by UUID;

DROP INDEX IF EXISTS ix_notification_outbox_ready;

DROP INDEX IF EXISTS ix_notification_outbox_ready_v2;

CREATE INDEX ix_notification_outbox_ready_v3
    ON notification_outbox (next_attempt_at, occurred_at, id)
    WHERE published_at IS NULL AND dead_lettered_at IS NULL
        AND event_type = 'submission.forwarded';

UPDATE notification_outbox
SET dead_lettered_at = CURRENT_TIMESTAMP
WHERE dead_lettered_at IS NOT NULL
  AND last_error = 'Incompatible legacy notification payload';

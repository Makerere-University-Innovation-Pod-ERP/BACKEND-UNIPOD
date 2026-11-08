DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = current_schema()
            AND table_name = 'submissions'
            AND column_name = 'submitter_id'
    ) THEN
        EXECUTE 'ALTER TABLE submissions ALTER COLUMN submitter_id DROP NOT NULL';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE table_schema = current_schema()
            AND table_name = 'submissions'
            AND constraint_name = 'fk_submissions_responsible_sector'
            AND constraint_type = 'FOREIGN KEY'
    ) THEN
        EXECUTE 'ALTER TABLE submissions DROP CONSTRAINT fk_submissions_responsible_sector';
    END IF;

    IF to_regclass(format('%I.notification_outbox', current_schema())) IS NOT NULL THEN
        EXECUTE 'ALTER TABLE notification_outbox ADD COLUMN IF NOT EXISTS next_attempt_at TIMESTAMPTZ';
        EXECUTE 'UPDATE notification_outbox SET next_attempt_at = occurred_at WHERE next_attempt_at IS NULL';
        EXECUTE 'ALTER TABLE notification_outbox ALTER COLUMN next_attempt_at SET DEFAULT CURRENT_TIMESTAMP';
        EXECUTE 'ALTER TABLE notification_outbox ALTER COLUMN next_attempt_at SET NOT NULL';
    END IF;
END
$$;

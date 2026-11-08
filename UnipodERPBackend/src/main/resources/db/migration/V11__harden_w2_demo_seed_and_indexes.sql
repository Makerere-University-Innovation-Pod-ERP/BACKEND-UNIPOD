ALTER INDEX IF EXISTS ix_submissions_assigned_reviewer_updated_at
    RENAME TO ix_submissions_assigned_reviewer_received_at;

DO $migration$
BEGIN
    IF current_setting('app.demo_seed', true) IS DISTINCT FROM 'true' THEN
        DELETE FROM case_stage_history
        WHERE submission_id IN (
            SELECT id FROM submissions
            WHERE id >= '20000000-0000-0000-0000-000000000001'::uuid
              AND id <= '20000000-0000-0000-0000-000000000010'::uuid
        );
        DELETE FROM submissions
        WHERE id >= '20000000-0000-0000-0000-000000000001'::uuid
          AND id <= '20000000-0000-0000-0000-000000000010'::uuid;
        DELETE FROM sector_members
        WHERE id >= '11000000-0000-0000-0000-000000000001'::uuid
          AND id <= '11000000-0000-0000-0000-000000000003'::uuid;
        DELETE FROM sectors
        WHERE id >= '10000000-0000-0000-0000-000000000001'::uuid
          AND id <= '10000000-0000-0000-0000-000000000003'::uuid;
    END IF;
END
$migration$;

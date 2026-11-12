-- Domain audit log (AD2): who did what to which record, with before and after values.
-- This is separate from audit_events (V21), which only records HTTP requests.
CREATE TABLE audit_log (
    id            UUID         NOT NULL,
    occurred_at   TIMESTAMPTZ  NOT NULL,
    actor         VARCHAR(254) NOT NULL,
    actor_role    VARCHAR(64)  NOT NULL,
    action        VARCHAR(64)  NOT NULL,
    entity_type   VARCHAR(64)  NOT NULL,
    entity_id     UUID,
    before_value  JSONB,
    after_value   JSONB,
    department_id UUID,
    request_id    VARCHAR(64),
    CONSTRAINT pk_audit_log PRIMARY KEY (id)
);

CREATE INDEX ix_audit_log_entity ON audit_log (entity_type, entity_id, occurred_at DESC);
CREATE INDEX ix_audit_log_occurred_at ON audit_log (occurred_at DESC);
CREATE INDEX ix_audit_log_actor ON audit_log (actor, occurred_at DESC);

-- Append-only: once a line is written nobody can change or remove it, not even through SQL.
CREATE FUNCTION audit_log_reject_change() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_log is append-only: % is not allowed', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_log_no_update_or_delete
    BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION audit_log_reject_change();

CREATE TRIGGER audit_log_no_truncate
    BEFORE TRUNCATE ON audit_log
    FOR EACH STATEMENT EXECUTE FUNCTION audit_log_reject_change();

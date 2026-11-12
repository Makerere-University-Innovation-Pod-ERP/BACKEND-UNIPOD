-- In-app notifications (NT1): the bell. One row per recipient per event.
CREATE TABLE notifications (
    id          UUID         NOT NULL,
    recipient   VARCHAR(254) NOT NULL,
    type        VARCHAR(64)  NOT NULL,
    title       VARCHAR(200) NOT NULL,
    body        TEXT,
    entity_type VARCHAR(64),
    entity_id   UUID,
    created_at  TIMESTAMPTZ  NOT NULL,
    read_at     TIMESTAMPTZ,
    CONSTRAINT pk_notifications PRIMARY KEY (id),
    CONSTRAINT ck_notifications_read_after_created CHECK (read_at IS NULL OR read_at >= created_at)
);

-- The bell list: my notifications, newest first.
CREATE INDEX ix_notifications_recipient_created ON notifications (recipient, created_at DESC);

-- The red badge: count of my unread ones. Partial index, so it only holds unread rows.
CREATE INDEX ix_notifications_recipient_unread ON notifications (recipient) WHERE read_at IS NULL;

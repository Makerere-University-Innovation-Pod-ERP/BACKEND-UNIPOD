CREATE TABLE notification_push_subscriptions (
    id         UUID          NOT NULL,
    recipient  VARCHAR(254)  NOT NULL,
    endpoint   VARCHAR(2048) NOT NULL,
    p256dh     VARCHAR(128)  NOT NULL,
    auth_key   VARCHAR(128)  NOT NULL,
    created_at TIMESTAMPTZ   NOT NULL,
    CONSTRAINT pk_notification_push_subscriptions PRIMARY KEY (id),
    CONSTRAINT uq_notification_push_subscriptions_endpoint UNIQUE (endpoint)
);

CREATE INDEX ix_notification_push_subscriptions_recipient
    ON notification_push_subscriptions (recipient);

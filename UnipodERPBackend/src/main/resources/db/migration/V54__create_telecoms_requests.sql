CREATE SEQUENCE telecoms_request_code_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE telecoms_requests (
    id                    UUID          NOT NULL,
    code                  VARCHAR(40)   NOT NULL,
    customer_name         VARCHAR(200)  NOT NULL DEFAULT '',
    customer_type         VARCHAR(40)   NOT NULL DEFAULT 'Student',
    service_wanted        VARCHAR(2000) NOT NULL DEFAULT '',
    equipment_involved    VARCHAR(1000) NOT NULL DEFAULT '',
    preferred_date        DATE,
    rules_accepted        BOOLEAN       NOT NULL DEFAULT FALSE,
    status                VARCHAR(24)   NOT NULL,
    estimate              JSONB,
    quote                 JSONB,
    payment_status        VARCHAR(24)   NOT NULL DEFAULT 'NOT_REQUESTED',
    payment_reference     VARCHAR(100),
    payment_confirmed_at  TIMESTAMPTZ,
    technician            VARCHAR(200)  NOT NULL DEFAULT '',
    parts_used            VARCHAR(2000) NOT NULL DEFAULT '',
    time_spent            VARCHAR(200)  NOT NULL DEFAULT '',
    work_notes            VARCHAR(5000) NOT NULL DEFAULT '',
    created_by            VARCHAR(254)  NOT NULL,
    created_at            TIMESTAMPTZ   NOT NULL,
    updated_at            TIMESTAMPTZ   NOT NULL,
    version               BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT pk_telecoms_requests PRIMARY KEY (id),
    CONSTRAINT uq_telecoms_requests_code UNIQUE (code),
    CONSTRAINT ck_telecoms_requests_status CHECK (
        status IN ('DRAFT', 'NEW', 'AWAITING_DETAILS', 'RETURNED', 'QUOTED', 'SCHEDULED', 'IN_PROGRESS', 'DONE')
    ),
    CONSTRAINT ck_telecoms_requests_customer_type CHECK (
        customer_type IN ('Student', 'Public', 'Company', 'Research Group', 'Internal')
    ),
    CONSTRAINT ck_telecoms_requests_payment_status CHECK (
        payment_status IN ('NOT_REQUESTED', 'AWAITING_PROOF', 'PROOF_SUBMITTED', 'CONFIRMED', 'REJECTED')
    ),
    CONSTRAINT ck_telecoms_requests_payment_confirmation CHECK (
        (payment_status = 'CONFIRMED') = (payment_confirmed_at IS NOT NULL)
    ),
    CONSTRAINT ck_telecoms_requests_done_notes CHECK (
        status <> 'DONE' OR length(btrim(work_notes)) > 0
    )
);

CREATE INDEX ix_telecoms_requests_updated
    ON telecoms_requests (updated_at DESC, id ASC);
CREATE INDEX ix_telecoms_requests_status_updated
    ON telecoms_requests (status, updated_at DESC, id ASC);

CREATE TABLE telecoms_request_history (
    id          UUID          NOT NULL,
    request_id  UUID          NOT NULL,
    status      VARCHAR(24)   NOT NULL,
    actor       VARCHAR(254)  NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL,
    note        VARCHAR(2000),
    CONSTRAINT pk_telecoms_request_history PRIMARY KEY (id),
    CONSTRAINT fk_telecoms_request_history_request
        FOREIGN KEY (request_id) REFERENCES telecoms_requests (id) ON DELETE CASCADE,
    CONSTRAINT ck_telecoms_request_history_status CHECK (
        status IN ('DRAFT', 'NEW', 'AWAITING_DETAILS', 'RETURNED', 'QUOTED', 'SCHEDULED', 'IN_PROGRESS', 'DONE')
    ),
    CONSTRAINT ck_telecoms_request_history_actor CHECK (length(btrim(actor)) > 0)
);

CREATE INDEX ix_telecoms_request_history_request
    ON telecoms_request_history (request_id, created_at ASC, id ASC);

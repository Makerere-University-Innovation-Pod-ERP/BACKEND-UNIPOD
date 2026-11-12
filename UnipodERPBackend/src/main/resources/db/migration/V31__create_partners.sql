CREATE TABLE partners (
    id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(30) NOT NULL,
    renewal_date DATE,
    contact_person VARCHAR(255),
    contact_email VARCHAR(254),
    notes VARCHAR(2000),
    document_reference VARCHAR(500),
    last_notified_status VARCHAR(20),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_partners PRIMARY KEY (id),
    CONSTRAINT ck_partners_name CHECK (length(btrim(name)) > 0),
    CONSTRAINT ck_partners_type CHECK (type IN (
        'INDUSTRY_PARTNER',
        'FUNDER',
        'ACADEMIC_PARTNER'
    )),
    CONSTRAINT ck_partners_last_notified_status CHECK (
        last_notified_status IS NULL OR last_notified_status IN (
            'ACTIVE',
            'RENEWAL_DUE',
            'EXPIRING_SOON',
            'EXPIRED'
        )
    )
);

-- Names are compared case-insensitively so "Acme Ltd" and "ACME LTD" cannot both exist.
CREATE UNIQUE INDEX uq_partners_name ON partners (lower(name));

-- Serves the list ordering: renewal date (undated last), then name.
CREATE INDEX ix_partners_renewal_date_name ON partners (renewal_date ASC NULLS LAST, lower(name));

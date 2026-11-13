-- W3 submitter home: invoices, payment evidence, receipts and facility bookings.
-- Invoice ownership is the customer email, which is the submitter's JWT subject.
-- Cross-module references (source_id) are plain values, not foreign keys.

ALTER TABLE api_users DROP CONSTRAINT ck_api_users_role;
ALTER TABLE api_users ADD CONSTRAINT ck_api_users_role CHECK (role IN (
    'ADMINISTRATOR',
    'INNOVATION_COORDINATOR',
    'DEPARTMENT_COORDINATOR',
    'RECEPTIONIST',
    'FACT_FINDING_OFFICER',
    'REVIEWER',
    'SECTOR_OFFICER',
    'IP_LIAISON',
    'SUBMITTER',
    'FINANCE_OFFICER',
    'OBSERVER'
));

CREATE SEQUENCE invoice_number_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE receipt_number_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE booking_reference_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE invoices (
    id UUID NOT NULL,
    number VARCHAR(32) NOT NULL,
    customer_email VARCHAR(254) NOT NULL,
    customer_name VARCHAR(255),
    department VARCHAR(100),
    description VARCHAR(1000) NOT NULL,
    amount BIGINT NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'UGX',
    source_type VARCHAR(16) NOT NULL,
    source_id VARCHAR(64),
    status VARCHAR(24) NOT NULL,
    issued_by VARCHAR(254) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_invoices PRIMARY KEY (id),
    CONSTRAINT uq_invoices_number UNIQUE (number),
    CONSTRAINT ck_invoices_amount CHECK (amount > 0),
    CONSTRAINT ck_invoices_currency CHECK (currency = 'UGX'),
    CONSTRAINT ck_invoices_source_type CHECK (source_type IN ('REQUEST', 'BOOKING', 'SESSION', 'JOB')),
    CONSTRAINT ck_invoices_status CHECK (
        status IN ('WAITING_FOR_PROOF', 'WAITING_FOR_CHECK', 'PAID', 'RECEIPTED')
    )
);

CREATE INDEX ix_invoices_customer_status ON invoices (lower(customer_email), status);
CREATE INDEX ix_invoices_status_created ON invoices (status, created_at);

CREATE TABLE invoice_payment_proofs (
    id UUID NOT NULL,
    invoice_id UUID NOT NULL,
    reference VARCHAR(120) NOT NULL,
    amount BIGINT NOT NULL,
    paid_on DATE NOT NULL,
    file_reference VARCHAR(256) NOT NULL,
    content_type VARCHAR(255) NOT NULL,
    file_size BIGINT NOT NULL,
    original_filename VARCHAR(255),
    status VARCHAR(16) NOT NULL,
    rejection_reason VARCHAR(500),
    uploaded_by VARCHAR(254) NOT NULL,
    uploaded_at TIMESTAMPTZ NOT NULL,
    verified_by VARCHAR(254),
    verified_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_invoice_payment_proofs PRIMARY KEY (id),
    CONSTRAINT fk_invoice_payment_proofs_invoice
        FOREIGN KEY (invoice_id) REFERENCES invoices (id),
    CONSTRAINT ck_invoice_payment_proofs_amount CHECK (amount > 0),
    CONSTRAINT ck_invoice_payment_proofs_status CHECK (status IN ('PENDING', 'VERIFIED', 'REJECTED'))
);

CREATE INDEX ix_invoice_payment_proofs_invoice
    ON invoice_payment_proofs (invoice_id, uploaded_at DESC);

CREATE TABLE invoice_receipts (
    id UUID NOT NULL,
    invoice_id UUID NOT NULL,
    receipt_number VARCHAR(32) NOT NULL,
    issued_by VARCHAR(254) NOT NULL,
    issued_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_invoice_receipts PRIMARY KEY (id),
    CONSTRAINT uq_invoice_receipts_invoice UNIQUE (invoice_id),
    CONSTRAINT uq_invoice_receipts_number UNIQUE (receipt_number),
    CONSTRAINT fk_invoice_receipts_invoice
        FOREIGN KEY (invoice_id) REFERENCES invoices (id)
);

CREATE TABLE facilities (
    id UUID NOT NULL,
    name VARCHAR(160) NOT NULL,
    location VARCHAR(160),
    capacity INTEGER NOT NULL,
    status VARCHAR(16) NOT NULL,
    department VARCHAR(100),
    created_by VARCHAR(254) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_facilities PRIMARY KEY (id),
    CONSTRAINT uq_facilities_name UNIQUE (name),
    CONSTRAINT ck_facilities_capacity CHECK (capacity > 0),
    CONSTRAINT ck_facilities_status CHECK (status IN ('AVAILABLE', 'BOOKED', 'MAINTENANCE'))
);

CREATE TABLE bookings (
    id UUID NOT NULL,
    reference VARCHAR(32) NOT NULL,
    facility_id UUID NOT NULL,
    requester_id VARCHAR(254) NOT NULL,
    purpose VARCHAR(1000) NOT NULL,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    attendees INTEGER NOT NULL,
    operator VARCHAR(254),
    status VARCHAR(16) NOT NULL,
    rejection_reason VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_bookings PRIMARY KEY (id),
    CONSTRAINT uq_bookings_reference UNIQUE (reference),
    CONSTRAINT fk_bookings_facility
        FOREIGN KEY (facility_id) REFERENCES facilities (id),
    CONSTRAINT ck_bookings_attendees CHECK (attendees > 0),
    CONSTRAINT ck_bookings_time CHECK (ends_at > starts_at),
    CONSTRAINT ck_bookings_status CHECK (
        status IN ('PENDING', 'APPROVED', 'REJECTED', 'CHECKED_IN', 'COMPLETED', 'CANCELLED')
    )
);

CREATE INDEX ix_bookings_requester_starts ON bookings (requester_id, starts_at DESC);
CREATE INDEX ix_bookings_facility_starts ON bookings (facility_id, starts_at, ends_at);
CREATE INDEX ix_bookings_status_starts ON bookings (status, starts_at);

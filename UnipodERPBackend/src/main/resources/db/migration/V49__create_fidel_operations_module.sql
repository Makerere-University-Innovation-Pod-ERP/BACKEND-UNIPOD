-- Fidel operations modules: allocations/equipment flow, media delivery, catalogue,
-- unified department orders, media contracts, and timetable blocks.

ALTER TABLE facilities
    ADD COLUMN IF NOT EXISTS requires_safety_pass BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE facilities
    SET requires_safety_pass = TRUE
    WHERE UPPER(department) = 'CNC';

ALTER TABLE bookings
    ADD COLUMN IF NOT EXISTS request_type VARCHAR(20) NOT NULL DEFAULT 'FACILITY',
    ADD COLUMN IF NOT EXISTS is_equipment_request BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS equipment_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS purpose_detail VARCHAR(2000),
    ADD COLUMN IF NOT EXISTS decision_note VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS decided_by VARCHAR(254),
    ADD COLUMN IF NOT EXISTS decided_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS returned_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS condition_out VARCHAR(32),
    ADD COLUMN IF NOT EXISTS condition_returned VARCHAR(32),
    ADD COLUMN IF NOT EXISTS return_notes VARCHAR(2000);

ALTER TABLE bookings DROP CONSTRAINT IF EXISTS ck_bookings_status;
ALTER TABLE bookings
    ADD CONSTRAINT ck_bookings_status CHECK (
        status IN ('PENDING', 'APPROVED', 'REJECTED', 'CHECKED_IN', 'COMPLETED', 'CANCELLED', 'OUT', 'RETURNED')
    );
ALTER TABLE bookings DROP CONSTRAINT IF EXISTS ck_bookings_request_type;
ALTER TABLE bookings
    ADD CONSTRAINT ck_bookings_request_type CHECK (request_type IN ('FACILITY', 'EQUIPMENT'));
ALTER TABLE bookings DROP CONSTRAINT IF EXISTS ck_bookings_equipment_request_matches_type;
ALTER TABLE bookings
    ADD CONSTRAINT ck_bookings_equipment_request_matches_type
        CHECK ((request_type = 'EQUIPMENT') = is_equipment_request);
ALTER TABLE bookings DROP CONSTRAINT IF EXISTS ck_bookings_condition_out;
ALTER TABLE bookings
    ADD CONSTRAINT ck_bookings_condition_out CHECK (
        condition_out IS NULL OR condition_out IN ('WORKING', 'BEING_REPAIRED', 'DAMAGED')
    );
ALTER TABLE bookings DROP CONSTRAINT IF EXISTS ck_bookings_condition_returned;
ALTER TABLE bookings
    ADD CONSTRAINT ck_bookings_condition_returned CHECK (
        condition_returned IS NULL OR condition_returned IN ('WORKING', 'BEING_REPAIRED', 'DAMAGED')
    );

CREATE INDEX IF NOT EXISTS ix_bookings_request_type_status ON bookings (request_type, status, starts_at DESC);

ALTER TABLE training_participants
    ADD COLUMN IF NOT EXISTS passed_at TIMESTAMPTZ;

CREATE TABLE IF NOT EXISTS service_catalogue_items (
    id UUID NOT NULL,
    department VARCHAR(32) NOT NULL,
    name VARCHAR(200) NOT NULL,
    description VARCHAR(2000) NOT NULL DEFAULT '',
    price BIGINT NOT NULL DEFAULT 0,
    currency VARCHAR(3) NOT NULL DEFAULT 'UGX',
    turnaround_estimate VARCHAR(120),
    whatsapp_group_url VARCHAR(500),
    requires_brief BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_service_catalogue_items PRIMARY KEY (id),
    CONSTRAINT ck_service_catalogue_items_department CHECK (department IN ('MULTIMEDIA','TEXTILE','CNC','TELECOMS')),
    CONSTRAINT ck_service_catalogue_items_price CHECK (price >= 0)
);
CREATE INDEX IF NOT EXISTS ix_service_catalogue_items_dept_active
    ON service_catalogue_items (department, active, sort_order, name);

CREATE TABLE IF NOT EXISTS department_orders (
    id UUID NOT NULL,
    code VARCHAR(40) NOT NULL,
    department VARCHAR(32) NOT NULL,
    customer_id VARCHAR(254) NOT NULL,
    title VARCHAR(255) NOT NULL,
    quantity INTEGER NOT NULL DEFAULT 1,
    date_needed DATE,
    accepted_rules_version VARCHAR(32),
    stage VARCHAR(40) NOT NULL,
    specs JSONB NOT NULL DEFAULT '{}'::jsonb,
    quote JSONB,
    quote_submitted_at TIMESTAMPTZ,
    quoted_by VARCHAR(254),
    invoice_id UUID,
    invoice_status VARCHAR(32),
    payment_confirmed_at TIMESTAMPTZ,
    materials_used JSONB,
    machine_time_used VARCHAR(255),
    quality_checklist JSONB,
    collection_signature VARCHAR(255),
    stage_history JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_by VARCHAR(254) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_department_orders_invoice
        FOREIGN KEY (invoice_id) REFERENCES invoices (id),
    CONSTRAINT pk_department_orders PRIMARY KEY (id),
    CONSTRAINT uq_department_orders_code UNIQUE (code),
    CONSTRAINT ck_department_orders_department CHECK (department IN ('MULTIMEDIA','TEXTILE','CNC','TELECOMS')),
    CONSTRAINT ck_department_orders_quantity CHECK (quantity > 0)
);
CREATE INDEX IF NOT EXISTS ix_department_orders_dept_stage
    ON department_orders (department, stage, updated_at DESC, created_at DESC);
CREATE INDEX IF NOT EXISTS ix_department_orders_customer
    ON department_orders (customer_id, updated_at DESC);

CREATE SEQUENCE IF NOT EXISTS department_order_code_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS media_contracts (
    id UUID NOT NULL,
    code VARCHAR(40) NOT NULL,
    client VARCHAR(255) NOT NULL,
    event_name VARCHAR(255) NOT NULL,
    event_date DATE NOT NULL,
    venue VARCHAR(255),
    agreed_price BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'UGX',
    milestone_payment_dates JSONB NOT NULL DEFAULT '[]'::jsonb,
    signed_contract_doc_id UUID,
    assigned_staff JSONB NOT NULL DEFAULT '[]'::jsonb,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    invoice_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_by VARCHAR(254) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_media_contracts_signed_document
        FOREIGN KEY (signed_contract_doc_id) REFERENCES documents (id),
    CONSTRAINT pk_media_contracts PRIMARY KEY (id),
    CONSTRAINT uq_media_contracts_code UNIQUE (code),
    CONSTRAINT ck_media_contracts_agreed_price CHECK (agreed_price >= 0),
    CONSTRAINT ck_media_contracts_status CHECK (status IN ('DRAFT','SIGNED','IN_PROGRESS','COMPLETED','CANCELLED'))
);
CREATE INDEX IF NOT EXISTS ix_media_contracts_event_date ON media_contracts (event_date DESC);
CREATE SEQUENCE IF NOT EXISTS media_contract_code_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS timetable_blocks (
    id UUID NOT NULL,
    facility_id UUID,
    department VARCHAR(32),
    title VARCHAR(255) NOT NULL,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    recurrence_rule VARCHAR(255),
    recurrence_until DATE,
    source VARCHAR(32) NOT NULL DEFAULT 'MANUAL',
    created_by VARCHAR(254) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_timetable_blocks PRIMARY KEY (id),
    CONSTRAINT ck_timetable_blocks_window CHECK (ends_at > starts_at),
    CONSTRAINT ck_timetable_blocks_source CHECK (source IN ('MANUAL','IMPORT')),
    CONSTRAINT fk_timetable_blocks_facility FOREIGN KEY (facility_id) REFERENCES facilities (id)
);
CREATE INDEX IF NOT EXISTS ix_timetable_blocks_facility_time ON timetable_blocks (facility_id, starts_at, ends_at);
CREATE INDEX IF NOT EXISTS ix_timetable_blocks_time ON timetable_blocks (starts_at, ends_at);
-- add machine booking eligibility tracking and participant user linkage for safety pass
CREATE TABLE IF NOT EXISTS machine_booking_eligibilities (
    id UUID NOT NULL,
    user_id VARCHAR(254) NOT NULL,
    department VARCHAR(32) NOT NULL DEFAULT 'CNC',
    session_id UUID,
    granted_at TIMESTAMPTZ NOT NULL,
    granted_by VARCHAR(254) NOT NULL,
    CONSTRAINT pk_machine_booking_eligibilities PRIMARY KEY (id),
    CONSTRAINT uq_machine_booking_eligibilities_user_dept UNIQUE (user_id, department)
);

ALTER TABLE training_participants
    ADD COLUMN IF NOT EXISTS user_id VARCHAR(254);
CREATE INDEX IF NOT EXISTS ix_training_participants_session_user
    ON training_participants (session_id, user_id);
-- extend for F4 media requests
CREATE TABLE IF NOT EXISTS media_requests (
    id UUID NOT NULL,
    code VARCHAR(40) NOT NULL,
    brief_id UUID,
    department_order_id UUID,
    submitter_id VARCHAR(254) NOT NULL,
    title VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'IN_PRODUCTION',
    delivery_payload JSONB,
    delivered_at TIMESTAMPTZ,
    delivered_by VARCHAR(254),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_media_requests PRIMARY KEY (id),
    CONSTRAINT uq_media_requests_code UNIQUE (code),
    CONSTRAINT ck_media_requests_status CHECK (status IN ('REQUESTED','SCHEDULED','IN_PRODUCTION','DELIVERED','COMPLETED','CANCELLED'))
);
CREATE SEQUENCE IF NOT EXISTS media_request_code_seq START WITH 1 INCREMENT BY 1;
CREATE INDEX IF NOT EXISTS ix_media_requests_brief_updated
    ON media_requests (brief_id, updated_at DESC);
CREATE INDEX IF NOT EXISTS ix_media_requests_status_updated
    ON media_requests (status, updated_at DESC);

CREATE TABLE IF NOT EXISTS stock_items (
    id UUID NOT NULL,
    sku VARCHAR(64) NOT NULL,
    name VARCHAR(255) NOT NULL,
    department VARCHAR(32),
    quantity_on_hand INTEGER NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_stock_items PRIMARY KEY (id),
    CONSTRAINT uq_stock_items_sku UNIQUE (sku),
    CONSTRAINT ck_stock_items_qty CHECK (quantity_on_hand >= 0)
);

CREATE TABLE IF NOT EXISTS stock_movements (
    id UUID NOT NULL,
    stock_item_id UUID NOT NULL,
    department_order_id UUID,
    delta INTEGER NOT NULL,
    reason VARCHAR(255),
    created_by VARCHAR(254) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_stock_movements PRIMARY KEY (id),
    CONSTRAINT fk_stock_movements_item FOREIGN KEY (stock_item_id) REFERENCES stock_items (id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS ix_stock_movements_item ON stock_movements (stock_item_id, created_at DESC);

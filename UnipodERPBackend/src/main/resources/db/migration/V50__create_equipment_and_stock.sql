-- F2 Equipment & Stock.
-- Equipment is counted per type, not per serial number (D-008): one row has a total and an available quantity.
-- Machines are a separate table because their states and fields differ. Fault and service reports share one table.
-- stock_items and stock_movements already exist (V49); they are extended here, never recreated.

CREATE TABLE equipment_items (
    id                 UUID          NOT NULL,
    department         VARCHAR(32)   NOT NULL,
    name               VARCHAR(255)  NOT NULL,
    category           VARCHAR(100)  NOT NULL DEFAULT '',
    asset_tag          VARCHAR(64)   NOT NULL,
    total_quantity     INTEGER       NOT NULL,
    available_quantity INTEGER       NOT NULL,
    state              VARCHAR(24)   NOT NULL DEFAULT 'AVAILABLE',
    location           VARCHAR(255)  NOT NULL DEFAULT '',
    note               VARCHAR(2000) NOT NULL DEFAULT '',
    created_at         TIMESTAMPTZ   NOT NULL,
    updated_at         TIMESTAMPTZ   NOT NULL,
    version            BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT pk_equipment_items PRIMARY KEY (id),
    CONSTRAINT ck_equipment_items_department CHECK (department IN ('MULTIMEDIA', 'TEXTILE', 'CNC', 'TELECOMS')),
    CONSTRAINT ck_equipment_items_name CHECK (length(btrim(name)) > 0),
    CONSTRAINT ck_equipment_items_asset_tag CHECK (length(btrim(asset_tag)) > 0),
    CONSTRAINT ck_equipment_items_total CHECK (total_quantity > 0),
    CONSTRAINT ck_equipment_items_available CHECK (available_quantity >= 0 AND available_quantity <= total_quantity),
    CONSTRAINT ck_equipment_items_state CHECK (
        state IN ('AVAILABLE', 'IN_USE', 'LOANED', 'UNDER_MAINTENANCE', 'FAULTY')
    )
);

CREATE UNIQUE INDEX uq_equipment_items_asset_tag ON equipment_items (lower(asset_tag));
CREATE INDEX ix_equipment_items_department_name ON equipment_items (department, lower(name), asset_tag);

CREATE TABLE machines (
    id         UUID          NOT NULL,
    department VARCHAR(32)   NOT NULL,
    name       VARCHAR(255)  NOT NULL,
    state      VARCHAR(24)   NOT NULL DEFAULT 'WORKING',
    note       VARCHAR(2000) NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ   NOT NULL,
    updated_at TIMESTAMPTZ   NOT NULL,
    version    BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT pk_machines PRIMARY KEY (id),
    CONSTRAINT ck_machines_department CHECK (department IN ('MULTIMEDIA', 'TEXTILE', 'CNC', 'TELECOMS')),
    CONSTRAINT ck_machines_name CHECK (length(btrim(name)) > 0),
    CONSTRAINT ck_machines_state CHECK (
        state IN ('WORKING', 'FAULT_REPORTED', 'SERVICE_DUE', 'BEING_REPAIRED')
    )
);

CREATE UNIQUE INDEX uq_machines_department_name ON machines (department, lower(name));

-- A report belongs to exactly one equipment item or one machine. Equipment only has faults; machines also have service reports.
-- The description is free text kept here and nowhere else (never in the audit log).
CREATE TABLE fault_reports (
    id           UUID          NOT NULL,
    kind         VARCHAR(16)   NOT NULL,
    equipment_id UUID,
    machine_id   UUID,
    description  VARCHAR(2000) NOT NULL,
    photo_name   VARCHAR(255),
    reported_by  VARCHAR(254)  NOT NULL,
    reported_at  TIMESTAMPTZ   NOT NULL,
    CONSTRAINT pk_fault_reports PRIMARY KEY (id),
    CONSTRAINT fk_fault_reports_equipment FOREIGN KEY (equipment_id) REFERENCES equipment_items (id),
    CONSTRAINT fk_fault_reports_machine FOREIGN KEY (machine_id) REFERENCES machines (id),
    CONSTRAINT ck_fault_reports_kind CHECK (kind IN ('FAULT', 'SERVICE')),
    CONSTRAINT ck_fault_reports_subject CHECK ((equipment_id IS NULL) <> (machine_id IS NULL)),
    CONSTRAINT ck_fault_reports_equipment_fault CHECK (equipment_id IS NULL OR kind = 'FAULT'),
    CONSTRAINT ck_fault_reports_description CHECK (length(btrim(description)) > 0)
);

CREATE INDEX ix_fault_reports_equipment ON fault_reports (equipment_id, reported_at DESC) WHERE equipment_id IS NOT NULL;
CREATE INDEX ix_fault_reports_machine ON fault_reports (machine_id, reported_at DESC) WHERE machine_id IS NOT NULL;

-- Loans are shown to the frontend by `code` (the screen prints it as the loan reference).
-- APPROVED is not a stored status: approving puts the loan straight on loan (ACTIVE). OVERDUE is derived on read.
CREATE SEQUENCE equipment_loan_code_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE equipment_loans (
    id                 UUID          NOT NULL,
    code               VARCHAR(40)   NOT NULL,
    equipment_id       UUID          NOT NULL,
    quantity           INTEGER       NOT NULL,
    borrower_name      VARCHAR(200)  NOT NULL,
    borrower_type      VARCHAR(40)   NOT NULL,
    borrower_contact   VARCHAR(200)  NOT NULL,
    date_out           DATE          NOT NULL,
    date_back          DATE          NOT NULL,
    outgoing_condition VARCHAR(1000) NOT NULL DEFAULT '',
    incoming_condition VARCHAR(2000),
    status             VARCHAR(24)   NOT NULL DEFAULT 'PENDING_APPROVAL',
    requested_by       VARCHAR(254)  NOT NULL,
    decided_by         VARCHAR(254),
    decided_at         TIMESTAMPTZ,
    decision_note      VARCHAR(1000),
    returned_by        VARCHAR(254),
    returned_at        TIMESTAMPTZ,
    created_at         TIMESTAMPTZ   NOT NULL,
    updated_at         TIMESTAMPTZ   NOT NULL,
    version            BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT pk_equipment_loans PRIMARY KEY (id),
    CONSTRAINT uq_equipment_loans_code UNIQUE (code),
    CONSTRAINT fk_equipment_loans_equipment FOREIGN KEY (equipment_id) REFERENCES equipment_items (id),
    CONSTRAINT ck_equipment_loans_quantity CHECK (quantity > 0),
    CONSTRAINT ck_equipment_loans_borrower_name CHECK (length(btrim(borrower_name)) > 0),
    CONSTRAINT ck_equipment_loans_borrower_contact CHECK (length(btrim(borrower_contact)) > 0),
    CONSTRAINT ck_equipment_loans_dates CHECK (date_back >= date_out),
    CONSTRAINT ck_equipment_loans_status CHECK (status IN ('PENDING_APPROVAL', 'DECLINED', 'ACTIVE', 'RETURNED')),
    CONSTRAINT ck_equipment_loans_decision CHECK ((status = 'PENDING_APPROVAL') = (decided_at IS NULL)),
    CONSTRAINT ck_equipment_loans_return CHECK (
        (status = 'RETURNED') = (returned_at IS NOT NULL)
        AND (status <> 'RETURNED' OR incoming_condition IS NOT NULL)
    )
);

CREATE INDEX ix_equipment_loans_created ON equipment_loans (created_at DESC, id DESC);
CREATE INDEX ix_equipment_loans_equipment ON equipment_loans (equipment_id, status);

-- Stock: quantities become fractional (the frontend sells fabric by the metre, e.g. 1.5), and items get the
-- columns the screens show. Existing rows keep their values; checks that old rows might not meet start NOT VALID.
ALTER TABLE stock_items
    ALTER COLUMN quantity_on_hand TYPE NUMERIC(12, 2),
    ADD COLUMN unit     VARCHAR(32)    NOT NULL DEFAULT 'pcs',
    ADD COLUMN minimum  NUMERIC(12, 2) NOT NULL DEFAULT 0,
    ADD COLUMN location VARCHAR(255)   NOT NULL DEFAULT '';

ALTER TABLE stock_items
    ADD CONSTRAINT ck_stock_items_minimum CHECK (minimum >= 0),
    ADD CONSTRAINT ck_stock_items_department CHECK (
        department IS NULL OR department IN ('MULTIMEDIA', 'TEXTILE', 'CNC', 'TELECOMS')
    ) NOT VALID;

ALTER TABLE stock_movements
    ALTER COLUMN delta TYPE NUMERIC(12, 2),
    ALTER COLUMN reason TYPE VARCHAR(1000),
    ADD COLUMN movement_type  VARCHAR(24),
    ADD COLUMN balance_after  NUMERIC(12, 2),
    ADD COLUMN reference_id   VARCHAR(64),
    ADD COLUMN reference_code VARCHAR(64);

ALTER TABLE stock_movements
    ADD CONSTRAINT ck_stock_movements_type CHECK (
        movement_type IS NULL OR movement_type IN ('RECEIVED', 'USED_ON_REQUEST', 'USED_ON_ORDER', 'CORRECTION')
    ),
    ADD CONSTRAINT ck_stock_movements_balance CHECK (balance_after IS NULL OR balance_after >= 0),
    ADD CONSTRAINT ck_stock_movements_delta CHECK (delta <> 0) NOT VALID,
    ADD CONSTRAINT ck_stock_movements_recorded CHECK (movement_type IS NOT NULL AND balance_after IS NOT NULL) NOT VALID;

CREATE INDEX ix_stock_items_department_name ON stock_items (department, lower(name));
CREATE INDEX ix_stock_movements_created ON stock_movements (created_at DESC, id DESC);

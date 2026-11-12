CREATE TABLE price_list_items (
    id             UUID         NOT NULL,
    department_id  UUID         NOT NULL,
    service        VARCHAR(200) NOT NULL,
    unit           VARCHAR(20)  NOT NULL,
    price_ugx      BIGINT       NOT NULL,
    effective_from DATE         NOT NULL,
    status         VARCHAR(20)  NOT NULL,
    proposed_by    VARCHAR(254) NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL,
    version        BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_price_list_items PRIMARY KEY (id),
    CONSTRAINT fk_price_list_items_department FOREIGN KEY (department_id) REFERENCES sectors (id),
    CONSTRAINT ck_price_list_items_unit CHECK (unit IN ('PER_SESSION', 'PER_ITEM', 'PER_HOUR', 'PER_DAY')),
    CONSTRAINT ck_price_list_items_status CHECK (status IN ('PROPOSED', 'ACTIVE', 'RETIRED')),
    CONSTRAINT ck_price_list_items_price CHECK (price_ugx > 0),
    CONSTRAINT ck_price_list_items_service CHECK (length(btrim(service)) > 0)
);

CREATE INDEX ix_price_list_items_department_status ON price_list_items (department_id, status);

-- F4: fields the media board needs, the extra detail of department (lab and workshop)
-- bookings, and workshop sign-ups. Bookings themselves stay in `bookings`.

ALTER TABLE media_requests
    ADD COLUMN studio_id UUID,
    ADD COLUMN assignee VARCHAR(200),
    ADD COLUMN scheduled_at TIMESTAMPTZ,
    ADD COLUMN description VARCHAR(2000);

ALTER TABLE media_requests
    ADD CONSTRAINT fk_media_requests_studio FOREIGN KEY (studio_id) REFERENCES facilities (id),
    ADD CONSTRAINT ck_media_requests_assignee CHECK (assignee IS NULL OR length(btrim(assignee)) > 0),
    ADD CONSTRAINT ck_media_requests_description CHECK (description IS NULL OR length(btrim(description)) > 0);

-- The board lists every open request, newest activity first.
CREATE INDEX ix_media_requests_updated ON media_requests (updated_at DESC, id);
CREATE INDEX ix_media_requests_studio ON media_requests (studio_id) WHERE studio_id IS NOT NULL;

CREATE TABLE department_booking_details (
    booking_id UUID NOT NULL,
    department VARCHAR(16) NOT NULL,
    kind VARCHAR(16) NOT NULL,
    booked_by VARCHAR(254) NOT NULL,
    customer_type VARCHAR(64),
    machine_id VARCHAR(64),
    capacity INTEGER,
    cancel_reason VARCHAR(500),
    cancelled_by VARCHAR(254),
    cancelled_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_department_booking_details PRIMARY KEY (booking_id),
    CONSTRAINT fk_department_booking_details_booking
        FOREIGN KEY (booking_id) REFERENCES bookings (id) ON DELETE CASCADE,
    CONSTRAINT ck_department_booking_details_department CHECK (department IN ('TELECOMS', 'TEXTILE')),
    CONSTRAINT ck_department_booking_details_kind CHECK (kind IN ('LAB', 'WORKSHOP', 'TRAINING')),
    CONSTRAINT ck_department_booking_details_kind_matches_department CHECK (
        (department = 'TELECOMS' AND kind = 'LAB')
        OR (department = 'TEXTILE' AND kind IN ('WORKSHOP', 'TRAINING'))
    ),
    CONSTRAINT ck_department_booking_details_capacity CHECK (capacity IS NULL OR capacity > 0),
    CONSTRAINT ck_department_booking_details_capacity_for_training CHECK (capacity IS NULL OR kind = 'TRAINING'),
    CONSTRAINT ck_department_booking_details_booked_by CHECK (length(btrim(booked_by)) > 0),
    CONSTRAINT ck_department_booking_details_cancellation CHECK (
        (cancel_reason IS NULL) = (cancelled_at IS NULL)
    )
);

CREATE INDEX ix_department_booking_details_department ON department_booking_details (department);

-- The department screens look up the invoice behind each booking and registration by source.
CREATE INDEX ix_invoices_source ON invoices (source_type, source_id);

CREATE TABLE booking_attendees (
    id UUID NOT NULL,
    booking_id UUID NOT NULL,
    name VARCHAR(200) NOT NULL,
    attended BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_booking_attendees PRIMARY KEY (id),
    CONSTRAINT fk_booking_attendees_booking
        FOREIGN KEY (booking_id) REFERENCES department_booking_details (booking_id) ON DELETE CASCADE,
    CONSTRAINT ck_booking_attendees_name CHECK (length(btrim(name)) > 0)
);

CREATE INDEX ix_booking_attendees_booking ON booking_attendees (booking_id, created_at, id);

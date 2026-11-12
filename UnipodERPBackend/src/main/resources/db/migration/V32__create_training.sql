CREATE TABLE training_sessions (
    id UUID NOT NULL,
    department_id UUID NOT NULL,
    title VARCHAR(200) NOT NULL,
    facilitator VARCHAR(200) NOT NULL,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    capacity INTEGER NOT NULL,
    venue VARCHAR(200) NOT NULL,
    description VARCHAR(2000) NOT NULL DEFAULT '',
    fee INTEGER NOT NULL DEFAULT 0,
    safety_course BOOLEAN NOT NULL DEFAULT FALSE,
    published BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_training_sessions PRIMARY KEY (id),
    CONSTRAINT fk_training_sessions_department
        FOREIGN KEY (department_id) REFERENCES sectors (id),
    CONSTRAINT ck_training_sessions_title CHECK (length(btrim(title)) > 0),
    CONSTRAINT ck_training_sessions_facilitator CHECK (length(btrim(facilitator)) > 0),
    CONSTRAINT ck_training_sessions_venue CHECK (length(btrim(venue)) > 0),
    CONSTRAINT ck_training_sessions_window CHECK (ends_at > starts_at),
    CONSTRAINT ck_training_sessions_capacity CHECK (capacity > 0),
    CONSTRAINT ck_training_sessions_fee CHECK (fee >= 0)
);

-- Serve the overview ordering (newest first), unfiltered and per department.
CREATE INDEX ix_training_sessions_starts_at ON training_sessions (starts_at DESC);
CREATE INDEX ix_training_sessions_department_starts_at ON training_sessions (department_id, starts_at DESC);

CREATE TABLE training_participants (
    id UUID NOT NULL,
    session_id UUID NOT NULL,
    name VARCHAR(200) NOT NULL,
    reg_number VARCHAR(50) NOT NULL,
    department VARCHAR(200) NOT NULL,
    contact_email VARCHAR(254),
    status VARCHAR(20) NOT NULL,
    attendance VARCHAR(10) NOT NULL DEFAULT 'UNMARKED',
    passed BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0,
    registered_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_training_participants PRIMARY KEY (id),
    CONSTRAINT fk_training_participants_session
        FOREIGN KEY (session_id) REFERENCES training_sessions (id),
    CONSTRAINT ck_training_participants_name CHECK (length(btrim(name)) > 0),
    CONSTRAINT ck_training_participants_reg_number CHECK (length(btrim(reg_number)) > 0),
    CONSTRAINT ck_training_participants_department CHECK (length(btrim(department)) > 0),
    CONSTRAINT ck_training_participants_status CHECK (status IN (
        'PENDING_PAYMENT',
        'CONFIRMED',
        'CANCELLED'
    )),
    CONSTRAINT ck_training_participants_attendance CHECK (attendance IN (
        'PRESENT',
        'ABSENT',
        'EXCUSED',
        'UNMARKED'
    )),
    -- A pass only means something for someone who attended.
    CONSTRAINT ck_training_participants_passed_present CHECK (NOT passed OR attendance = 'PRESENT')
);

-- A registration number holds one seat per session; cancelling frees it. Also serves the seat count.
CREATE UNIQUE INDEX uq_training_participants_session_reg_number
    ON training_participants (session_id, lower(reg_number))
    WHERE status <> 'CANCELLED';

CREATE INDEX ix_training_participants_session_registered_at
    ON training_participants (session_id, registered_at);

CREATE TABLE mentorship_follow_ups (
    id UUID NOT NULL,
    title VARCHAR(200) NOT NULL,
    mentee VARCHAR(200) NOT NULL,
    mentor VARCHAR(200) NOT NULL,
    scheduled_date DATE NOT NULL,
    focus VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING_FOLLOW_UP',
    notes VARCHAR(2000) NOT NULL DEFAULT '',
    priority VARCHAR(10) NOT NULL DEFAULT 'NORMAL',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_mentorship_follow_ups PRIMARY KEY (id),
    CONSTRAINT ck_mentorship_follow_ups_title CHECK (length(btrim(title)) > 0),
    CONSTRAINT ck_mentorship_follow_ups_mentee CHECK (length(btrim(mentee)) > 0),
    CONSTRAINT ck_mentorship_follow_ups_mentor CHECK (length(btrim(mentor)) > 0),
    CONSTRAINT ck_mentorship_follow_ups_focus CHECK (length(btrim(focus)) > 0),
    CONSTRAINT ck_mentorship_follow_ups_status CHECK (status IN (
        'PENDING_FOLLOW_UP',
        'ACTION_REQUIRED',
        'SCHEDULED',
        'COMPLETED'
    )),
    CONSTRAINT ck_mentorship_follow_ups_priority CHECK (priority IN (
        'NORMAL',
        'HIGH',
        'URGENT'
    ))
);

-- Serves the follow-up list ordering.
CREATE INDEX ix_mentorship_follow_ups_scheduled_date ON mentorship_follow_ups (scheduled_date, id);

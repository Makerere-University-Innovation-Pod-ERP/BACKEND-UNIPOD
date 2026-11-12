CREATE TABLE user_profiles (
    user_id            UUID         NOT NULL,
    account_kind       VARCHAR(20)  NOT NULL,
    full_name          VARCHAR(200) NOT NULL,
    display_first_name VARCHAR(80),
    phone              VARCHAR(20)  NOT NULL,
    student_number     VARCHAR(40),
    course             VARCHAR(200),
    university_email   VARCHAR(254),
    organisation_name  VARCHAR(200),
    contact_person     VARCHAR(200),
    mou_reference      VARCHAR(100),
    rules_version      VARCHAR(40)  NOT NULL,
    rules_accepted_at  TIMESTAMPTZ  NOT NULL,
    created_at         TIMESTAMPTZ  NOT NULL,
    updated_at         TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_user_profiles PRIMARY KEY (user_id),
    CONSTRAINT fk_user_profiles_user FOREIGN KEY (user_id) REFERENCES api_users (id) ON DELETE CASCADE,
    CONSTRAINT ck_user_profiles_account_kind CHECK (account_kind IN ('STUDENT', 'PUBLIC', 'ORGANISATION')),
    CONSTRAINT ck_user_profiles_student_details CHECK (
        account_kind <> 'STUDENT'
        OR (student_number IS NOT NULL AND course IS NOT NULL AND university_email IS NOT NULL)
    ),
    CONSTRAINT ck_user_profiles_organisation_details CHECK (
        account_kind <> 'ORGANISATION' OR organisation_name IS NOT NULL
    )
);

CREATE UNIQUE INDEX uq_user_profiles_student_number
    ON user_profiles (upper(student_number))
    WHERE student_number IS NOT NULL;

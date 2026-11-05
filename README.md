# BACKEND-UNIPOD

Backend API for the Makerere Innovation Hub ERP, built to manage core department workflows.

## W1 submission registration

The W1 API contract is in [`openapi.yaml`](./openapi.yaml). `POST /api/submissions`
creates a draft; `POST /api/submissions/{id}/register` records receipt, creates
the case-stage history entry, and allocates an immutable `STU-{year}-{sequence}`
or `GEN-{year}-{sequence}` case code. Sequence allocation is transactional and
safe for concurrent registrations.

Student drafts may be saved before all student details are available, but
registration requires student number, programme, supervisor, and
recommendation-letter status. General-public submissions must not contain
student-only details.

## Authentication configuration

Protected API operations use bearer JWTs from the existing OIDC-compatible
issuer. Configure these values in the deployment environment:

- `JWT_ISSUER_URI`: trusted issuer URL.
- `JWT_AUDIENCE`: audience required for this API.
- `JWT_ROLES_CLAIM`: claim containing a list of role names (defaults to `roles`).
- `JWT_SUBJECT_CLAIM`: claim used as the authenticated user identifier (defaults to `sub`).

Role values are mapped to Spring authorities by adding the `ROLE_` prefix. Intake
creation and registration require `ADMINISTRATOR`, `INNOVATION_COORDINATOR`,
`RECEPTIONIST`, or `FACT_FINDING_OFFICER`. If issuer or audience configuration
is missing, protected API routes fail closed.

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

- `DB_PASS`: required PostgreSQL password. There is no default password.
- `JWT_ISSUER_URI`: trusted issuer URL.
- `JWT_AUDIENCE`: audience required for this API.
- `JWT_ROLES_CLAIM`: claim containing a list of role names (defaults to `roles`).
- `JWT_SUBJECT_CLAIM`: claim used as the authenticated user identifier (defaults to `sub`).
- `CORS_ALLOWED_ORIGINS`: comma-separated exact browser origins allowed to call the API.

Role values are mapped to Spring authorities by adding the `ROLE_` prefix. Intake
creation and registration require `ADMINISTRATOR`, `INNOVATION_COORDINATOR`,
`RECEPTIONIST`, or `FACT_FINDING_OFFICER`. If issuer or audience configuration
is missing, protected API routes fail closed.

Set `SPRING_PROFILES_ACTIVE` only when selecting a profile that exists in the
deployment; the application does not silently activate a development profile.
Database URL and username have local defaults, but credentials must be supplied
through environment variables or a secrets manager.

## API errors

Errors use RFC 9457 `application/problem+json` responses. Client errors include
a safe summary; unexpected server errors return a generic message and a
reference ID for support. Stack traces and internal exception details are
written only to server logs and are never included in the response.

## Testing the API with Postman

1. Start PostgreSQL and create the database configured by `DATABASE_URL` (the
   local default is `makerere_hub_db`). Set `DB_PASS` and, if needed, `DB_USER`
   and `DATABASE_URL` in the environment. Configure `JWT_ISSUER_URI` and
   `JWT_AUDIENCE` to match your identity provider, then run the backend from
   `UnipodERPBackend` with `.\mvnw.cmd spring-boot:run`.
2. Obtain an access token from the configured identity provider for a user with
   one of the intake roles. Do not put real tokens or database credentials in
   the repository or a shared Postman collection.
3. In Postman, create an environment with `baseUrl` set to
   `http://localhost:8080/api` and `accessToken` set to the token. For protected
   requests, open the Authorization tab, select the JWT access-token scheme,
   and enter `{{accessToken}}`.
4. Send `POST {{baseUrl}}/submissions` with `Content-Type: application/json`
   and this body to create a student draft:

   ```json
   {
     "category": "student",
     "title": "Postman test submission",
     "purpose": "Validate submission intake",
     "problemAddressed": "The intake workflow needs verification",
     "studentNumber": "TEST-0001",
     "programme": "Engineering",
     "supervisor": "Test Supervisor",
     "recommendationLetterStatus": "submitted"
   }
   ```

   Expect `201 Created`; save the response `id` as `submissionId`.
5. Send `POST {{baseUrl}}/submissions/{{submissionId}}/register` with the same
   authorization configuration. Expect `200 OK`, stage `RECEIVED`, and a
   generated case code.
   Repeating this request should return `409 Conflict`.
6. Verify safe failures: omit the token for `401`, use a token without an
   allowed role for `403`, add an undeclared JSON property for `400`, and use
   an unknown submission UUID for `404`. Error responses use
   `application/problem+json`; unexpected `500` responses include a support
   reference without a stack trace.

For automated checks, run `.\mvnw.cmd test` from `UnipodERPBackend`. The
database-backed integration tests require a reachable PostgreSQL database and
the configured database credentials.

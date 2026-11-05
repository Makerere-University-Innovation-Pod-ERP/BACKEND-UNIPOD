# BACKEND-UNIPOD

Backend API for the Makerere Innovation Hub ERP, built to manage core department workflows.

## Waluube case workflow

The Waluube API contract is in [`openapi.yaml`](./openapi.yaml).
`POST /api/submissions` creates a draft;
`POST /api/submissions/{id}/register` records receipt, creates
the case-stage history entry, and allocates an immutable `STU-{year}-{sequence}`
or `GEN-{year}-{sequence}` case code. Sequence allocation is transactional and
safe for concurrent registrations.

Student drafts may be saved before all student details are available, but
registration requires student number, programme, supervisor, and
recommendation-letter status. General-public submissions must not contain
student-only details.

The lifecycle is `DRAFT` → `RECEIVED` → `FORWARDED` → `IN_PROGRESS` →
`COMPLETED`. The API rejects skipped and backward transitions. Forwarding
assigns an opaque sector UUID, writes stage history, and inserts a
`submission.forwarded` record in `notification_outbox` in the same transaction.
This backend has no configured notification delivery worker, so the outbox is a
durable integration handoff and does not itself send email or messages.

Reception staff can use `GET /api/queues/reception` for paginated drafts and
counters: `inQueueCount` is the number of drafts, `awaitingActionCount` is the
number of received cases not yet forwarded, and `recentlyUpdatedCount` counts
all cases updated in the last 14 days. `GET /api/submissions` lists all cases;
`GET /api/submissions/mine` filters by the authenticated user's `registeredBy`
identity. Both are paginated (default 25, maximum 100). `GET
/api/submissions/{id}` returns the case record, documents, and stage history.
`GET /api/dashboard/overview` returns total/category/stage counts and the same
14-day recent-update metric.

After registration, forward a case with
`POST /api/submissions/{id}/forward` and `{"sectorId":"<sector-uuid>"}`.
Advance it in order using `POST /api/submissions/{id}/transition` with
`{"stage":"IN_PROGRESS"}` and then `{"stage":"COMPLETED"}`. Add metadata using
`POST /api/submissions/{id}/documents`, for example:

```json
{
  "name": "Project brief",
  "status": "SUBMITTED",
  "fileReference": "storage_object_01J..."
}
```

The `fileReference` may be omitted when no file has been attached yet.
`status` is one of `VERIFIED`, `SUBMITTED`, `ONE_FILE`, or `NOT_REQUIRED`.

The file reference must be a provider-generated URL-safe ID; URLs, filesystem
paths, file bytes, and access tokens are not accepted. The actual file upload
and storage service must be provided separately. The sector UUID is also stored
as an opaque reference because no sector catalog/service exists in this backend.

All case APIs are restricted to Administrator, Innovation Coordinator,
Receptionist, and Fact-Finding Officer. `My Submissions` is based on the
authenticated principal recorded at intake.

The backend repository does not contain the Reception, All Submissions, My
Submissions, Case file, or Overview frontend screens; it provides their API
surfaces only.

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

Before applying the workflow migration to an existing database, check for
registered student submissions that do not meet the new constraint. This
includes older `RECEIVED` cases whose recommendation status was missing; the
previous PostgreSQL `CHECK` constraint could allow those rows because a
`NULL` result passes a check. Correct any existing records using their actual
recommendation-letter status before applying the migration. Do not infer a
status from missing data, and do not edit a migration after it has been
applied:

```sql
SELECT id, recommendation_letter_status
FROM submissions
WHERE stage <> 'DRAFT'
  AND category = 'STUDENT'
  AND (
      student_number IS NULL OR length(btrim(student_number)) = 0
      OR programme IS NULL OR length(btrim(programme)) = 0
      OR supervisor IS NULL OR length(btrim(supervisor)) = 0
      OR recommendation_letter_status IS NULL
      OR recommendation_letter_status NOT IN ('VERIFIED', 'SUBMITTED', 'ONE_FILE', 'NOT_REQUIRED')
  );
```

After correcting any returned rows, rerun the query and confirm it returns no
rows before deploying.

## API errors

Errors use RFC 9457 `application/problem+json` responses. Client errors include
a safe summary; unexpected server errors return a generic message and a
reference ID for support. Stack traces and internal exception details are
written only to server logs and are never included in the response.

## Testing the API with Postman

1. Start PostgreSQL and create the database configured by `DATABASE_URL` (the
   local default is `makerere_hub_db`). Set `DB_PASS` and, if needed, `DB_USER`
   and `DATABASE_URL` in the environment. Configure `JWT_ISSUER_URI` and
   `JWT_AUDIENCE` to match your identity provider. From `UnipodERPBackend`,
   start the backend with `.\mvnw.cmd spring-boot:run` on Windows PowerShell or
   `./mvnw spring-boot:run` on Linux/macOS.
2. Obtain an access token from the configured identity provider for a user with
   one of the intake roles. Do not put real tokens or database credentials in
   the repository or a shared Postman collection.
3. In Postman, create an environment with `baseUrl` set to
   `http://localhost:8080/api` and `accessToken` set to the token. For protected
   requests, open the Authorization tab, choose the built-in bearer
   authorization option, and enter `{{accessToken}}` as the token.
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

For automated checks from `UnipodERPBackend`, run `.\mvnw.cmd test` on Windows
PowerShell or `./mvnw test` on Linux/macOS. The database-backed integration
tests require a reachable PostgreSQL database and the configured credentials.




# BACKEND-UNIPOD

Backend API for the Makerere Innovation Hub ERP, built to manage core department workflows.

## Waluube Case Workflow

The Waluube API contract is documented in [`openapi.yaml`](./openapi.yaml).

### Submission Creation and Registration

* `POST /api/submissions` creates a draft submission.
* `POST /api/submissions/{id}/register` records receipt, creates the case-stage history entry, and allocates an immutable case code.

Case codes follow these formats:

* Student submissions: `STU-{year}-{sequence}`
* General-public submissions: `GEN-{year}-{sequence}`

Sequence allocation is transactional and safe for concurrent registrations.

Student drafts may be saved before all student details are available. However, registration requires:

* Student number.
* Programme.
* Supervisor.
* Recommendation-letter status.

General-public submissions must not contain student-only details.

### Submission Lifecycle

The submission lifecycle follows these stages:

`DRAFT` → `RECEIVED` → `FORWARDED` → `IN_PROGRESS` → `COMPLETED`

The API rejects skipped and backward transitions.

Forwarding a case:

1. Assigns an active sector from the catalog.
2. Records the stage history.
3. Inserts a `submission.forwarded` record, including the sector's notification
   address, into `notification_outbox`.

These operations happen within the same database transaction.

**Notification delivery:** When email delivery is enabled, a scheduled worker
claims outbox events with database leases and `SKIP LOCKED`, sends them through
SMTP, and retries failures with capped exponential backoff. Delivery is
at-least-once: an interruption after SMTP accepts a message but before the
database records success can cause a duplicate. With delivery disabled, events
remain in the outbox for an external consumer.

Delivery attempts are capped at ten. Events that continue to fail are moved to
the dead-letter state for operator review. V6 retires pre-existing forwarded
events that lack the sector recipient fields required by the SMTP worker,
rather than retrying them indefinitely.

### Reception Queue

Reception staff can use:

`GET /api/queues/reception`

This endpoint returns paginated drafts and queue counters:

* `inQueueCount`: Number of draft submissions.
* `awaitingActionCount`: Number of received cases that have not yet been forwarded.
* `recentlyUpdatedCount`: Number of all cases updated within the last 14 days.

### Submission Endpoints

| Method | Endpoint                           | Description                                             |
| ------ | ---------------------------------- | ------------------------------------------------------- |
| GET    | `/api/submissions`                 | Lists all submissions.                                  |
| GET    | `/api/submissions/mine`            | Lists submissions registered by the authenticated user. |
| GET    | `/api/submissions/{id}`            | Returns a submission, documents, and stage history.     |
| POST   | `/api/submissions`                 | Creates a draft submission.                             |
| POST   | `/api/submissions/{id}/register`   | Registers a submission and generates its case code.     |
| POST   | `/api/submissions/{id}/forward`    | Forwards a submission to a sector.                      |
| POST   | `/api/submissions/{id}/transition` | Advances a submission to the next lifecycle stage.      |
| POST   | `/api/submissions/{id}/documents`  | Adds document metadata.                                 |
| GET    | `/api/queues/reception`            | Returns the reception queue and counters.               |
| GET    | `/api/dashboard/overview`          | Returns dashboard statistics.                           |

Both `/api/submissions` and `/api/submissions/mine` are paginated.

Pagination defaults:

* Default page size: 25.
* Maximum page size: 100.

`GET /api/submissions/mine` filters submissions using the authenticated user's `registeredBy` identity.

### Dashboard Overview

`GET /api/dashboard/overview`

Returns:

* Total submission count.
* Submission counts by category.
* Submission counts by lifecycle stage.
* Number of submissions updated within the last 14 days.

### Forwarding a Submission

After registration, forward a case using:

`POST /api/submissions/{id}/forward`

Request body:

```json
{
  "sectorId": "<sector-uuid>"
}
```

Create and manage sectors through the sector catalog. Only active sector IDs are
accepted for forwarding; each sector has a notification email address.

| Method | Endpoint | Description |
| ------ | -------- | ----------- |
| GET | `/api/sectors` | Lists active sectors for intake roles. |
| GET | `/api/sectors/catalog` | Lists all sectors, including inactive ones (Administrator only). |
| POST | `/api/sectors` | Creates a sector (Administrator only). |
| PUT | `/api/sectors/{id}` | Updates or deactivates a sector (Administrator only). The request must include the current `version`; stale updates return `409 Conflict`. |

Forwarding writes an email notification to the outbox in the same transaction
as the stage change. Notification delivery itself is asynchronous.

### Transitioning a Submission

Advance a submission through its lifecycle using:

`POST /api/submissions/{id}/transition`

Example request body for moving to `IN_PROGRESS`:

```json
{
  "stage": "IN_PROGRESS"
}
```

To complete the submission:

```json
{
  "stage": "COMPLETED"
}
```

Transitions must follow the defined lifecycle order.

### Adding Document Metadata

Use:

`POST /api/submissions/{id}/documents`

Example request body:

```json
{
  "name": "Project brief",
  "status": "SUBMITTED",
  "fileReference": "storage_object_01J..."
}
```

The `fileReference` field may be omitted when no file has been attached yet.

Supported document statuses:

* `VERIFIED`
* `SUBMITTED`
* `ONE_FILE`
* `NOT_REQUIRED`

The file reference must be a provider-generated URL-safe identifier.

The following values are not accepted as file references:

* URLs.
* Filesystem paths.
* File bytes.
* Access tokens.

When S3 storage is enabled, the API can upload a file using
`POST /api/submissions/{id}/documents/upload` as `multipart/form-data` fields
`name`, `status`, and `file`. It generates the opaque `fileReference` itself.
Download with `GET /api/submissions/{id}/documents/{documentId}/file`; responses
use the sanitized original filename when available, are forced to
attachment/octet-stream, and include `nosniff`. Storage is disabled by
default and metadata-only document creation remains available.

The configured multipart and S3 object size limits are enforced from
`S3_MAX_FILE_BYTES` (with a separate request envelope limit
`S3_MAX_REQUEST_BYTES`). Upload cleanup is attempted if the corresponding
database transaction rolls back; deletion failures are logged and require
periodic orphan-object reconciliation in the storage provider. The
backend does not scan files for malware; deploy an appropriate scanning and
content-retention policy before accepting untrusted uploads. Requests over the
configured limit return `413 Payload Too Large`. Downloads use a five-minute
async request timeout by default, configurable with `ASYNC_REQUEST_TIMEOUT`.

### API Access Control

All case APIs are restricted to the following roles:

* `ADMINISTRATOR`
* `INNOVATION_COORDINATOR`
* `RECEPTIONIST`
* `FACT_FINDING_OFFICER`

Intake creation and registration require one of these authorized roles.

`My Submissions` uses the authenticated principal recorded at intake.

**Frontend availability:** This backend repository does not contain the Reception, All Submissions, My Submissions, Case File, or Overview frontend screens. It provides the API endpoints required to support these interfaces.

---

## Authentication Configuration

Protected API operations use bearer JWTs issued by the existing OIDC-compatible identity provider.

Configure the following environment variables in the deployment environment.

| Environment Variable   | Description                                                             |
| ---------------------- | ----------------------------------------------------------------------- |
| `DB_PASS`              | Required PostgreSQL password. No default password is provided.          |
| `JWT_ISSUER_URI`       | Trusted JWT issuer URL.                                                 |
| `JWT_AUDIENCE`         | Required audience for this API.                                         |
| `JWT_ROLES_CLAIM`      | JWT claim containing role names. Defaults to `roles`.                   |
| `JWT_SUBJECT_CLAIM`    | JWT claim used as the authenticated user identifier. Defaults to `sub`. |
| `CORS_ALLOWED_ORIGINS` | Comma-separated list of exact browser origins allowed to call the API.  |
| `DATABASE_URL`         | PostgreSQL connection URL.                                              |
| `DB_USER`              | PostgreSQL username.                                                    |
| `NOTIFICATION_EMAIL_ENABLED` | Enables the scheduled SMTP outbox worker; defaults to `false`. |
| `NOTIFICATION_EMAIL_FROM` | Sender address for forwarding notifications. |
| `SMTP_HOST`, `SMTP_PORT` | SMTP server and port (port defaults to `587`). |
| `SMTP_USERNAME`, `SMTP_PASSWORD` | SMTP credentials when required by the provider. |
| `SMTP_AUTH`, `SMTP_STARTTLS` | SMTP authentication and STARTTLS toggles (both default to `true`). |
| `SMTP_STARTTLS_REQUIRED` | Requires STARTTLS when enabled (defaults to `true`). |
| `NOTIFICATION_OUTBOX_POLL_INTERVAL` | Worker delay as a Spring duration (defaults to `PT15S`). |
| `S3_STORAGE_ENABLED` | Enables S3-compatible file upload/download; defaults to `false`. |
| `S3_BUCKET` | Required bucket when S3 is enabled. |
| `AWS_REGION` | Storage region; defaults to `us-east-1`. |
| `S3_ENDPOINT` | Optional S3-compatible endpoint, such as a private object-storage service. |
| `S3_PATH_STYLE_ACCESS` | Enables path-style bucket addressing (defaults to `true`). |
| `S3_MAX_FILE_BYTES` | Maximum stored file size in bytes (defaults to `10485760`). |
| `S3_MAX_REQUEST_BYTES` | Multipart request envelope limit in bytes (defaults to `11534336`). |

Use the AWS SDK default credential provider chain for S3 credentials (for
example, a workload IAM role or `AWS_ACCESS_KEY_ID` and
`AWS_SECRET_ACCESS_KEY`). Do not place credentials in `S3_ENDPOINT` or commit
them to the repository. Configure a real SMTP sender and S3 bucket before
enabling those integrations.

### JWT Role Mapping

Role values are mapped to Spring Security authorities by adding the `ROLE_` prefix.

For example:

| JWT Role                 | Spring Security Authority     |
| ------------------------ | ----------------------------- |
| `ADMINISTRATOR`          | `ROLE_ADMINISTRATOR`          |
| `INNOVATION_COORDINATOR` | `ROLE_INNOVATION_COORDINATOR` |
| `RECEPTIONIST`           | `ROLE_RECEPTIONIST`           |
| `FACT_FINDING_OFFICER`   | `ROLE_FACT_FINDING_OFFICER`   |

If issuer or audience configuration is missing, protected API routes fail closed.

Database URL and username have local defaults, but database credentials must be supplied through environment variables or a secrets manager.

Set `SPRING_PROFILES_ACTIVE` only when selecting a profile that actually exists in the deployment. The application does not silently activate a development profile.

Never commit real database credentials, access tokens, or other secrets to the repository.

---

## Database Migration and Existing Records

Before applying the workflow migration to an existing database, check for registered student submissions that do not satisfy the new database constraints.

This includes older `RECEIVED` cases whose recommendation-letter status was missing.

The previous PostgreSQL `CHECK` constraint could allow those records because a `NULL` result passes a check constraint.

Correct existing records using their actual recommendation-letter status before applying the migration.

Do not infer a recommendation-letter status from missing data.

Do not edit a migration after it has already been applied.

Run the following SQL query:

```sql
SELECT id, recommendation_letter_status
FROM submissions
WHERE stage <> 'DRAFT'
  AND category = 'STUDENT'
  AND (
      student_number IS NULL
      OR length(btrim(student_number)) = 0
      OR programme IS NULL
      OR length(btrim(programme)) = 0
      OR supervisor IS NULL
      OR length(btrim(supervisor)) = 0
      OR recommendation_letter_status IS NULL
      OR recommendation_letter_status NOT IN (
          'VERIFIED',
          'SUBMITTED',
          'ONE_FILE',
          'NOT_REQUIRED'
      )
  );
```

After correcting any returned records:

1. Rerun the query.
2. Confirm that it returns no rows.
3. Verify that the existing records satisfy the new constraints.
4. Apply the workflow migrations, including V5 for the sector catalog and
delivery/storage integration metadata.
5. Deploy the updated backend.

V5 adds a sector foreign key as `NOT VALID` so existing sector UUIDs do not
block migration. Before enabling forwarding on an upgraded database, map each
existing non-null sector UUID to its real sector name and notification address,
then insert the corresponding sector row using that same UUID. Do not invent
sector names or notification addresses. Find any unmapped references after
migration with:

```sql
SELECT DISTINCT s.responsible_sector_id
FROM submissions s
LEFT JOIN sectors sector ON sector.id = s.responsible_sector_id
WHERE s.responsible_sector_id IS NOT NULL
  AND sector.id IS NULL;
```

New and changed sector references are checked by the foreign key. Only active
catalog sectors may be selected when forwarding a case.

---

## API Error Handling

Errors use RFC 9457 `application/problem+json` responses.

Client errors include a safe summary describing the problem.

Unexpected server errors return:

* A generic error message.
* A support reference ID.

Internal exception details, stack traces, and validation internals are never exposed in API responses.

Unexpected exceptions are recorded in the server logs for diagnostic purposes.

The API must not expose sensitive implementation details to clients.

---

## Testing the API with Postman

Follow these steps to test the submission workflow.

### 1. Start PostgreSQL and the Backend

Start PostgreSQL and create the database configured by `DATABASE_URL`.

The local default database is:

`makerere_hub_db`

Configure the required environment variables:

```powershell
$env:DB_PASS="your_database_password"
$env:DB_USER="your_database_username"
$env:DATABASE_URL="jdbc:postgresql://localhost:5432/makerere_hub_db"

$env:JWT_ISSUER_URI="your_identity_provider_issuer"
$env:JWT_AUDIENCE="your_api_audience"
```

From the `UnipodERPBackend` directory, start the backend using Windows PowerShell:

```powershell
.\mvnw.cmd spring-boot:run
```

On Linux/macOS:

```bash
./mvnw spring-boot:run
```

### 2. Obtain an Access Token

Obtain an access token from the configured identity provider.

The authenticated user must have one of the following roles:

* `ADMINISTRATOR`
* `INNOVATION_COORDINATOR`
* `RECEPTIONIST`
* `FACT_FINDING_OFFICER`

Do not put real access tokens or database credentials in the repository or a shared Postman collection.

### 3. Configure Postman

Create a Postman environment containing the following variables:

| Variable       | Value                                   |
| -------------- | --------------------------------------- |
| `baseUrl`      | `http://localhost:8080/api`             |
| `accessToken`  | Your valid access token                 |
| `submissionId` | ID returned when creating a submission. |

For protected requests:

1. Open the Authorization tab.
2. Select the built-in Bearer Token authorization option.
3. Enter `{{accessToken}}` as the token.
4. Ensure the request uses `Content-Type: application/json` when sending JSON.

### 4. Create a Student Draft

Send:

`POST {{baseUrl}}/submissions`

Request headers:

```http
Content-Type: application/json
Authorization: Bearer {{accessToken}}
```

Request body:

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

Expected response:

`201 Created`

Save the returned submission `id` in the Postman environment as:

`submissionId`

### 5. Register the Submission

Send:

`POST {{baseUrl}}/submissions/{{submissionId}}/register`

Use the same authorization configuration.

Expected response:

`200 OK`

The response should contain:

* The registered submission.
* Stage: `RECEIVED`.
* A generated case code.

Repeating this registration request should return:

`409 Conflict`

### 6. Verify Error Handling

Test the following scenarios:

| Test                                           | Expected HTTP Status |
| ---------------------------------------------- | -------------------- |
| Request without an access token                | `401 Unauthorized`   |
| Request with a token lacking an allowed role   | `403 Forbidden`      |
| Request containing an undeclared JSON property | `400 Bad Request`    |
| Request using an unknown submission UUID       | `404 Not Found`      |
| Registering an already registered submission   | `409 Conflict`       |

Error responses must use:

`application/problem+json`

Unexpected `500 Internal Server Error` responses must include a support reference without exposing stack traces or internal exception details.

---

## Running Automated Tests

From the `UnipodERPBackend` directory, run the following command in Windows PowerShell:

```powershell
.\mvnw.cmd test
```

On Linux/macOS:

```bash
./mvnw test
```

Database-backed integration tests require:

* A reachable PostgreSQL database.
* A correctly configured database URL.
* Valid database credentials.
* The required database schema and migrations.

Ensure PostgreSQL is running and the required environment variables are configured before executing the integration tests.

---

## Security and Deployment Notes

1. Never commit database passwords, JWT tokens, or other secrets.
2. Always configure the trusted JWT issuer and audience in deployment.
3. Keep protected API routes secured through Spring Security.
4. Do not expose stack traces or internal exception details to clients.
5. Use exact allowed browser origins when configuring CORS.
6. Validate submission lifecycle transitions on the backend.
7. Preserve transactional consistency during registration and forwarding.
8. Do not modify database migrations that have already been applied.
9. Use a secrets manager or deployment environment variables for sensitive configuration.
10. Ensure that database-backed integration tests pass before deploying changes.

---

**Project:** Makerere Innovation Hub ERP
**Repository:** BACKEND-UNIPOD
**Component:** Backend REST API
**API Contract:** `openapi.yaml`

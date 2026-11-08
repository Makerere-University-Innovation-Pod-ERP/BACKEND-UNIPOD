


# BACKEND-UNIPOD

Backend API for the Makerere Innovation Hub ERP, built to manage core department workflows.

## Backend package layout

Java sources use the existing `org.unipod.unipoderpbackend` base package and
group feature-specific classes inside each application layer:

* `controller.submission`, `service.submission`, `model.submission`,
  `repository.submission`, and `dto.submission` — submission workflow.
* `controller.reception` and `dto.reception` — reception queue.
* `controller.dashboard`, `service.dashboard`, `repository.dashboard`, and
  `dto.dashboard` — composed, role-scoped dashboard overview.
* `controller.sector`, `dto.sector`, `model.sector`, `repository.sector`, and
  `service.sector` — sector catalog and membership.
* `exception.submission`, `exception.sector`, and `exception.storage` contain
  domain-specific errors; `exception` contains shared API error handling.
* `service.storage` and `config.storage` — document storage integrations.
* `model.submission.CaseStage` — the seven W4 case-file stepper stages.
* `config`, `security`, and `util` — application-wide configuration, security,
  and reusable helpers.
* Shared types, such as pagination responses, remain directly in `dto`.

The Spring Boot application class stays at the base package so component and
entity scanning cover each layer.

`config.ClockConfiguration` provides a shared system `Clock` configured for
`Africa/Kampala`, keeping production timestamps timezone-consistent. Tests can
inject a fixed clock when deterministic timestamps are required.

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
| GET    | `/api/submissions/search`           | Exact code search, then title substring search.         |
| GET    | `/api/submissions/mine`            | Lists submissions registered by the authenticated user. |
| GET    | `/api/submissions/{id}`            | Returns a submission, documents, and stage history.     |
| POST   | `/api/submissions`                 | Creates a draft submission.                             |
| POST   | `/api/submissions/{id}/register`   | Registers a submission and generates its case code.     |
| POST   | `/api/submissions/{id}/forward`    | Forwards a submission to a sector.                      |
| POST   | `/api/submissions/{id}/transition` | Advances a submission to the next lifecycle stage.      |
| POST   | `/api/submissions/{id}/documents`  | Adds document metadata.                                 |
| GET    | `/api/queues/reception`            | Returns the reception queue and counters.               |
| GET    | `/api/dashboard/overview`          | Returns dashboard statistics.                           |

### W4 Case File

The case-file endpoints provide the aggregate, seven-stage stepper, outcome,
document status controls, and per-control capability reasons:

| Method | Endpoint | Purpose |
| ------ | -------- | ------- |
| GET | `/api/cases/{id}` | Returns the case aggregate, stage history, stepper, outcome, review panel, activity, and capabilities. |
| GET | `/api/cases/{id}/capabilities` | Returns `enabled` and `reason` for each mutating control. |
| GET | `/api/cases/{id}/transitions/allowed` | Returns valid next stages for the current actor. |
| POST | `/api/cases/{id}/transitions` | Moves to `to_stage`, recording an optional note. Requires the current `version`. |
| PUT | `/api/cases/{id}/outcome` | Saves a decision or progress note. Requires the current `version` and reviewer quorum. |
| PATCH | `/api/cases/{id}/documents/{docId}` | Updates document status. Requires the current case `version`. |

The seven stepper stages are `RECEIVED`, `REGISTERED`, `FACT_FINDING`,
`UNDER_REVIEW`, `REFINEMENT_REQUESTED`, `FURTHER_HANDLING`, and `COMPLETED`.
Stage changes are currently sequential; only Administrators and Innovation
Coordinators may perform them, including moving a case out of Fact-Finding.
The source SRS 8.2 transition matrix was not present in this repository, so the
sequential transition map should be checked against that matrix before treating
it as authoritative.

Administrators and Innovation Coordinators may update outcomes and document
statuses. Other permitted case-file readers receive disabled mutation
capabilities with a reason. Every write includes the current case version;
stale versions return `409 Conflict`. Outcome writes return `409 Conflict`
with `Save stays disabled until the configured reviewer quorum is met` unless
the reviewer-quorum provider is available and reports quorum met.

The Mark reviewer-team/IP-panel/quorum and David activity integrations are not
configured in this backend. Their aggregate sections explicitly report
`NOT_CONFIGURED` and return empty data rather than fabricating panel members or
activity. Replace the default `CaseFileEnrichmentProvider` bean when those
service contracts are available. Until quorum is configured, saving outcomes
is intentionally unavailable.

`/api/submissions`, `/api/submissions/search`, and `/api/submissions/mine` are
paginated. The search endpoint accepts `code`, `page`, and `size`; `page` is
zero-based, defaults to `0`, and `size` defaults to `25` with a maximum of
`100`.

Pagination defaults:

* Default page size: 25.
* Maximum page size: 100.

`GET /api/submissions/mine` filters submissions using the authenticated user's `registeredBy` identity.

### All Submissions and Global Search

`GET /api/submissions` supports the optional `category`, `stage`, `sector`, and
`q` filters. Results are ordered by received date descending, with the
submission ID as a deterministic tie-breaker, and use the same page-size limits
as the other submission lists.

`GET /api/submissions/search?code=...` first checks for an exact visible case
code. If no exact code is visible, it searches visible titles using a
case-insensitive substring match.

The `code` value must not be blank. Literal `%`, `_`, and `\` characters in
title searches are treated as ordinary text, not wildcard operators.

Visibility is enforced in the database query:

* Submitters see submissions they registered.
* Reviewers see submissions assigned to their subject.
* Sector Officers see submissions routed to sectors where they are members.
* Administrators, Coordinators, Receptionists, Fact-Finding Officers,
  IP Liaisons, and Observers see all submissions.

Student Projects and General Innovations use the same list endpoint with the
appropriate `category` filter. Administrator-only endpoints manage reviewer
assignments and Sector Officer membership.

### Dashboard Overview

`GET /api/dashboard/overview`

The response combines Waluube database metrics with the Operations (Fidel),
Finance and audit (David) module tiles. Results are cached for 30 seconds and
workflow aggregates respect each caller's submission visibility scope:

* All authenticated roles may view the dashboard and operational tiles.
* Submitters, reviewers, and sector officers see only workflow cases within
  their existing row-level visibility scope.
* Only Administrators and Innovation Coordinators receive the finance tile.
* Workflow metrics include category totals, awaiting review (Fact-Finding and
  Forwarded), refinement requested, six grouped pipeline counts, and up to five
  oldest open cases with an attention reason.

The Operations, Finance, and audit modules are not connected in this backend.
Their tiles return `NOT_CONFIGURED` with empty metric values until provider
contracts and endpoints are configured; no external values are fabricated.
The six pipeline groups combine Received with Registered, Fact-Finding with
Forwarded, and Under Review with In Progress, followed by Refinement Requested,
Further Handling, and Completed.

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

Intake creation and lifecycle operations are restricted to:

* `ADMINISTRATOR`
* `INNOVATION_COORDINATOR`
* `RECEPTIONIST`
* `FACT_FINDING_OFFICER`

Submission list and global search additionally support:

* `IP_LIAISON`
* `OBSERVER`
* `SUBMITTER`
* `REVIEWER`
* `SECTOR_OFFICER`

These read roles use database-enforced row-level visibility: submitters see
their own cases, reviewers see assigned cases, and sector officers see cases
routed to their sectors. Administrators and the operational roles above see all
cases where their endpoint permission allows it.

### Submitter Home

`GET /api/me/submissions` returns only submissions whose `submitterId` matches
the authenticated subject. `GET /api/me/capabilities` returns the locked
navigation modules and explains that only the four intake roles can register a
submission. Submitters have no write authorities; attempts to create,
register, forward, transition, or modify documents return `403` with the safe
permission envelope.

Administrators, Innovation Coordinators, Receptionists, and Fact-Finding
Officers may set `submitterId` when creating a case for another person.
For backwards compatibility, omitted values default to the registering
principal; the persisted field remains separate from `registeredBy`.

The W3 submitter endpoints are read-only and do not publish workflow commands.
The existing Waluube notification outbox is a PostgreSQL-backed external-consumer
boundary for durable asynchronous delivery. This repository does not provide a
Kafka topic or Kafka consumer; a separate polling or CDC bridge is required
before integrating Kafka.

**Frontend availability:** This backend repository does not contain the Reception, All Submissions, My Submissions, Case File, or Overview frontend screens. It provides the API endpoints required to support these interfaces.

---

## Authentication Configuration

The backend issues short-lived bearer JWTs through `POST /api/auth/login`.
Accounts cannot self-register; the first Administrator is bootstrapped from
Railway secrets, and only Administrators can create additional accounts through
`POST /api/admin/users`. Passwords are stored as PBKDF2 hashes and are never
returned by the API.

Configure the following environment variables in the deployment environment.

| Environment Variable | Description |
| -------------------- | ----------- |
| `JDBC_DATABASE_URL` | PostgreSQL JDBC URL, beginning with `jdbc:postgresql://`. |
| `DB_USER`, `DATABASE_PASSWORD` | PostgreSQL credentials. |
| `JWT_SECRET` | Base64-encoded random secret with at least 32 bytes; required on hosted deployments. |
| `JWT_ISSUER_URI` | Issuer URI embedded in and checked against each JWT. |
| `JWT_AUDIENCE` | Audience required for this API; defaults to `unipod-erp-api`. |
| `JWT_ACCESS_TOKEN_TTL` | Token lifetime as a Spring duration; defaults to `PT15M` and cannot exceed one hour. |
| `BOOTSTRAP_ADMIN_EMAIL` | Email for the first Administrator account. |
| `BOOTSTRAP_ADMIN_PASSWORD` | Initial Administrator password (minimum 12 characters). |
| `CORS_ALLOWED_ORIGINS` | Comma-separated list of exact browser origins allowed to call the API. |
| `JWT_ROLES_CLAIM` | JWT claim containing role names. Defaults to `roles`. |
| `JWT_SUBJECT_CLAIM` | JWT claim used as the authenticated user identifier. Defaults to `sub`. |
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
| `IP_LIAISON`            | `ROLE_IP_LIAISON`              |
| `OBSERVER`              | `ROLE_OBSERVER`                |
| `SUBMITTER`             | `ROLE_SUBMITTER`               |
| `REVIEWER`              | `ROLE_REVIEWER`                |
| `SECTOR_OFFICER`        | `ROLE_SECTOR_OFFICER`          |

The bootstrap account is created only when the user table is empty. Configure
both bootstrap variables together as deployment secrets; later restarts will not
replace existing credentials. If either value is missing, no administrator is
created and the API remains protected.

The API signs tokens with HS256, validates issuer and audience, and protects
all endpoints except login and the health check. A fresh random signing key is
used only for local runs when `JWT_SECRET` is omitted; tokens from such runs
are not valid after restart.

## Render Deployment

The repository-root `Dockerfile` builds the Spring Boot backend using Java 21.
Create a Render Web Service from this repository with the repository root as
the Docker build context and `Dockerfile` as its Dockerfile path. Select the
same region as the PostgreSQL instance; set the health-check path to
`/api/actuator/health`.

The deployed API base URL is `https://backend-unipod.onrender.com/api`.
The OpenAPI `servers` list includes this production URL and the relative `/api`
path for local development.

Configure these variables on the Web Service using Render's private PostgreSQL
connection details (do not use the external database URL):

| Variable | Value |
| -------- | ----- |
| `PORT` | `10000` |
| `JDBC_DATABASE_URL` | `jdbc:postgresql://dpg-dbo6m5nf3r2c73aasfq0-a:5432/unipod_erp` |
| `DB_USER` | Render database username |
| `DATABASE_PASSWORD` | Render database password |
| `JWT_SECRET` | Base64-encoded random secret with at least 32 bytes |
| `JWT_ISSUER_URI` | `https://backend-unipod.onrender.com` |
| `JWT_AUDIENCE` | `unipod-erp-api` |
| `BOOTSTRAP_ADMIN_EMAIL` | Initial Administrator email |
| `BOOTSTRAP_ADMIN_PASSWORD` | Initial password, at least 12 characters |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173,http://localhost:3000` for localhost development |

The database connection above uses the private Render hostname, port `5432`,
and database `unipod_erp`. Keep the database inbound rule private for
Render-to-Render traffic. Never commit database credentials or other secrets
to the repository. The free Render PostgreSQL plan expires; upgrade it before
using it for persistent production data.

Intake creation and lifecycle operations are restricted to Administrators,
Innovation Coordinators, Receptionists, and Fact-Finding Officers. Read-only
submission list and search access additionally supports IP Liaisons, Observers,
Submitters, Reviewers, and Sector Officers, with row-level visibility described
above.

The demonstration catalog and ten demonstration submissions are opt-in. To
enable them for a dedicated demo database, set the PostgreSQL connection option
`app.demo_seed=true`; production connections must not set this option.

Database URL, username, and password must be supplied through environment variables or a secrets manager; the application does not fall back to a local database.

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

Errors use RFC 9457 `application/problem+json` responses. This replaces the
previous custom `ApiErrorResponse` response shape; workflow success responses
are unchanged by the error-format refactor.

Problem responses include `type`, `title`, `status`, and `detail`. Unexpected
server errors also include a safe `reference` support ID.

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

Start PostgreSQL and create the database configured by `JDBC_DATABASE_URL`.

For local development, create and use this database:

`makerere_hub_db`

Configure the required environment variables:

```powershell
$env:DATABASE_PASSWORD="your_database_password"
$env:DB_USER="your_database_username"
$env:JDBC_DATABASE_URL="jdbc:postgresql://localhost:5432/makerere_hub_db"
$env:JWT_ISSUER_URI="http://localhost:8080"
$env:JWT_AUDIENCE="unipod-erp-api"
$env:BOOTSTRAP_ADMIN_EMAIL="admin@example.com"
$env:BOOTSTRAP_ADMIN_PASSWORD="use-a-unique-password-of-at-least-12-characters"
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

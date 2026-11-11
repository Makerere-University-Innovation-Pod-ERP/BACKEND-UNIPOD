# Postman API testing guide

The Postman files in [`postman/`](./postman/) were generated from
[`openapi.yaml`](./openapi.yaml) and contain all 34 documented operations:

- [`Waluube API.postman_collection.json`](./postman/Waluube%20API.postman_collection.json)
- [`Local API.postman_environment.json`](./postman/Local%20API.postman_environment.json)
- [`Render API.postman_environment.json`](./postman/Render%20API.postman_environment.json)

The collection has a request for every OpenAPI operation, sample JSON bodies,
documented response-status checks, and scripts that save tokens and returned
IDs into the selected Postman environment.

## About the access token

There is no static API key to generate or share. This backend uses short-lived
JWT bearer access tokens. A valid token is issued by `POST /api/auth/login`
only after an existing account's credentials are verified. I cannot mint a
valid Render token or provide account credentials; use an authorized account
you control.

The login response contains `accessToken` and `expiresAt`. The collection's
login test script saves these in the selected Postman environment. The token
is a secret: do not paste it into source control, the collection, screenshots,
or a shared environment.

## Import and configure Postman

1. In Postman, choose **Import** and import the collection and one environment
   JSON file from `postman/`.
2. Select **Waluube Local** for a local backend or **Waluube Render** for the
   deployed backend. The base URLs are:
   - Local: `http://localhost:8080/api`
   - Render: `https://backend-unipod.onrender.com/api`
3. Enter `testerEmail` and `testerPassword` in the environment's **Current
   value** fields. Use an account already provisioned on that environment.
   Never enter production credentials in a shared Postman environment.
4. Run **Authentication → POST /auth/login**. On success, the collection saves
   `accessToken` and `tokenExpiresAt` to the selected environment. Other
   protected requests inherit Bearer authorization from the collection.
5. After a `401`, run login again. Tokens are short-lived (15 minutes by
   default).

For local testing, start PostgreSQL and the backend with local credentials and
the application migrations applied. Do not run mutation requests against
production unless you intend to create or change production data.

## Endpoint-by-endpoint checklist

`{id}` in a submission or case route is `{{submissionId}}`; a sector route's
`{id}` is `{{sectorId}}`. The collection updates these variables when the
corresponding create request succeeds. Every request's description includes
its OpenAPI summary and documented success/error status codes; JSON requests
include a valid example body. Replace example values as appropriate.

| # | Request | Required role / conditions | What to check |
|---:|---|---|---|
| 1 | `POST /auth/login` | Any provisioned, enabled account | `200`; response contains `accessToken`, `tokenType`, `expiresAt`; token is saved in the selected environment. |
| 2 | `POST /admin/users` | Administrator | `201`; creates the requested role. Password is never returned. Use the created email and password to log in as that role. |
| 3 | `POST /submissions` | Administrator, Coordinator, Receptionist, Fact-Finding Officer, or Submitter | `201`; stage is `DRAFT`, code is assigned immediately (`STU-...` or `GEN-...`). A Submitter's `submitterId` is taken from the authenticated identity. Intake staff creating a walk-in case may set `submitterId` and `submitterEmail` separately. |
| 4 | `GET /submissions` | Administrator, Coordinator, Receptionist, Fact-Finding Officer, IP Liaison, Observer, Submitter, Reviewer, or Sector Officer | `200`; check pagination and optional `category`, `stage`, `sector`, and `q` filters. Results are scoped to role. |
| 5 | `GET /submissions/search?code=...` | Same roles as the submission list | `200`; exact visible case code wins; otherwise the value is searched in visible titles. Blank `code` should return `400`. |
| 6 | `GET /submissions/mine` | Administrator, Coordinator, Receptionist, or Fact-Finding Officer | `200`; contains cases registered by the signed-in staff identity. |
| 7 | `GET /me/submissions` | Submitter | `200`; contains only cases linked to the signed-in submitter, without staff-only fields. |
| 8 | `GET /me/capabilities` | Submitter | `200`; shows submitter guidance and locked modules; submitters cannot register cases. |
| 9 | `POST /submissions/{id}/register` | Administrator, Coordinator, Receptionist, or Fact-Finding Officer | `200`; DRAFT becomes `RECEIVED`, with the same code assigned at creation. Student cases require student number, programme, supervisor, and recommendation-letter status. Repeating registration returns `409`. |
| 10 | `POST /submissions/{id}/request-information` | Administrator, Coordinator, Receptionist, or Fact-Finding Officer; case must be DRAFT and have a valid submitter email | `200`; note appears on the draft and an information-request outbox event with the recipient email is stored. A missing email returns `400`. |
| 11 | `PUT /submissions/{id}/reviewer` | Administrator | `200`; reviewer assignment is updated. |
| 12 | `GET /submissions/{id}` | Administrator, Coordinator, Receptionist, or Fact-Finding Officer | `200`; check submission, documents, and stage history. |
| 13 | `PUT /submissions/{id}` | Intake roles, or Submitter for their own DRAFT | `200`; code and ownership remain unchanged. A submitter changing another user's case or a non-draft receives `403`. Updating the draft clears an outstanding information request. |
| 14 | `POST /cases/{id}/transitions` | Administrator or Coordinator; current version required | `200` for an allowed transition; verify stage history and incremented version. Stale version returns `409`; invalid transition is rejected. |
| 15 | `GET /cases/{id}` | Administrator, Coordinator, Fact-Finding Officer, Reviewer, Sector Officer, IP Liaison, Submitter, or Observer | `200`; check the seven-step stage view, outcome, history, capabilities, and explicit integration availability. |
| 16 | `GET /cases/{id}/capabilities` | Same roles as case aggregate | `200`; each mutating control includes enabled state and a reason. |
| 17 | `GET /cases/{id}/transitions/allowed` | Same roles as case aggregate | `200`; returns only transitions allowed for the current stage and actor. |
| 18 | `PUT /cases/{id}/outcome` | Administrator or Coordinator; current version and reviewer quorum required | `200` only when quorum is configured and met. With the default unconfigured reviewer integration, expect `409` with the quorum explanation. |
| 19 | `PATCH /cases/{id}/documents/{documentId}` | Administrator or Coordinator; current version required | `200`; status and case version update. Obtain `documentId` from a document-creation response. |
| 20 | `POST /submissions/{id}/forward` | Administrator, Coordinator, Receptionist, or Fact-Finding Officer; active sector required | `200`; a DRAFT is registered and forwarded atomically, or a RECEIVED case is forwarded. Check stage history, assigned sector, and outbox event. |
| 21 | `POST /submissions/{id}/transition` | Administrator or Coordinator | `200` for allowed legacy lifecycle transition; test the current stage rules and confirm invalid/skipped transitions are rejected. |
| 22 | `POST /submissions/{id}/documents` | Intake roles; Submitter may add submitted documents to their own DRAFT | `201`; response includes `documentType`. Submitters cannot mark a document `VERIFIED`. `fileReference` must be an opaque reference, not a URL or path. |
| 23 | `POST /submissions/{id}/documents/upload` | Intake roles; Submitter may upload to their own DRAFT | `201`; set `name`, `status`, `documentType`, and a local file in Postman's form-data. Files are stored in Postgres by default, or in S3 when `S3_STORAGE_ENABLED=true`. |
| 24 | `GET /submissions/{id}/documents/{documentId}/file` | Administrator, Coordinator, Receptionist, or Fact-Finding Officer | `200` attachment with `nosniff`; otherwise check the documented not-found/storage response. |
| 25 | `GET /sectors` | Administrator, Coordinator, Receptionist, or Fact-Finding Officer | `200`; lists active sectors. |
| 26 | `POST /sectors` | Administrator | `201`; use the returned `id` (`{{sectorId}}`) for forwarding and membership tests. |
| 27 | `GET /sectors/catalog` | Administrator | `200`; includes active and inactive sectors. |
| 28 | `GET /sectors/filter-options` | Administrator, Coordinator, Receptionist, Fact-Finding Officer, IP Liaison, Observer, Submitter, Reviewer, or Sector Officer | `200`; returns sector IDs and names. |
| 29 | `PUT /sectors/{id}` | Administrator | `200`; update sector details or deactivate it. |
| 30 | `GET /sectors/{id}/members` | Administrator | `200`; lists sector-officer subjects. |
| 31 | `POST /sectors/{id}/members` | Administrator | `200`; adds the subject supplied in the body. |
| 32 | `DELETE /sectors/{id}/members?subject=...` | Administrator | `204`; membership is removed. |
| 33 | `GET /queues/reception` | Administrator, Coordinator, Receptionist, or Fact-Finding Officer | `200`; check paginated DRAFT queue and the three queue counters. |
| 34 | `GET /dashboard/overview` | Any authenticated role | `200`; workflow metrics are role-scoped. Operations and finance fields are available only to Administrators and Coordinators. |

## Suggested end-to-end sequence

Use the collection's requests and environment variables in this order:

1. Log in as an Administrator. Create a test Receptionist with
   `POST /admin/users`, then create the Submitter with
   `POST /admin/users (Submitter)`. Use unique emails and a strong test
   password; the latter request saves its email as `submitterSubject`.
2. Log in as the Receptionist. Create a sector with `POST /sectors`, then use
   `POST /submissions (walk-in for Submitter)` to create a student draft.
   The request sends `submitterSubject` as both `submitterId` (the account's
   authenticated subject) and `submitterEmail` (the notification recipient).
   The collection saves `sectorId`, `submissionId`, and `submissionCode`.
3. Add a recommendation-letter metadata record with
   `POST /submissions/{{submissionId}}/documents`. The response saves
   `documentId`.
4. Request missing information with
   `POST /submissions/{{submissionId}}/request-information`.
5. Log in as the Submitter that owns the case. For a self-created draft,
   `submitterId` is taken from the authenticated identity. Verify
   `GET /me/submissions`, then update the owned draft with `PUT
   /submissions/{{submissionId}}`. Attempt an update as a different Submitter
   and confirm it is rejected.
6. Log in as the Receptionist again. Either register using
   `POST /submissions/{{submissionId}}/register`, then forward with
   `POST /submissions/{{submissionId}}/forward`, or test the combined behavior
   by forwarding a separate newly created DRAFT directly. Registration can
   happen only once.
7. Inspect `GET /submissions/{{submissionId}}`, the `/cases/{{submissionId}}`
   read endpoints, reception queue, and dashboard. Test case transitions as
   an Administrator or Coordinator using the latest `version`.
8. Test outcomes separately: unless Mark's reviewer-quorum integration is
   configured and quorum is met, the expected result is `409`, not success.
   Upload/download works with the default Postgres storage or with S3 enabled.

## Negative and authorization checks

- Remove or clear `accessToken` and call a protected route: expect `401`.
- Log in as a role that is not permitted for a route: expect `403`.
- Try to create a General submission with student-only fields, use a blank
  required value, or send an unknown JSON property: expect `400`.
- Use an unknown UUID: expect `404`.
- Re-register a received case, use a stale version, or attempt an invalid
  lifecycle transition: expect `409` where documented.
- For submitter checks, confirm a Submitter cannot access another user's
  submission, register/forward a case, mark evidence verified, or write
  outside their own DRAFT.
- Error responses should be `application/problem+json`; they must not contain
  stack traces, passwords, bearer tokens, or database details.

For operations whose documented behavior depends on an external integration
(SMTP, S3, reviewer quorum, facilities, finance, or activity feed), test the
configured integration separately. An explicit `NOT_CONFIGURED` state is not
evidence that the integration is live.

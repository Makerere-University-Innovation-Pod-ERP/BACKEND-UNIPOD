# Parked tests (not compiled)

These tests specified behaviour of the improvement module's own **ask for changes** and **improved version**
endpoints. The team decided to keep main's implementation of those two endpoints (Alvin11David, PR #62:
commits e5ac5f4 and fa1924a), so my implementation was removed on `feat/improvement`. The tests are kept here,
moved with `git mv` so their history is intact, outside `src/test/java` so Maven does not compile them.
Package paths are unchanged.

Main's code that now provides the behaviour:

- **Ask for changes:** `CaseFileController.askRefinement` (`POST /cases/{id}/refinement-requests`), then
  `CaseFileService.askRefinement`, then `service.submission.RefinementRequestService.ask`. Requests are also
  created inside `CaseFileService.transition` (to IMPROVEMENT_REQUESTED) and `CaseFileService.saveOutcome`
  ("Needs more improvement").
- **Improved version:** `CaseFileController.submitImprovedVersion` (`POST /cases/{id}/improved-version`), then
  `SubmissionService.submitImprovedVersion` / `reply`, then `RefinementRequestService.recordResponse` (marks the
  request RESPONDED with `RefinementRequest.respond`, inserts the `case_revisions` row with
  `versionNumber = briefVersion`).
- **Schema:** `V48__create_improvement_tables.sql` (main's rename of my V40, with the composite
  `fk_case_revisions_request_brief`).

## Parked whole files

| File | What it specified | Main's counterpart |
|---|---|---|
| `java/org/unipod/unipoderpbackend/controller/improvement/RefinementRequestApiIntegrationTest.java` | Ask for changes: roles and stages (officer only in IN_FACT_FINDING, coordinator/admin also READY_FOR_REVIEW and IN_REVIEW, adopt in IMPROVEMENT_REQUESTED), field rules after trimming (guidance 1-2000, due date not past, 1-20 changes of 1-500), 422 naming the field, case-version conflicts, server-owned fields ignored, audit `submission.improvement_requested` with structural details only, the submitter email text, no requested-change text on submitter pages, response field names. **Also contains the 3 history tests, copied unchanged to the kept `RefinementHistoryApiIntegrationTest`.** | `CaseFileService.askRefinement`, `RefinementRequestService.ask`; main's tests `RefinementRequestServiceTest`, `CaseFileServiceTest`, `WaluubeApiIntegrationTest.improvementWorkflowCreatesRequestsAcceptsDocumentsAndResubmits` |
| `java/org/unipod/unipoderpbackend/controller/improvement/RefinementRequestAtomicityTest.java` | A failure in the refinement insert, my audit or my notification rolls back Waluube's stage change, history and audit (committed data, delegating repository spies). | One `@Transactional` in `CaseFileService.askRefinement`; main has no committed-data rollback test |
| `java/org/unipod/unipoderpbackend/controller/improvement/RefinementRequestConcurrencyTest.java` | Two simultaneous asks give one 201 and one 409. | `findByIdForUpdate` plus the case version in `CaseFileService.mutableCase`; main has no two-thread test |
| `java/org/unipod/unipoderpbackend/controller/improvement/ImprovementResponseApiIntegrationTest.java` | The improved version: version 2 then 3, brief edits applied through Waluube's reply, conflicts when nothing is waiting, request-version lock token, `whatChanged` rules, Waluube's validation passed through, a non-owner gets the same 404 as an unknown brief before validation, server-owned fields ignored, audit `submission.improved_version_submitted` (entity `improvement`) without answer text, response field names. **Also contains the 7 my-improvement tests, copied unchanged to the kept `MyImprovementApiIntegrationTest`.** The two tests `staffAreForbiddenOnBothEndpoints` and `unauthenticatedAndMalformedRequests` check my-improvement and the removed endpoint in the same method, so they are parked only; the kept file has no role-matrix or 401 test for my-improvement until they are rewritten. | `SubmissionService.submitImprovedVersion`, `RefinementRequestService.recordResponse`; main's tests `SubmissionServiceReplyTest`, `RefinementRequestServiceTest.responseClosesRequestAndRecordsTheImprovedVersion`, `OutboxNotificationDispatcherTest` |
| `java/org/unipod/unipoderpbackend/controller/improvement/ImprovementResponseAtomicityTest.java` | A failure in the revision insert, the request update or my audit rolls back Waluube's reply. | One `@Transactional` in `SubmissionService.reply`; no committed-data test on main |
| `java/org/unipod/unipoderpbackend/controller/improvement/ImprovementResponseConcurrencyTest.java` | Two simultaneous answers give one 201 and one 409. | `findByIdForUpdate` in `SubmissionService.reply`; no two-thread test on main |
| `java/org/unipod/unipoderpbackend/controller/improvement/RevisionIdApiIntegrationTest.java` | `ImprovedVersionResponse.revisionId` equals the stored revision (`improvedVersionResponseCarriesTheStoredRevisionId`). **The other 3 tests were copied unchanged to the kept `RevisionIdReadApiIntegrationTest`.** | Main's improved version returns `SubmissionResponse`, which has no revision id; the id is read from `GET /cases/{id}/refinement-history` |
| `java/org/unipod/unipoderpbackend/service/improvement/RefinementRequestServiceTest.java` | Unit tests of the removed service: unique-cycle and optimistic-lock failures become 409, other violations are rethrown, other roles are refused before any read. | `service.submission.RefinementRequestServiceTest` (main) |
| `java/org/unipod/unipoderpbackend/service/improvement/ImprovementResponseServiceTest.java` | Unit tests of the removed `submit`: revision unique violations become "already answered", optimistic lock, rethrow; staff refused by `submit` and `myImprovement` in one method. | `SubmissionServiceReplyTest` (main) |

## Kept copies (in `src/test/java`, compiled)

Each kept copy is the original file with the removed-behaviour test methods dropped and the class renamed.
Every remaining line is unchanged.

- `controller/improvement/RefinementHistoryApiIntegrationTest.java`: `historyIsEmptyWithoutRequests`,
  `historyListsCyclesInOrderWithTheirChangesAndRevisions`, `historyNeverWrites`.
- `controller/improvement/MyImprovementApiIntegrationTest.java`: the 7 my-improvement tests.
- `controller/improvement/RevisionIdReadApiIntegrationTest.java`:
  `historyAndMyImprovementShowTheStoredIdOnlyWhereARevisionExists`,
  `coordinatorAcceptsUsingTheIdFromRefinementHistory`, `submitterIsStillForbiddenToAcceptWithTheIdTheySee`.
- `controller/improvement/ImprovementTestBodies.java`: the static request-body helpers `body` and `answerBody`,
  moved unchanged from the parked classes.

The kept tests still seed with the request bodies of the removed endpoints (`caseVersion`; `whatChanged` with
`requestVersion`). Against main's handlers those seeds are rejected, so these tests fail until their seeding is
adapted to main's bodies (`version`; `ReplySubmissionRequest.note`). This was left as it is on purpose: the
cleanup step did not change any test behaviour.

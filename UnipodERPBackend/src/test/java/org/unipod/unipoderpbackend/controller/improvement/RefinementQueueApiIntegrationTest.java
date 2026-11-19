package org.unipod.unipoderpbackend.controller.improvement;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.model.factfinding.FactFindingCheck;
import org.unipod.unipoderpbackend.model.factfinding.FactFindingResult;
import org.unipod.unipoderpbackend.model.improvement.RefinementRequest;
import org.unipod.unipoderpbackend.model.improvement.RefinementSource;
import org.unipod.unipoderpbackend.model.improvement.RefinementStatus;
import org.unipod.unipoderpbackend.model.review.Review;
import org.unipod.unipoderpbackend.model.review.ReviewAssignment;
import org.unipod.unipoderpbackend.model.review.ReviewAssignmentStatus;
import org.unipod.unipoderpbackend.model.review.ReviewDecision;
import org.unipod.unipoderpbackend.model.review.ReviewMode;
import org.unipod.unipoderpbackend.model.review.ReviewSettings;
import org.unipod.unipoderpbackend.model.submission.Submission;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;
import org.unipod.unipoderpbackend.repository.factfinding.FactFindingRepository;
import org.unipod.unipoderpbackend.repository.improvement.RefinementRequestRepository;
import org.unipod.unipoderpbackend.repository.review.ReviewAssignmentRepository;
import org.unipod.unipoderpbackend.repository.review.ReviewRepository;
import org.unipod.unipoderpbackend.repository.review.ReviewSettingsRepository;
import org.unipod.unipoderpbackend.repository.submission.SubmissionRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * GET /queues/refinement: the staff queue of briefs being improved. Briefs are seeded with ImprovementTestBriefs
 * and moved through main's real ask and improved-version endpoints and my accept endpoint; a past due date and a
 * legacy brief (IMPROVEMENT_REQUESTED with no refinement row) are seeded through the repositories, because main's
 * endpoints refuse past dates and always record a request.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RefinementQueueApiIntegrationTest {
    private static final String QUEUE = "/queues/refinement";
    private static final String COORDINATOR = "coord@example.test";
    private static final String OWNER = "queue-owner@example.test";
    private static final ZoneId HUB_ZONE = ZoneId.of("Africa/Kampala");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SubmissionRepository submissions;

    @Autowired
    private RefinementRequestRepository requests;

    @Autowired
    private FactFindingRepository factFinding;

    @Autowired
    private ReviewSettingsRepository reviewSettings;

    @Autowired
    private ReviewAssignmentRepository assignments;

    @Autowired
    private ReviewRepository reviews;

    @Autowired
    private org.unipod.unipoderpbackend.repository.improvement.CaseRevisionRepository revisions;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    // ---- 1-5: states through the real workflow ---------------------------------------------

    @Test
    void noMatchingBriefsGiveAnEmptyQueue() throws Exception {
        submissions.saveAndFlush(ImprovementTestBriefs.ownedBy(OWNER, SubmissionStage.IN_REVIEW, "CASE-QE-"));

        queue(coordinator())
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.rows.items").isEmpty())
                .andExpect(jsonPath("$.rows.totalElements").value(0))
                .andExpect(jsonPath("$.rows.page").value(0))
                .andExpect(jsonPath("$.rows.size").value(25));
    }

    @Test
    void anOpenRequestIsWaitingForTheSubmitter() throws Exception {
        Submission brief = inReview();
        ask(brief, "Add a costed budget.", today().plusDays(7));

        queue(coordinator())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows.items", hasSize(1)))
                .andExpect(jsonPath("$.rows.totalElements").value(1))
                .andExpect(jsonPath("$.rows.items[0].briefId").value(brief.getId().toString()))
                .andExpect(jsonPath("$.rows.items[0].title").value("Improvement brief"))
                .andExpect(jsonPath("$.rows.items[0].trackingReference").value(brief.getCode()))
                .andExpect(jsonPath("$.rows.items[0].briefType").value("general"))
                .andExpect(jsonPath("$.rows.items[0].stage").value("IMPROVEMENT_REQUESTED"))
                .andExpect(jsonPath("$.rows.items[0].cycle").value(1))
                .andExpect(jsonPath("$.rows.items[0].source").value("COORDINATOR"))
                .andExpect(jsonPath("$.rows.items[0].dueDate").value(today().plusDays(7).toString()))
                .andExpect(jsonPath("$.rows.items[0].state").value("WAITING_FOR_SUBMITTER"))
                .andExpect(jsonPath("$.rows.items[0].overdue").value(false));
    }

    @Test
    void afterTheSubmitterAnswersTheBriefIsReadyToAccept() throws Exception {
        Submission brief = inReview();
        ask(brief, "Add a costed budget.", today().plusDays(5));
        answer(brief, "Added a budget table.");

        queue(coordinator())
                .andExpect(jsonPath("$.rows.items", hasSize(1)))
                .andExpect(jsonPath("$.rows.items[0].state").value("READY_TO_ACCEPT"))
                .andExpect(jsonPath("$.rows.items[0].stage").value("RESUBMITTED"))
                .andExpect(jsonPath("$.rows.items[0].cycle").value(1))
                .andExpect(jsonPath("$.rows.items[0].source").value("COORDINATOR"))
                .andExpect(jsonPath("$.rows.items[0].dueDate").value(today().plusDays(5).toString()))
                .andExpect(jsonPath("$.rows.items[0].overdue").value(false));
    }

    @Test
    void afterTheCoordinatorAcceptsTheBriefLeavesTheQueue() throws Exception {
        Submission brief = inReview();
        ask(brief, "Add a costed budget.", today().plusDays(5));
        answer(brief, "Added a budget table.");
        accept(brief);

        // Back in IN_REVIEW with an old RESPONDED request: not in the queue (requirement 12 as well).
        queue(coordinator())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows.items").isEmpty())
                .andExpect(jsonPath("$.rows.totalElements").value(0));
    }

    @Test
    void aLegacyBriefNeedsARequestUntilStaffAsk() throws Exception {
        // As in main's legacyImprovementRequestWithoutRefinementRowCanStillBeAnswered: a brief already in
        // IMPROVEMENT_REQUESTED with no refinement row.
        Submission brief = submissions.saveAndFlush(
                ImprovementTestBriefs.ownedBy(OWNER, SubmissionStage.IMPROVEMENT_REQUESTED, "CASE-QL-"));

        queue(coordinator())
                .andExpect(jsonPath("$.rows.items", hasSize(1)))
                .andExpect(jsonPath("$.rows.items[0].briefId").value(brief.getId().toString()))
                .andExpect(jsonPath("$.rows.items[0].state").value("NEEDS_REQUEST"))
                .andExpect(jsonPath("$.rows.items[0].stage").value("IMPROVEMENT_REQUESTED"))
                .andExpect(jsonPath("$.rows.items[0].cycle").value(nullValue()))
                .andExpect(jsonPath("$.rows.items[0].source").value(nullValue()))
                .andExpect(jsonPath("$.rows.items[0].dueDate").value(nullValue()))
                .andExpect(jsonPath("$.rows.items[0].overdue").value(false));

        ask(brief, "Please add the missing data.", today().plusDays(3));

        queue(coordinator())
                .andExpect(jsonPath("$.rows.items", hasSize(1)))
                .andExpect(jsonPath("$.rows.items[0].state").value("WAITING_FOR_SUBMITTER"))
                .andExpect(jsonPath("$.rows.items[0].cycle").value(1));
    }

    // ---- 6: overdue ----------------------------------------------------------------------------

    @Test
    void yesterdayIsOverdueTodayIsNotAndOverdueRowsComeFirst() throws Exception {
        // Due yesterday (past dates only through the repository): overdue.
        Submission late = submissions.saveAndFlush(
                ImprovementTestBriefs.ownedBy(OWNER, SubmissionStage.IMPROVEMENT_REQUESTED, "CASE-QO-"));
        savedRequest(late, 1, RefinementStatus.OPEN, today().minusDays(1));
        // Answered, with an earlier due date, but not overdue (not waiting for the submitter).
        Submission answered = submissions.saveAndFlush(
                ImprovementTestBriefs.ownedBy(OWNER, SubmissionStage.RESUBMITTED, "CASE-QA-"));
        savedRequest(answered, 1, RefinementStatus.RESPONDED, today().minusDays(10));
        // Due today through main's endpoint: not overdue.
        Submission dueToday = inReview();
        ask(dueToday, "Due today.", today());

        queue(coordinator())
                .andExpect(jsonPath("$.rows.items", hasSize(3)))
                .andExpect(jsonPath("$.rows.items[0].briefId").value(late.getId().toString()))
                .andExpect(jsonPath("$.rows.items[0].state").value("WAITING_FOR_SUBMITTER"))
                .andExpect(jsonPath("$.rows.items[0].overdue").value(true))
                .andExpect(jsonPath("$.rows.items[1].briefId").value(answered.getId().toString()))
                .andExpect(jsonPath("$.rows.items[1].state").value("READY_TO_ACCEPT"))
                .andExpect(jsonPath("$.rows.items[1].dueDate").value(today().minusDays(10).toString()))
                .andExpect(jsonPath("$.rows.items[1].overdue").value(false))
                .andExpect(jsonPath("$.rows.items[2].briefId").value(dueToday.getId().toString()))
                .andExpect(jsonPath("$.rows.items[2].dueDate").value(today().toString()))
                .andExpect(jsonPath("$.rows.items[2].overdue").value(false));
    }

    // ---- 7-8: further cycles -----------------------------------------------------------------

    @Test
    void aSecondCycleAfterANewReviewRoundShowsOneRowWithCycleTwo() throws Exception {
        Submission brief = inReview();
        reviewRoundOneFinished(brief);
        ask(brief, "Round one.", today().plusDays(5));
        answer(brief, "Answer one.");
        accept(brief);
        mockMvc.perform(json(post("/cases/{id}/review-rounds", brief.getId()), Map.of(
                        "mode", "PARALLEL", "reviewsNeeded", 1, "settingsVersion", settingsVersion(brief)))
                        .with(coordinator()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.round").value(2));
        ask(brief, "Round two.", today().plusDays(9));

        queue(coordinator())
                .andExpect(jsonPath("$.rows.items", hasSize(1)))
                .andExpect(jsonPath("$.rows.totalElements").value(1))
                .andExpect(jsonPath("$.rows.items[0].state").value("WAITING_FOR_SUBMITTER"))
                .andExpect(jsonPath("$.rows.items[0].cycle").value(2))
                .andExpect(jsonPath("$.rows.items[0].dueDate").value(today().plusDays(9).toString()));
    }

    @Test
    void askingAgainFromResubmittedWaitsForTheSubmitterWithCycleTwo() throws Exception {
        Submission brief = inReview();
        ask(brief, "Round one.", today().plusDays(5));
        answer(brief, "Answer one.");
        ask(brief, "Still missing the letters.", today().plusDays(6));

        queue(coordinator())
                .andExpect(jsonPath("$.rows.items", hasSize(1)))
                .andExpect(jsonPath("$.rows.items[0].state").value("WAITING_FOR_SUBMITTER"))
                .andExpect(jsonPath("$.rows.items[0].stage").value("IMPROVEMENT_REQUESTED"))
                .andExpect(jsonPath("$.rows.items[0].cycle").value(2));
    }

    // ---- 9: paging -----------------------------------------------------------------------------

    @Test
    void twentyFiveBriefsPageAsTenTenAndFive() throws Exception {
        for (int i = 0; i < 25; i++) {
            ask(inReview(), "Brief " + i, today().plusDays(1 + i));
        }
        List<String> seen = new ArrayList<>();
        int[] sizes = {10, 10, 5};
        for (int page = 0; page < 3; page++) {
            JsonNode rows = json(queue(coordinator(), Map.of("page", String.valueOf(page), "size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.rows.totalElements").value(25))
                    .andExpect(jsonPath("$.rows.totalPages").value(3))
                    .andReturn()).get("rows");
            assertEquals(sizes[page], rows.get("items").size());
            rows.get("items").forEach(item -> seen.add(item.get("briefId").asString()));
        }
        assertEquals(25, seen.size());
        assertEquals(25, new HashSet<>(seen).size());

        queue(coordinator(), Map.of("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows.items", hasSize(25)))
                .andExpect(jsonPath("$.rows.size").value(100));
    }

    @ParameterizedTest
    @CsvSource({"page, -1", "size, 0", "size, 101"})
    void outOfRangePagingIsRejectedLikeTheReviewerQueue(String parameter, String value) throws Exception {
        MvcResult reviewers = mockMvc.perform(get("/queues/reviews").param(parameter, value)
                .with(user("reviewer@example.test").roles("REVIEWER"))).andReturn();
        queue(coordinator(), Map.of(parameter, value))
                .andExpect(status().isUnprocessableContent())
                .andExpect(status().is(reviewers.getResponse().getStatus()))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.title").value(json(reviewers).get("title").asString()))
                .andExpect(jsonPath("$.detail").value(json(reviewers).get("detail").asString()))
                .andExpect(jsonPath("$.errors[0].field").value(parameter));
    }

    @ParameterizedTest
    @ValueSource(strings = {"page", "size"})
    void malformedPagingIsABadRequestLikeTheReviewerQueue(String parameter) throws Exception {
        MvcResult reviewers = mockMvc.perform(get("/queues/reviews").param(parameter, "abc")
                .with(user("reviewer@example.test").roles("REVIEWER"))).andReturn();
        queue(coordinator(), Map.of(parameter, "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(status().is(reviewers.getResponse().getStatus()))
                .andExpect(jsonPath("$.title").value("Invalid request"))
                .andExpect(jsonPath("$.title").value(json(reviewers).get("title").asString()))
                .andExpect(jsonPath("$.detail").value(json(reviewers).get("detail").asString()));
    }

    // ---- 10-12 -----------------------------------------------------------------------------

    // (10) trackingReference null: the schema (ck_submission_receipt_state) requires a code in
    // IMPROVEMENT_REQUESTED and RESUBMITTED, so it cannot be seeded here; RefinementQueueServiceTest checks
    // that a null code maps to a null trackingReference.

    @Test
    void factFindingOfficerAndAdministratorCanRead() throws Exception {
        ask(inReview(), "Improve.", today().plusDays(4));
        for (RequestPostProcessor reader : List.of(user("officer@example.test").roles("FACT_FINDING_OFFICER"),
                user("admin@example.test").roles("ADMINISTRATOR"))) {
            queue(reader)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.rows.items", hasSize(1)));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"REVIEWER", "SUBMITTER", "RECEPTIONIST", "SECTOR_OFFICER", "IP_LIAISON", "OBSERVER"})
    void otherRolesAreForbidden(String role) throws Exception {
        queue(user("someone@example.test").roles(role))
                .andExpect(status().isForbidden())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Forbidden"))
                .andExpect(jsonPath("$.detail").value("You do not have permission to perform this action."));
    }

    @Test
    void unauthenticatedIsUnauthorized() throws Exception {
        mockMvc.perform(get(QUEUE))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.detail").value("A valid bearer token is required."));
    }

    @Test
    void briefsInOtherStagesNeverAppear() throws Exception {
        for (SubmissionStage stage : List.of(SubmissionStage.IN_REVIEW, SubmissionStage.READY_FOR_REVIEW,
                SubmissionStage.IN_FACT_FINDING, SubmissionStage.CLOSED)) {
            Submission brief = submissions.saveAndFlush(ImprovementTestBriefs.ownedBy(OWNER, stage, "CASE-QS-"));
            savedRequest(brief, 1, RefinementStatus.OPEN, today().minusDays(2));
            Submission other = submissions.saveAndFlush(ImprovementTestBriefs.ownedBy(OWNER, stage, "CASE-QS-"));
            savedRequest(other, 1, RefinementStatus.RESPONDED, today().plusDays(2));
        }
        // A brief that went through a full cycle and is back in review with its RESPONDED request.
        Submission back = inReview();
        ask(back, "Round one.", today().plusDays(5));
        answer(back, "Answer one.");
        accept(back);

        queue(coordinator())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows.items").isEmpty())
                .andExpect(jsonPath("$.rows.totalElements").value(0));
    }

    // ---- 14-15: contract and leaks -----------------------------------------------------------

    @Test
    void jsonFieldNamesMatchTheRecordsExactly() throws Exception {
        ask(inReview(), "Waiting row.", today().plusDays(3));
        submissions.saveAndFlush(
                ImprovementTestBriefs.ownedBy(OWNER, SubmissionStage.IMPROVEMENT_REQUESTED, "CASE-QK-"));

        JsonNode response = json(queue(coordinator()).andExpect(status().isOk()).andReturn());

        assertEquals(Set.of("rows", "waitingForSubmitter", "overdue", "readyToAccept", "needsRequest"),
                new HashSet<>(response.propertyNames()));
        assertEquals(Set.of("items", "page", "size", "totalElements", "totalPages"),
                new HashSet<>(response.get("rows").propertyNames()));
        JsonNode items = response.get("rows").get("items");
        assertEquals(2, items.size());
        Set<String> rowKeys = Set.of("briefId", "title", "trackingReference", "briefType", "stage", "cycle",
                "source", "dueDate", "state", "overdue", "requestedChanges", "versionsSoFar", "latestRevisionId");
        // Both a WAITING_FOR_SUBMITTER row and a NEEDS_REQUEST row (whose request fields are null) carry every key.
        Set<String> states = new HashSet<>();
        for (JsonNode item : items) {
            assertEquals(rowKeys, new HashSet<>(item.propertyNames()));
            states.add(item.get("state").asString());
        }
        assertEquals(Set.of("WAITING_FOR_SUBMITTER", "NEEDS_REQUEST"), states);
        int changeItems = 0;
        for (JsonNode item : items) {
            for (JsonNode change : item.get("requestedChanges")) {
                assertEquals(Set.of("position", "text"), new HashSet<>(change.propertyNames()));
                changeItems++;
            }
        }
        assertEquals(1, changeItems);
    }

    @Test
    void theQueueCarriesNoStaffTextReviewOrFactFindingDataOrSubmitterEmail() throws Exception {
        String marker = "SECRET-MARKER-xyz";
        String markedOwner = marker + "@example.test";
        Submission brief = submissions.saveAndFlush(
                ImprovementTestBriefs.ownedBy(markedOwner, SubmissionStage.IN_REVIEW, "CASE-QX-"));
        FactFindingCheck check = new FactFindingCheck(brief.getId(), 1, "officer@example.test");
        check.setTitleNote("Fact-finding " + marker);
        check.setEvidenceNote("Evidence " + marker);
        check.complete(FactFindingResult.READY_FOR_REVIEW, Instant.parse("2026-11-10T09:00:00Z"));
        factFinding.saveAndFlush(check);
        reviewSettings.saveAndFlush(new ReviewSettings(brief.getId(), 1, ReviewMode.PARALLEL, 1, null, COORDINATOR));
        ReviewAssignment done = new ReviewAssignment(brief.getId(), 1, "queue-reviewer@example.test", null, 1,
                COORDINATOR);
        done.setStatus(ReviewAssignmentStatus.SUBMITTED);
        done = assignments.saveAndFlush(done);
        reviews.saveAndFlush(new Review(done.getId(), ReviewDecision.CHANGES_NEEDED, "Reviewer " + marker,
                Instant.parse("2026-11-11T09:00:00Z")));
        mockMvc.perform(json(post("/cases/{id}/refinement-requests", brief.getId()),
                        ImprovementTestBodies.body("Guidance " + marker, today().plusDays(4),
                                List.of("Add a costed budget"), caseVersion(brief)))
                        .with(coordinator()))
                .andExpect(status().isCreated());

        String body = queue(coordinator())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows.items", hasSize(1)))
                .andExpect(jsonPath("$.rows.items[0].briefId").value(brief.getId().toString()))
                .andReturn().getResponse().getContentAsString();

        assertFalse(body.contains(marker), body);
        assertFalse(body.toLowerCase().contains(marker.toLowerCase()), body);
        assertFalse(body.contains("requestedBy"), body);
        assertFalse(body.contains(COORDINATOR), body);
        assertFalse(body.contains("CHANGES_NEEDED"), body);
        assertFalse(body.contains("queue-reviewer@example.test"), body);
        assertFalse(body.contains("officer@example.test"), body);
    }

    // ---- counters ----------------------------------------------------------------------------

    @Test
    void theResponseCarriesTheFourCountersForAMixedSet() throws Exception {
        ask(inReview(), "Waiting.", today().plusDays(6));
        Submission late = submissions.saveAndFlush(
                ImprovementTestBriefs.ownedBy(OWNER, SubmissionStage.IMPROVEMENT_REQUESTED, "CASE-QC-"));
        savedRequest(late, 1, RefinementStatus.OPEN, today().minusDays(1));
        Submission answered = inReview();
        ask(answered, "Answer me.", today().plusDays(2));
        answer(answered, "Answered.");
        submissions.saveAndFlush(
                ImprovementTestBriefs.ownedBy(OWNER, SubmissionStage.IMPROVEMENT_REQUESTED, "CASE-QC-"));

        queue(coordinator())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows.totalElements").value(4))
                .andExpect(jsonPath("$.waitingForSubmitter").value(2))
                .andExpect(jsonPath("$.overdue").value(1))
                .andExpect(jsonPath("$.readyToAccept").value(1))
                .andExpect(jsonPath("$.needsRequest").value(1));
    }

    @Test
    void theCountersAreTheSameOnEveryPageAndCoverAllRows() throws Exception {
        for (int i = 0; i < 20; i++) {
            ask(inReview(), "Brief " + i, today().plusDays(1 + i));
        }
        for (int i = 0; i < 3; i++) {
            Submission brief = inReview();
            ask(brief, "Answered " + i, today().plusDays(30 + i));
            answer(brief, "Done " + i);
        }
        for (int i = 0; i < 2; i++) {
            submissions.saveAndFlush(
                    ImprovementTestBriefs.ownedBy(OWNER, SubmissionStage.IMPROVEMENT_REQUESTED, "CASE-QP-"));
        }
        for (int page = 0; page < 3; page++) {
            queue(coordinator(), Map.of("page", String.valueOf(page), "size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.rows.items", hasSize(page < 2 ? 10 : 5)))
                    .andExpect(jsonPath("$.rows.totalElements").value(25))
                    .andExpect(jsonPath("$.waitingForSubmitter").value(20))
                    .andExpect(jsonPath("$.overdue").value(0))
                    .andExpect(jsonPath("$.readyToAccept").value(3))
                    .andExpect(jsonPath("$.needsRequest").value(2));
        }
    }

    @Test
    void anEmptyQueueHasAllCountersZero() throws Exception {
        queue(coordinator())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows.totalElements").value(0))
                .andExpect(jsonPath("$.waitingForSubmitter").value(0))
                .andExpect(jsonPath("$.overdue").value(0))
                .andExpect(jsonPath("$.readyToAccept").value(0))
                .andExpect(jsonPath("$.needsRequest").value(0));
    }

    @Test
    void answeringAndAcceptingMoveTheCounters() throws Exception {
        Submission brief = inReview();
        ask(brief, "Add a costed budget.", today().plusDays(5));
        queue(coordinator())
                .andExpect(jsonPath("$.waitingForSubmitter").value(1))
                .andExpect(jsonPath("$.readyToAccept").value(0));

        answer(brief, "Added a budget table.");
        queue(coordinator())
                .andExpect(jsonPath("$.waitingForSubmitter").value(0))
                .andExpect(jsonPath("$.readyToAccept").value(1));

        accept(brief);
        queue(coordinator())
                .andExpect(jsonPath("$.waitingForSubmitter").value(0))
                .andExpect(jsonPath("$.readyToAccept").value(0))
                .andExpect(jsonPath("$.needsRequest").value(0));
    }

    @Test
    void readingTheQueueWritesNothing() throws Exception {
        ask(inReview(), "Waiting.", today().plusDays(3));
        Submission answered = inReview();
        ask(answered, "Answer me.", today().plusDays(4));
        answer(answered, "Answered.");
        submissions.saveAndFlush(
                ImprovementTestBriefs.ownedBy(OWNER, SubmissionStage.IMPROVEMENT_REQUESTED, "CASE-QW-"));
        entityManager.flush();
        Map<String, Object> before = tableCounts();

        for (RequestPostProcessor reader : List.of(coordinator(),
                user("officer@example.test").roles("FACT_FINDING_OFFICER"),
                user("admin@example.test").roles("ADMINISTRATOR"))) {
            queue(reader).andExpect(status().isOk());
        }
        entityManager.flush();

        assertEquals(before, tableCounts());
    }

    /** Row counts of every table the queue could touch, plus the latest update times of the read tables. */
    private Map<String, Object> tableCounts() {
        return jdbcTemplate.queryForMap("""
                SELECT (SELECT count(*) FROM refinement_requests) AS requests,
                       (SELECT count(*) FROM refinement_requested_changes) AS changes,
                       (SELECT count(*) FROM case_revisions) AS revisions,
                       (SELECT count(*) FROM submissions) AS submissions,
                       (SELECT count(*) FROM audit_log) AS audit,
                       (SELECT count(*) FROM notification_outbox) AS outbox,
                       (SELECT max(updated_at) FROM submissions) AS last_submission_update,
                       (SELECT max(updated_at) FROM refinement_requests) AS last_request_update
                """);
    }

    // ---- requested changes, versions so far, latest revision ---------------------------------

    @Test
    void aWaitingRowShowsItsChangesInOrderAndVersionOne() throws Exception {
        askWith(inReview(), "Improve.", today().plusDays(5), List.of("First change", "Second change", "Third change"));

        queue(coordinator())
                .andExpect(jsonPath("$.rows.items", hasSize(1)))
                .andExpect(jsonPath("$.rows.items[0].state").value("WAITING_FOR_SUBMITTER"))
                .andExpect(jsonPath("$.rows.items[0].requestedChanges", hasSize(3)))
                .andExpect(jsonPath("$.rows.items[0].requestedChanges[0].position").value(1))
                .andExpect(jsonPath("$.rows.items[0].requestedChanges[0].text").value("First change"))
                .andExpect(jsonPath("$.rows.items[0].requestedChanges[1].position").value(2))
                .andExpect(jsonPath("$.rows.items[0].requestedChanges[1].text").value("Second change"))
                .andExpect(jsonPath("$.rows.items[0].requestedChanges[2].position").value(3))
                .andExpect(jsonPath("$.rows.items[0].requestedChanges[2].text").value("Third change"))
                .andExpect(jsonPath("$.rows.items[0].versionsSoFar").value(1))
                .andExpect(jsonPath("$.rows.items[0].latestRevisionId").value(nullValue()));
    }

    @Test
    void anAnsweredRowShowsItsChangesVersionTwoAndTheStoredRevisionId() throws Exception {
        Submission brief = inReview();
        askWith(brief, "Improve.", today().plusDays(5), List.of("Add a budget", "Add letters"));
        answer(brief, "Added both.");
        long stored = revisions.findTopByBriefIdOrderByVersionNumberDesc(brief.getId()).orElseThrow().getId();

        queue(coordinator())
                .andExpect(jsonPath("$.rows.items", hasSize(1)))
                .andExpect(jsonPath("$.rows.items[0].state").value("READY_TO_ACCEPT"))
                .andExpect(jsonPath("$.rows.items[0].requestedChanges[0].text").value("Add a budget"))
                .andExpect(jsonPath("$.rows.items[0].requestedChanges[1].text").value("Add letters"))
                .andExpect(jsonPath("$.rows.items[0].requestedChanges", hasSize(2)))
                .andExpect(jsonPath("$.rows.items[0].versionsSoFar").value(2))
                .andExpect(jsonPath("$.rows.items[0].latestRevisionId").value(stored));
    }

    @Test
    void theLatestRevisionIdFromTheQueueAcceptsTheBrief() throws Exception {
        Submission brief = inReview();
        ask(brief, "Improve.", today().plusDays(5));
        answer(brief, "Improved.");
        long revisionId = json(queue(coordinator()).andExpect(status().isOk()).andReturn())
                .get("rows").get("items").get(0).get("latestRevisionId").asLong();

        mockMvc.perform(json(post("/cases/{id}/revisions/{revisionId}/accept", brief.getId(), revisionId),
                        Map.of("caseVersion", caseVersion(brief))).with(coordinator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revisionId").value(revisionId))
                .andExpect(jsonPath("$.briefStage").value("IN_REVIEW"));
        queue(coordinator()).andExpect(jsonPath("$.rows.items").isEmpty());
    }

    @Test
    void aSecondCycleShowsOnlyItsOwnChangesAndTheFirstCyclesRevision() throws Exception {
        Submission brief = inReview();
        reviewRoundOneFinished(brief);
        askWith(brief, "Round one.", today().plusDays(5), List.of("Cycle one A", "Cycle one B"));
        answer(brief, "Answer one.");
        long firstRevision = revisions.findTopByBriefIdOrderByVersionNumberDesc(brief.getId()).orElseThrow().getId();
        accept(brief);
        mockMvc.perform(json(post("/cases/{id}/review-rounds", brief.getId()), Map.of(
                        "mode", "PARALLEL", "reviewsNeeded", 1, "settingsVersion", settingsVersion(brief)))
                        .with(coordinator()))
                .andExpect(status().isCreated());
        askWith(brief, "Round two.", today().plusDays(9), List.of("Cycle two only"));

        queue(coordinator())
                .andExpect(jsonPath("$.rows.items", hasSize(1)))
                .andExpect(jsonPath("$.rows.items[0].cycle").value(2))
                .andExpect(jsonPath("$.rows.items[0].requestedChanges", hasSize(1)))
                .andExpect(jsonPath("$.rows.items[0].requestedChanges[0].position").value(1))
                .andExpect(jsonPath("$.rows.items[0].requestedChanges[0].text").value("Cycle two only"))
                .andExpect(jsonPath("$.rows.items[0].versionsSoFar").value(2))
                .andExpect(jsonPath("$.rows.items[0].latestRevisionId").value(firstRevision));
    }

    @Test
    void askingAgainFromResubmittedShowsTheNewChangesAndTheEarlierRevision() throws Exception {
        Submission brief = inReview();
        askWith(brief, "Round one.", today().plusDays(5), List.of("Old change"));
        answer(brief, "Answer one.");
        long firstRevision = revisions.findTopByBriefIdOrderByVersionNumberDesc(brief.getId()).orElseThrow().getId();
        askWith(brief, "Round two.", today().plusDays(6), List.of("New change A", "New change B"));

        queue(coordinator())
                .andExpect(jsonPath("$.rows.items", hasSize(1)))
                .andExpect(jsonPath("$.rows.items[0].state").value("WAITING_FOR_SUBMITTER"))
                .andExpect(jsonPath("$.rows.items[0].cycle").value(2))
                .andExpect(jsonPath("$.rows.items[0].requestedChanges", hasSize(2)))
                .andExpect(jsonPath("$.rows.items[0].requestedChanges[0].text").value("New change A"))
                .andExpect(jsonPath("$.rows.items[0].requestedChanges[1].text").value("New change B"))
                .andExpect(jsonPath("$.rows.items[0].versionsSoFar").value(2))
                .andExpect(jsonPath("$.rows.items[0].latestRevisionId").value(firstRevision));
    }

    @Test
    void aLegacyBriefHasNoChangesVersionOneAndNoRevision() throws Exception {
        submissions.saveAndFlush(
                ImprovementTestBriefs.ownedBy(OWNER, SubmissionStage.IMPROVEMENT_REQUESTED, "CASE-QY-"));

        queue(coordinator())
                .andExpect(jsonPath("$.rows.items", hasSize(1)))
                .andExpect(jsonPath("$.rows.items[0].state").value("NEEDS_REQUEST"))
                .andExpect(jsonPath("$.rows.items[0].requestedChanges").isArray())
                .andExpect(jsonPath("$.rows.items[0].requestedChanges").isEmpty())
                .andExpect(jsonPath("$.rows.items[0].versionsSoFar").value(1))
                .andExpect(jsonPath("$.rows.items[0].latestRevisionId").value(nullValue()));
    }

    @Test
    void twentyChangesAreAllReturnedInPositionOrder() throws Exception {
        List<String> twenty = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            twenty.add("Change number " + i);
        }
        askWith(inReview(), "Many changes.", today().plusDays(5), twenty);

        JsonNode changes = json(queue(coordinator()).andExpect(status().isOk()).andReturn())
                .get("rows").get("items").get(0).get("requestedChanges");
        assertEquals(20, changes.size());
        for (int i = 0; i < 20; i++) {
            assertEquals(i + 1, changes.get(i).get("position").asInt());
            assertEquals("Change number " + (i + 1), changes.get(i).get("text").asString());
        }
    }

    @Test
    void requestedChangeTextIsShownButNoOtherStaffTextOrEmail() throws Exception {
        String changeMarker = "CHANGE-MARKER-abc";
        String guidanceMarker = "GUIDANCE-MARKER-def";
        String answerMarker = "WHATCHANGED-MARKER-ghi";
        String reviewMarker = "REVIEW-MARKER-jkl";
        String factFindingMarker = "FACTFINDING-MARKER-mno";
        String markedOwner = "owner-marker-pqr@example.test";
        Submission brief = submissions.saveAndFlush(
                ImprovementTestBriefs.ownedBy(markedOwner, SubmissionStage.IN_REVIEW, "CASE-QM-"));
        FactFindingCheck check = new FactFindingCheck(brief.getId(), 1, "officer@example.test");
        check.setTitleNote("Title " + factFindingMarker);
        check.setEvidenceNote("Evidence " + factFindingMarker);
        check.complete(FactFindingResult.READY_FOR_REVIEW, Instant.parse("2026-11-10T09:00:00Z"));
        factFinding.saveAndFlush(check);
        reviewSettings.saveAndFlush(new ReviewSettings(brief.getId(), 1, ReviewMode.PARALLEL, 1, null, COORDINATOR));
        ReviewAssignment done = new ReviewAssignment(brief.getId(), 1, "queue-reviewer@example.test", null, 1,
                COORDINATOR);
        done.setStatus(ReviewAssignmentStatus.SUBMITTED);
        done = assignments.saveAndFlush(done);
        reviews.saveAndFlush(new Review(done.getId(), ReviewDecision.CHANGES_NEEDED, "Reviewer " + reviewMarker,
                Instant.parse("2026-11-11T09:00:00Z")));
        mockMvc.perform(json(post("/cases/{id}/refinement-requests", brief.getId()),
                        ImprovementTestBodies.body("Guidance " + guidanceMarker, today().plusDays(4),
                                List.of("Change " + changeMarker), caseVersion(brief)))
                        .with(coordinator()))
                .andExpect(status().isCreated());
        mockMvc.perform(json(post("/cases/{id}/improved-version", brief.getId()),
                        ImprovementTestBodies.answerBody("Answer " + answerMarker, null))
                        .with(user(markedOwner).roles("SUBMITTER")))
                .andExpect(status().isCreated());

        String body = queue(coordinator())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows.items", hasSize(1)))
                .andExpect(jsonPath("$.rows.items[0].state").value("READY_TO_ACCEPT"))
                .andExpect(jsonPath("$.rows.items[0].requestedChanges[0].text").value("Change " + changeMarker))
                .andReturn().getResponse().getContentAsString();

        assertEquals(1, body.split(changeMarker, -1).length - 1, body);
        for (String hidden : List.of(guidanceMarker, answerMarker, reviewMarker, factFindingMarker, markedOwner,
                "requestedBy", COORDINATOR, "queue-reviewer@example.test", "officer@example.test", "CHANGES_NEEDED")) {
            assertFalse(body.contains(hidden), hidden + " must not appear: " + body);
        }
    }

    // ---- helpers -----------------------------------------------------------------------------

    private ResultActions queue(RequestPostProcessor caller) throws Exception {
        return queue(caller, Map.of());
    }

    private ResultActions queue(RequestPostProcessor caller, Map<String, String> params) throws Exception {
        entityManager.flush();
        MockHttpServletRequestBuilder request = get(QUEUE).with(caller);
        params.forEach(request::param);
        return mockMvc.perform(request);
    }

    /** An IN_REVIEW brief owned by OWNER with a completed fact-finding check (needed to accept later). */
    private Submission inReview() {
        Submission brief = submissions.saveAndFlush(
                ImprovementTestBriefs.ownedBy(OWNER, SubmissionStage.IN_REVIEW, "CASE-QR-"));
        FactFindingCheck check = new FactFindingCheck(brief.getId(), 1, "officer@example.test");
        check.complete(FactFindingResult.READY_FOR_REVIEW, Instant.parse("2026-11-10T09:00:00Z"));
        factFinding.saveAndFlush(check);
        return brief;
    }

    /** Main's POST /cases/{id}/refinement-requests as the coordinator. */
    private void ask(Submission brief, String guidance, LocalDate dueDate) throws Exception {
        mockMvc.perform(json(post("/cases/{id}/refinement-requests", brief.getId()),
                        ImprovementTestBodies.body(guidance, dueDate, List.of("Change one"), caseVersion(brief)))
                        .with(coordinator()))
                .andExpect(status().isCreated());
    }

    /** Main's POST /cases/{id}/refinement-requests as the coordinator, with the given requested changes. */
    private void askWith(Submission brief, String guidance, LocalDate dueDate, List<String> changes) throws Exception {
        mockMvc.perform(json(post("/cases/{id}/refinement-requests", brief.getId()),
                        ImprovementTestBodies.body(guidance, dueDate, changes, caseVersion(brief)))
                        .with(coordinator()))
                .andExpect(status().isCreated());
    }

    /** Main's POST /cases/{id}/improved-version as the owner. */
    private void answer(Submission brief, String whatChanged) throws Exception {
        mockMvc.perform(json(post("/cases/{id}/improved-version", brief.getId()),
                        ImprovementTestBodies.answerBody(whatChanged, null))
                        .with(user(OWNER).roles("SUBMITTER")))
                .andExpect(status().isCreated());
    }

    /** My accept endpoint, with the latest revision id read from the staff refinement history. */
    private void accept(Submission brief) throws Exception {
        JsonNode history = json(mockMvc.perform(get("/cases/{id}/refinement-history", brief.getId())
                .with(coordinator())).andExpect(status().isOk()).andReturn()).get("requests");
        long revisionId = history.get(history.size() - 1).get("revision").get("id").asLong();
        mockMvc.perform(json(post("/cases/{id}/revisions/{revisionId}/accept", brief.getId(), revisionId),
                        Map.of("caseVersion", caseVersion(brief))).with(coordinator()))
                .andExpect(status().isOk());
    }

    /** Review round 1 with one submitted review, so a new round can be started after the improvement. */
    private void reviewRoundOneFinished(Submission brief) {
        reviewSettings.saveAndFlush(new ReviewSettings(brief.getId(), 1, ReviewMode.PARALLEL, 1, null, COORDINATOR));
        ReviewAssignment done = new ReviewAssignment(brief.getId(), 1, "queue-reviewer@example.test", null, 1,
                COORDINATOR);
        done.setStatus(ReviewAssignmentStatus.SUBMITTED);
        assignments.saveAndFlush(done);
    }

    /** A request saved directly (used for past due dates, which main's endpoint refuses). */
    private void savedRequest(Submission brief, int cycle, RefinementStatus status, LocalDate dueDate) {
        RefinementRequest request = new RefinementRequest(brief.getId(), cycle, RefinementSource.COORDINATOR,
                "Seeded request.", dueDate, COORDINATOR, Instant.parse("2026-11-10T09:00:00Z"));
        if (status == RefinementStatus.RESPONDED) {
            request.setStatus(RefinementStatus.RESPONDED);
            request.setRespondedAt(Instant.parse("2026-11-11T09:00:00Z"));
        }
        requests.saveAndFlush(request);
    }

    private long settingsVersion(Submission brief) throws Exception {
        return json(mockMvc.perform(get("/cases/{id}/reviewers", brief.getId()).with(coordinator()))
                .andExpect(status().isOk()).andReturn()).get("version").asLong();
    }

    private long caseVersion(Submission brief) throws Exception {
        entityManager.flush();
        return json(mockMvc.perform(get("/cases/{id}", brief.getId()).with(coordinator()))
                .andExpect(status().isOk()).andReturn()).get("submission").get("version").asLong();
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, Map<String, ?> body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body));
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private static LocalDate today() {
        return LocalDate.now(HUB_ZONE);
    }

    private static RequestPostProcessor coordinator() {
        return user(COORDINATOR).roles("INNOVATION_COORDINATOR");
    }
}

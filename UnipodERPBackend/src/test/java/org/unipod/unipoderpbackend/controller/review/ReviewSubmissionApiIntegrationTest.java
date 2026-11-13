package org.unipod.unipoderpbackend.controller.review;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.model.auth.ApiUser;
import org.unipod.unipoderpbackend.model.auth.ApiUserRole;
import org.unipod.unipoderpbackend.model.review.ReviewAssignment;
import org.unipod.unipoderpbackend.model.review.ReviewMode;
import org.unipod.unipoderpbackend.model.review.ReviewSettings;
import org.unipod.unipoderpbackend.model.submission.Submission;
import org.unipod.unipoderpbackend.model.submission.SubmissionCategory;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;
import org.unipod.unipoderpbackend.repository.auth.ApiUserRepository;
import org.unipod.unipoderpbackend.repository.review.ReviewAssignmentRepository;
import org.unipod.unipoderpbackend.repository.review.ReviewSettingsRepository;
import org.unipod.unipoderpbackend.repository.submission.SubmissionRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReviewSubmissionApiIntegrationTest {
    private static final UUID UNKNOWN_BRIEF = UUID.fromString("00000000-0000-0000-0000-00000000c0de");
    private static final String COORDINATOR = "coord@example.test";
    private static final String REVIEWER = "reviewer@example.test";
    private static final String SUBMIT = "/cases/{id}/reviews";
    private static final String MY_REVIEW = "/cases/{id}/my-review";
    private static final String SETTINGS = "/cases/{id}/review-settings";
    private static final String REVIEWERS = "/cases/{id}/reviewers";
    private static final String NOT_YOUR_TURN = "It is not your turn yet. A reviewer before you has not submitted.";
    private static final String ALREADY_SUBMITTED = "You have already submitted your review for this brief.";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SubmissionRepository submissions;

    @Autowired
    private ApiUserRepository users;

    @Autowired
    private ReviewSettingsRepository settings;

    @Autowired
    private ReviewAssignmentRepository assignments;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    // ---- submit: success --------------------------------------------------------------

    @Test
    void assignedReviewerSubmitsAndEverythingIsStored() throws Exception {
        Submission brief = briefWithReviewers("PARALLEL", 2, REVIEWER);
        long version = assignmentVersion(brief, REVIEWER);
        Instant before = Instant.now().minusSeconds(5);

        submit(brief, REVIEWER, body("RECOMMENDED", "  Strong, clear brief.  ", version))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.briefId").value(brief.getId().toString()))
                .andExpect(jsonPath("$.round").value(1))
                .andExpect(jsonPath("$.reviewerId").value(REVIEWER))
                .andExpect(jsonPath("$.decision").value("RECOMMENDED"))
                .andExpect(jsonPath("$.comment").value("Strong, clear brief."))
                .andExpect(jsonPath("$.submittedAt").value(notNullValue()))
                .andExpect(jsonPath("$.position").value(1))
                .andExpect(jsonPath("$.quorum.required").value(2))
                .andExpect(jsonPath("$.quorum.completed").value(1))
                .andExpect(jsonPath("$.quorum.met").value(false));

        Map<String, Object> review = reviewRow(brief, REVIEWER);
        assertEquals("RECOMMENDED", review.get("decision"));
        assertEquals("Strong, clear brief.", review.get("comment"));
        Instant submittedAt = ((Timestamp) review.get("submitted_at")).toInstant();
        assertTrue(submittedAt.isAfter(before));
        assertEquals("SUBMITTED", assignmentStatus(brief, REVIEWER));
        assertEquals(1, countReviews(brief));
    }

    @ParameterizedTest
    @ValueSource(strings = {"RECOMMENDED", "CHANGES_NEEDED", "NOT_RECOMMENDED"})
    void everyDecisionValueIsAccepted(String decision) throws Exception {
        Submission brief = briefWithReviewers("PARALLEL", 1, REVIEWER);

        submit(brief, REVIEWER, body(decision, "Reasoned comment.", assignmentVersion(brief, REVIEWER)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.decision").value(decision));
        assertEquals(decision, reviewRow(brief, REVIEWER).get("decision"));
    }

    @Test
    void secondSubmitBySameReviewerIsAConflictAndOnlyOneReviewExists() throws Exception {
        Submission brief = briefWithReviewers("PARALLEL", 1, REVIEWER);
        long version = assignmentVersion(brief, REVIEWER);
        submit(brief, REVIEWER, body("RECOMMENDED", "First.", version)).andExpect(status().isCreated());

        submit(brief, REVIEWER, body("NOT_RECOMMENDED", "Second.", version))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Submission conflict"))
                .andExpect(jsonPath("$.detail").value(ALREADY_SUBMITTED));
        submit(brief, REVIEWER, body("NOT_RECOMMENDED", "Third.", assignmentVersion(brief, REVIEWER)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(ALREADY_SUBMITTED));

        assertEquals(1, countReviews(brief));
        assertEquals("First.", reviewRow(brief, REVIEWER).get("comment"));
    }

    // ---- submit: version and field validation ------------------------------------------

    @Test
    void missingAssignmentVersionIsUnprocessableAndStaleVersionIsAConflict() throws Exception {
        Submission brief = briefWithReviewers("PARALLEL", 1, REVIEWER);
        long version = assignmentVersion(brief, REVIEWER);

        submit(brief, REVIEWER, body("RECOMMENDED", "Fine.", null))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.detail").value(containsString("assignmentVersion")));
        submit(brief, REVIEWER, body("RECOMMENDED", "Fine.", version + 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("changed by another request")));

        assertEquals(0, countReviews(brief));
        assertEquals("NOT_STARTED", assignmentStatus(brief, REVIEWER));
    }

    @Test
    void commentAndDecisionAreValidated() throws Exception {
        Submission brief = briefWithReviewers("PARALLEL", 1, REVIEWER);
        long version = assignmentVersion(brief, REVIEWER);

        submit(brief, REVIEWER, body("RECOMMENDED", "   \t  ", version))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(containsString("comment")));
        submit(brief, REVIEWER, body("RECOMMENDED", null, version))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(containsString("comment")));
        submit(brief, REVIEWER, body("RECOMMENDED", "x".repeat(2001), version))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(containsString("comment")));
        submit(brief, REVIEWER, body(null, "A fine comment.", version))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(containsString("decision")));
        assertEquals(0, countReviews(brief));

        String longest = "y".repeat(2000);
        submit(brief, REVIEWER, body("CHANGES_NEEDED", longest, version))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.comment").value(longest));
        assertEquals(longest, reviewRow(brief, REVIEWER).get("comment"));
    }

    // ---- submit and my-review: not assigned looks like not found -----------------------

    @Test
    void unassignedReviewerGetsExactlyTheSameNotFoundAsAnUnknownBrief() throws Exception {
        Submission brief = briefWithReviewers("PARALLEL", 1, REVIEWER);
        String stranger = "stranger@example.test";

        JsonNode unknownSubmit = json(body(submit(UNKNOWN_BRIEF, stranger, body("RECOMMENDED", "x", 0L))
                .andExpect(status().isNotFound())));
        JsonNode strangerSubmit = json(body(submit(brief.getId(), stranger, body("RECOMMENDED", "x", 0L))
                .andExpect(status().isNotFound())));
        JsonNode unknownRead = json(body(myReview(UNKNOWN_BRIEF, stranger).andExpect(status().isNotFound())));
        JsonNode strangerRead = json(body(myReview(brief.getId(), stranger).andExpect(status().isNotFound())));

        for (JsonNode problem : List.of(unknownSubmit, strangerSubmit, unknownRead, strangerRead)) {
            assertEquals(404, problem.get("status").asInt());
            assertEquals("Submission not found", problem.get("title").asString());
            assertEquals("The requested submission was not found.", problem.get("detail").asString());
        }
        assertEquals(withoutInstance(unknownSubmit), withoutInstance(strangerSubmit));
        assertEquals(withoutInstance(unknownRead), withoutInstance(strangerRead));
        assertEquals(fieldNames(unknownSubmit), fieldNames(strangerSubmit));
        assertEquals(0, countReviews(brief));
    }

    @Test
    void briefWithoutReviewSettingsIsNotFoundForAReviewer() throws Exception {
        Submission brief = underReviewBrief();
        reviewerAccount(REVIEWER);

        submit(brief, REVIEWER, body("RECOMMENDED", "x", 0L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Submission not found"))
                .andExpect(jsonPath("$.detail").value("The requested submission was not found."));
        myReview(brief.getId(), REVIEWER)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("The requested submission was not found."));
    }

    @Test
    void assignmentInAnOlderRoundDoesNotCount() throws Exception {
        Submission brief = briefWithReviewers("PARALLEL", 1, REVIEWER);
        long version = assignmentVersion(brief, REVIEWER);
        settings.saveAndFlush(new ReviewSettings(brief.getId(), 2, ReviewMode.PARALLEL, 1, null, COORDINATOR));

        submit(brief, REVIEWER, body("RECOMMENDED", "Old round.", version))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Submission not found"))
                .andExpect(jsonPath("$.detail").value("The requested submission was not found."));
        myReview(brief.getId(), REVIEWER).andExpect(status().isNotFound());
        assertEquals(0, countReviews(brief));
    }

    @Test
    void briefNotUnderReviewIsAConflict() throws Exception {
        Submission brief = briefInStage(SubmissionStage.IN_FACT_FINDING);
        reviewerAccount(REVIEWER);
        settings.saveAndFlush(new ReviewSettings(brief.getId(), 1, ReviewMode.PARALLEL, 1, null, COORDINATOR));
        ReviewAssignment assignment = assignments.saveAndFlush(
                new ReviewAssignment(brief.getId(), 1, REVIEWER, null, 1, COORDINATOR));

        submit(brief, REVIEWER, body("RECOMMENDED", "Too early.", assignment.getVersion()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Submission conflict"))
                .andExpect(jsonPath("$.detail").value(containsString("under review")));
        assertEquals(0, countReviews(brief));
        assertEquals("IN_FACT_FINDING", storedStage(brief));
    }

    // ---- ordering ----------------------------------------------------------------------

    @Test
    void sequentialModeEnforcesTurnOrder() throws Exception {
        String first = "first@example.test";
        String second = "second@example.test";
        Submission brief = briefWithReviewers("SEQUENTIAL", 2, first, second);

        submit(brief, second, body("RECOMMENDED", "Eager.", assignmentVersion(brief, second)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(NOT_YOUR_TURN));
        assertEquals(0, countReviews(brief));

        submit(brief, first, body("RECOMMENDED", "Done first.", assignmentVersion(brief, first)))
                .andExpect(status().isCreated());
        submit(brief, second, body("CHANGES_NEEDED", "Now my turn.", assignmentVersion(brief, second)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.position").value(2));
        assertEquals(2, countReviews(brief));
    }

    @Test
    void sequentialModeWithAGapInPositions() throws Exception {
        String removed = "removed@example.test";
        String second = "second@example.test";
        String third = "third@example.test";
        Submission brief = briefWithReviewers("SEQUENTIAL", 2, removed, second, third);
        mockMvc.perform(delete(REVIEWERS + "/{reviewerId}", brief.getId(), removed).with(coordinator()))
                .andExpect(status().isOk());

        submit(brief, third, body("RECOMMENDED", "Too soon.", assignmentVersion(brief, third)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(NOT_YOUR_TURN));
        submit(brief, second, body("RECOMMENDED", "First in line.", assignmentVersion(brief, second)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.position").value(2));
        submit(brief, third, body("RECOMMENDED", "After two.", assignmentVersion(brief, third)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.position").value(3));
    }

    @Test
    void parallelModeAllowsAnyOrder() throws Exception {
        String first = "first@example.test";
        String second = "second@example.test";
        String third = "third@example.test";
        Submission brief = briefWithReviewers("PARALLEL", 3, first, second, third);

        for (String reviewer : List.of(third, first, second)) {
            submit(brief, reviewer, body("RECOMMENDED", "Any order.", assignmentVersion(brief, reviewer)))
                    .andExpect(status().isCreated());
        }
        assertEquals(3, countReviews(brief));
    }

    // ---- quorum ------------------------------------------------------------------------

    @Test
    void quorumIsMetAfterTheRequiredNumberOfReviewsAndAgreesWithTheQuorumEndpoint() throws Exception {
        String first = "first@example.test";
        String second = "second@example.test";
        Submission brief = briefWithReviewers("PARALLEL", 2, first, second);

        submit(brief, first, body("RECOMMENDED", "One.", assignmentVersion(brief, first)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quorum.required").value(2))
                .andExpect(jsonPath("$.quorum.completed").value(1))
                .andExpect(jsonPath("$.quorum.met").value(false));
        quorumEndpoint(brief)
                .andExpect(jsonPath("$.completed").value(1))
                .andExpect(jsonPath("$.met").value(false));

        submit(brief, second, body("NOT_RECOMMENDED", "Two.", assignmentVersion(brief, second)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quorum.required").value(2))
                .andExpect(jsonPath("$.quorum.completed").value(2))
                .andExpect(jsonPath("$.quorum.met").value(true));
        quorumEndpoint(brief)
                .andExpect(jsonPath("$.required").value(2))
                .andExpect(jsonPath("$.completed").value(2))
                .andExpect(jsonPath("$.met").value(true));
    }

    // ---- identity ----------------------------------------------------------------------

    @Test
    void tokenEmailWithDifferentLetterCaseStillMatchesTheAssignment() throws Exception {
        Submission brief = briefWithReviewers("PARALLEL", 1, REVIEWER);
        String shouted = "Reviewer@Example.TEST";

        myReview(brief.getId(), shouted)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NOT_STARTED"));
        submit(brief, shouted, body("RECOMMENDED", "Case-insensitive.", assignmentVersion(brief, REVIEWER)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reviewerId").value(REVIEWER));
        assertEquals("SUBMITTED", assignmentStatus(brief, REVIEWER));
    }

    // ---- security and request shape ---------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {
            "INNOVATION_COORDINATOR", "ADMINISTRATOR", "FACT_FINDING_OFFICER", "SUBMITTER",
            "RECEPTIONIST", "SECTOR_OFFICER", "IP_LIAISON", "OBSERVER"
    })
    void otherRolesAreForbidden(String role) throws Exception {
        Submission brief = briefWithReviewers("PARALLEL", 1, REVIEWER);
        long version = assignmentVersion(brief, REVIEWER);
        RequestPostProcessor other = user(REVIEWER).roles(role);

        for (MockHttpServletRequestBuilder request : List.of(
                withBody(post(SUBMIT, brief.getId()), body("RECOMMENDED", "Not mine to give.", version)),
                get(MY_REVIEW, brief.getId()))) {
            mockMvc.perform(request.with(other))
                    .andExpect(status().isForbidden())
                    .andExpect(header().string("WWW-Authenticate", "Bearer"))
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.title").value("Forbidden"))
                    .andExpect(jsonPath("$.detail").value("You do not have permission to perform this action."));
        }
        assertEquals(0, countReviews(brief));
        assertEquals("NOT_STARTED", assignmentStatus(brief, REVIEWER));
    }

    @Test
    void unauthenticatedRequestsAreUnauthorized() throws Exception {
        for (MockHttpServletRequestBuilder request : List.of(
                withBody(post(SUBMIT, UNKNOWN_BRIEF), body("RECOMMENDED", "x", 0L)),
                get(MY_REVIEW, UNKNOWN_BRIEF))) {
            mockMvc.perform(request)
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string("WWW-Authenticate", "Bearer"))
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.detail").value("A valid bearer token is required."));
        }
    }

    @Test
    void malformedBriefIdIsBadRequest() throws Exception {
        for (MockHttpServletRequestBuilder request : List.of(
                withBody(post("/cases/not-a-uuid/reviews"), body("RECOMMENDED", "x", 0L)),
                get("/cases/not-a-uuid/my-review"))) {
            mockMvc.perform(request.with(reviewer(REVIEWER)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Invalid request"))
                    .andExpect(jsonPath("$.detail").value("A request parameter has an invalid value."));
        }
    }

    @Test
    void malformedJsonBodyIsBadRequest() throws Exception {
        Submission brief = briefWithReviewers("PARALLEL", 1, REVIEWER);

        mockMvc.perform(post(SUBMIT, brief.getId()).with(reviewer(REVIEWER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\": \"RECOMMENDED\", "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Malformed request body"));
        submit(brief, REVIEWER, body("MAYBE", "Unknown decision.", assignmentVersion(brief, REVIEWER)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Malformed request body"));
        assertEquals(0, countReviews(brief));
    }

    @Test
    void serverOwnedFieldsInTheBodyAreIgnored() throws Exception {
        Submission brief = briefWithReviewers("PARALLEL", 1, REVIEWER);
        Map<String, Object> request = body("RECOMMENDED", "Honest review.", assignmentVersion(brief, REVIEWER));
        request.put("reviewerId", "attacker@example.test");
        request.put("round", 9);
        request.put("submittedAt", "2000-01-01T00:00:00Z");
        request.put("status", "NOT_STARTED");

        submit(brief, REVIEWER, request)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reviewerId").value(REVIEWER))
                .andExpect(jsonPath("$.round").value(1))
                .andExpect(jsonPath("$.submittedAt").value(not(containsString("2000-01-01"))));

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT reviewer_id, round, status FROM review_assignments WHERE brief_id = ?", brief.getId());
        assertEquals(Map.of("reviewer_id", REVIEWER, "round", 1, "status", "SUBMITTED"), row);
        Instant submittedAt = ((Timestamp) reviewRow(brief, REVIEWER).get("submitted_at")).toInstant();
        assertTrue(submittedAt.isAfter(Instant.parse("2026-01-01T00:00:00Z")));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM review_assignments WHERE reviewer_id = 'attacker@example.test'", Integer.class));
    }

    // ---- side effects ------------------------------------------------------------------

    @Test
    void submissionStageIsUnchangedAfterASubmit() throws Exception {
        Submission brief = briefWithReviewers("PARALLEL", 1, REVIEWER);
        int historyBefore = historyCount(brief);

        submit(brief, REVIEWER, body("RECOMMENDED", "Quorum reached.", assignmentVersion(brief, REVIEWER)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quorum.met").value(true));

        assertEquals("IN_REVIEW", storedStage(brief));
        assertEquals(historyBefore, historyCount(brief));
    }

    @Test
    void coordinatorPanelShowsDecisionAndTimeButNeverTheComment() throws Exception {
        String secretComment = "Confidential reasoning 7f3a.";
        Submission brief = briefWithReviewers("PARALLEL", 2, REVIEWER, "other@example.test");

        String submitted = body(submit(brief, REVIEWER,
                body("CHANGES_NEEDED", secretComment, assignmentVersion(brief, REVIEWER)))
                .andExpect(status().isCreated()));
        String submittedAt = json(submitted).get("submittedAt").asString();

        String panel = body(mockMvc.perform(get(REVIEWERS, brief.getId()).with(coordinator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewers[0].reviewerId").value(REVIEWER))
                .andExpect(jsonPath("$.reviewers[0].status").value("SUBMITTED"))
                .andExpect(jsonPath("$.reviewers[0].decision").value("CHANGES_NEEDED"))
                .andExpect(jsonPath("$.reviewers[0].submittedAt").value(submittedAt))
                .andExpect(jsonPath("$.reviewers[1].decision").value(nullValue())));

        assertFalse(panel.contains(secretComment), panel);
        assertFalse(panel.contains("\"comment\""), panel);
    }

    @Test
    void myReviewShowsOnlyTheCallersOwnAssignment() throws Exception {
        String other = "other-reviewer@example.test";
        Submission brief = briefWithReviewers("SEQUENTIAL", 2, other, REVIEWER);
        long version = assignmentVersion(brief, REVIEWER);

        String before = body(myReview(brief.getId(), REVIEWER)
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.briefId").value(brief.getId().toString()))
                .andExpect(jsonPath("$.round").value(1))
                .andExpect(jsonPath("$.position").value(2))
                .andExpect(jsonPath("$.department").value("Engineering"))
                .andExpect(jsonPath("$.status").value("NOT_STARTED"))
                .andExpect(jsonPath("$.assignmentVersion").value(version))
                .andExpect(jsonPath("$.mode").value("SEQUENTIAL"))
                .andExpect(jsonPath("$.dueDate").value(nullValue()))
                .andExpect(jsonPath("$.decision").value(nullValue()))
                .andExpect(jsonPath("$.comment").value(nullValue()))
                .andExpect(jsonPath("$.submittedAt").value(nullValue())));
        assertFalse(before.contains(other), before);

        submit(brief, other, body("NOT_RECOMMENDED", "Other reviewer's private view.",
                assignmentVersion(brief, other))).andExpect(status().isCreated());
        submit(brief, REVIEWER, body("RECOMMENDED", "My own view.", version)).andExpect(status().isCreated());

        String after = body(myReview(brief.getId(), REVIEWER)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.decision").value("RECOMMENDED"))
                .andExpect(jsonPath("$.comment").value("My own view."))
                .andExpect(jsonPath("$.submittedAt").value(notNullValue())));
        assertFalse(after.contains(other), after);
        assertFalse(after.contains("NOT_RECOMMENDED"), after);
        assertFalse(after.contains("private view"), after);
        assertFalse(json(after).has("quorum"), after);
        assertFalse(json(after).has("reviewers"), after);
        assertNotEquals(version, json(after).get("assignmentVersion").asLong());
    }

    @Test
    void myReviewNeverWrites() throws Exception {
        Submission brief = briefWithReviewers("PARALLEL", 1, REVIEWER);
        Map<String, Object> before = jdbcTemplate.queryForMap(
                "SELECT status, version, updated_at FROM review_assignments WHERE brief_id = ?", brief.getId());

        myReview(brief.getId(), REVIEWER).andExpect(status().isOk());
        myReview(brief.getId(), REVIEWER).andExpect(status().isOk());

        assertEquals(before, jdbcTemplate.queryForMap(
                "SELECT status, version, updated_at FROM review_assignments WHERE brief_id = ?", brief.getId()));
        assertEquals(0, countReviews(brief));
    }

    // ---- helpers -----------------------------------------------------------------------

    private ResultActions submit(Submission brief, String principal, Map<String, Object> body) throws Exception {
        return submit(brief.getId(), principal, body);
    }

    private ResultActions submit(UUID briefId, String principal, Map<String, Object> body) throws Exception {
        return mockMvc.perform(withBody(post(SUBMIT, briefId), body).with(reviewer(principal)));
    }

    private ResultActions myReview(UUID briefId, String principal) throws Exception {
        return mockMvc.perform(get(MY_REVIEW, briefId).with(reviewer(principal)));
    }

    private ResultActions quorumEndpoint(Submission brief) throws Exception {
        return mockMvc.perform(get("/cases/{id}/review-quorum", brief.getId()).with(coordinator()))
                .andExpect(status().isOk());
    }

    private long assignmentVersion(Submission brief, String reviewer) throws Exception {
        return json(body(myReview(brief.getId(), reviewer).andExpect(status().isOk())))
                .get("assignmentVersion").asLong();
    }

    private MockHttpServletRequestBuilder withBody(MockHttpServletRequestBuilder request, Map<String, Object> body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body));
    }

    private static Map<String, Object> body(String decision, String comment, Long assignmentVersion) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("decision", decision);
        body.put("comment", comment);
        body.put("assignmentVersion", assignmentVersion);
        return body;
    }

    private static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }

    private JsonNode json(String body) {
        return objectMapper.readTree(body);
    }

    private JsonNode withoutInstance(JsonNode problem) {
        tools.jackson.databind.node.ObjectNode copy = (tools.jackson.databind.node.ObjectNode) problem.deepCopy();
        copy.remove("instance");
        return copy;
    }

    private static List<String> fieldNames(JsonNode node) {
        return node.propertyNames().stream().sorted().toList();
    }

    private static RequestPostProcessor reviewer(String email) {
        return user(email).roles("REVIEWER");
    }

    private static RequestPostProcessor coordinator() {
        return user(COORDINATOR).roles("INNOVATION_COORDINATOR");
    }

    private void reviewerAccount(String email) {
        users.saveAndFlush(new ApiUser(UUID.randomUUID(), email, "not-a-real-hash", ApiUserRole.REVIEWER,
                Instant.parse("2026-11-01T08:00:00Z")));
    }

    /** An UNDER_REVIEW brief with settings and reviewers created through the coordinator endpoints. */
    private Submission briefWithReviewers(String mode, int reviewsNeeded, String... reviewers) throws Exception {
        Submission brief = underReviewBrief();
        Map<String, Object> settingsBody = new LinkedHashMap<>();
        settingsBody.put("mode", mode);
        settingsBody.put("reviewsNeeded", reviewsNeeded);
        mockMvc.perform(withBody(put(SETTINGS, brief.getId()), settingsBody).with(coordinator()))
                .andExpect(status().isOk());
        for (String reviewer : reviewers) {
            reviewerAccount(reviewer);
            mockMvc.perform(withBody(post(REVIEWERS, brief.getId()),
                            Map.of("reviewerId", reviewer, "department", "Engineering")).with(coordinator()))
                    .andExpect(status().isCreated());
        }
        return brief;
    }

    private Map<String, Object> reviewRow(Submission brief, String reviewer) {
        return jdbcTemplate.queryForMap("""
                SELECT r.decision, r.comment, r.submitted_at
                FROM reviews r JOIN review_assignments a ON a.id = r.assignment_id
                WHERE a.brief_id = ? AND a.reviewer_id = ?
                """, brief.getId(), reviewer);
    }

    private String assignmentStatus(Submission brief, String reviewer) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM review_assignments WHERE brief_id = ? AND reviewer_id = ?",
                String.class, brief.getId(), reviewer);
    }

    private int countReviews(Submission brief) {
        return jdbcTemplate.queryForObject("""
                SELECT count(*) FROM reviews r JOIN review_assignments a ON a.id = r.assignment_id
                WHERE a.brief_id = ?
                """, Integer.class, brief.getId());
    }

    private int historyCount(Submission brief) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM case_stage_history WHERE submission_id = ?", Integer.class, brief.getId());
    }

    private String storedStage(Submission brief) {
        return jdbcTemplate.queryForObject("SELECT stage FROM submissions WHERE id = ?", String.class, brief.getId());
    }

    private Submission underReviewBrief() {
        return briefInStage(SubmissionStage.IN_REVIEW);
    }

    private Submission briefInStage(SubmissionStage target) {
        Submission brief = new Submission(
                UUID.randomUUID(),
                SubmissionCategory.GENERAL,
                "Review brief",
                "Purpose",
                "Problem",
                "receptionist@example.test",
                "submitter@example.test",
                null,
                null,
                null,
                null,
                Instant.parse("2026-11-01T08:00:00Z")
        );
        brief.applyIntakeDetails(null, null, null, null, null, null, "2026-01");
        brief.register("CASE-RS-" + UUID.randomUUID().toString().substring(0, 8),
                Instant.parse("2026-11-01T09:00:00Z"), "receptionist@example.test");
        Instant time = Instant.parse("2026-11-01T10:00:00Z");
        for (SubmissionStage stage : List.of(
                SubmissionStage.FORWARDED, SubmissionStage.IN_FACT_FINDING, SubmissionStage.IN_REVIEW)) {
            brief.transitionTo(stage, time);
            if (stage == target) {
                break;
            }
        }
        return submissions.saveAndFlush(brief);
    }
}

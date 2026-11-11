package org.unipod.unipoderpbackend.controller.review;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
import org.unipod.unipoderpbackend.model.review.Review;
import org.unipod.unipoderpbackend.model.review.ReviewAssignment;
import org.unipod.unipoderpbackend.model.review.ReviewAssignmentStatus;
import org.unipod.unipoderpbackend.model.review.ReviewDecision;
import org.unipod.unipoderpbackend.model.submission.Submission;
import org.unipod.unipoderpbackend.model.submission.SubmissionCategory;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;
import org.unipod.unipoderpbackend.repository.auth.ApiUserRepository;
import org.unipod.unipoderpbackend.repository.review.ReviewAssignmentRepository;
import org.unipod.unipoderpbackend.repository.review.ReviewRepository;
import org.unipod.unipoderpbackend.repository.submission.SubmissionRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReviewCommandApiIntegrationTest {
    private static final UUID UNKNOWN_BRIEF = UUID.fromString("00000000-0000-0000-0000-00000000c0de");
    private static final String COORDINATOR = "coord@example.test";
    private static final String SETTINGS = "/cases/{id}/review-settings";
    private static final String REVIEWERS = "/cases/{id}/reviewers";
    private static final String REVIEWER = "/cases/{id}/reviewers/{reviewerId}";
    private static final ZoneId HUB_ZONE = ZoneId.of("Africa/Kampala");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SubmissionRepository submissions;

    @Autowired
    private ApiUserRepository users;

    @Autowired
    private ReviewAssignmentRepository assignments;

    @Autowired
    private ReviewRepository reviews;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ObjectMapper objectMapper;

    // ---- settings -----------------------------------------------------------------------

    @Test
    void firstSettingsCreateRoundOne() throws Exception {
        Submission brief = underReviewBrief();
        LocalDate due = today().plusDays(14);

        putSettings(brief, settingsBody("SEQUENTIAL", 2, due.toString(), null))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.briefId").value(brief.getId().toString()))
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.round").value(1))
                .andExpect(jsonPath("$.mode").value("SEQUENTIAL"))
                .andExpect(jsonPath("$.reviewsNeeded").value(2))
                .andExpect(jsonPath("$.dueDate").value(due.toString()))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.quorum.required").value(2))
                .andExpect(jsonPath("$.quorum.completed").value(0))
                .andExpect(jsonPath("$.reviewers").isEmpty());

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT round, set_by FROM review_settings WHERE brief_id = ?", brief.getId());
        assertEquals(1, row.get("round"));
        assertEquals(COORDINATOR, row.get("set_by"));
    }

    @Test
    void updateWithCorrectVersionIncrementsVersionAndKeepsOneRow() throws Exception {
        Submission brief = underReviewBrief();
        long version = version(putSettings(brief, settingsBody("PARALLEL", 2, null, null)));

        putSettings(brief, settingsBody("SEQUENTIAL", 3, null, version), "admin@example.test", "ADMINISTRATOR")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("SEQUENTIAL"))
                .andExpect(jsonPath("$.reviewsNeeded").value(3))
                .andExpect(jsonPath("$.version").value(version + 1));

        assertEquals(1, countSettings(brief));
        assertEquals("admin@example.test", jdbcTemplate.queryForObject(
                "SELECT set_by FROM review_settings WHERE brief_id = ?", String.class, brief.getId()));
    }

    @Test
    void staleMissingAndUnexpectedVersionsAreRejected() throws Exception {
        Submission brief = underReviewBrief();
        putSettings(brief, settingsBody("PARALLEL", 2, null, 0L))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value(containsString("version must not be supplied")));
        assertEquals(0, countSettings(brief));

        long version = version(putSettings(brief, settingsBody("PARALLEL", 2, null, null)));
        putSettings(brief, settingsBody("PARALLEL", 3, null, version)).andExpect(status().isOk());

        putSettings(brief, settingsBody("PARALLEL", 4, null, version))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Submission conflict"))
                .andExpect(jsonPath("$.detail").value(containsString("changed by another user")));
        putSettings(brief, settingsBody("PARALLEL", 4, null, null))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(containsString("version is required")));

        assertEquals(3, jdbcTemplate.queryForObject(
                "SELECT reviews_needed FROM review_settings WHERE brief_id = ?", Integer.class, brief.getId()));
    }

    @Test
    void modeIsLockedAfterASubmittedReviewButDueDateCanChange() throws Exception {
        Submission brief = underReviewBrief();
        long version = version(putSettings(brief, settingsBody("PARALLEL", 2, null, null)));
        submittedAssignment(brief, "done@example.test", 1);

        putSettings(brief, settingsBody("SEQUENTIAL", 2, null, version))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("mode cannot be changed")));

        String due = today().plusDays(5).toString();
        putSettings(brief, settingsBody("PARALLEL", 2, due, version))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dueDate").value(due))
                .andExpect(jsonPath("$.mode").value("PARALLEL"));
    }

    @Test
    void reviewsNeededCannotDropBelowSubmittedReviews() throws Exception {
        Submission brief = underReviewBrief();
        long version = version(putSettings(brief, settingsBody("PARALLEL", 3, null, null)));
        submittedAssignment(brief, "one@example.test", 1);
        submittedAssignment(brief, "two@example.test", 2);

        putSettings(brief, settingsBody("PARALLEL", 1, null, version))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("cannot be lower than the 2")));

        putSettings(brief, settingsBody("PARALLEL", 2, null, version))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewsNeeded").value(2))
                .andExpect(jsonPath("$.quorum.met").value(true));
    }

    @Test
    void dueDateMustNotBeInThePast() throws Exception {
        Submission brief = underReviewBrief();

        putSettings(brief, settingsBody("PARALLEL", 1, today().minusDays(1).toString(), null))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.detail").value(containsString("dueDate")));

        long version = version(putSettings(brief, settingsBody("PARALLEL", 1, today().toString(), null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dueDate").value(today().toString())));

        putSettings(brief, settingsBody("PARALLEL", 1, null, version))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dueDate").value(nullValue()));
    }

    @Test
    void reviewsNeededAndModeAreValidated() throws Exception {
        Submission brief = underReviewBrief();

        putSettings(brief, settingsBody("PARALLEL", 0, null, null))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(containsString("reviewsNeeded")));
        putSettings(brief, settingsBody("PARALLEL", 21, null, null))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(containsString("reviewsNeeded")));
        putSettings(brief, settingsBody("PARALLEL", null, null, null))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(containsString("reviewsNeeded")));
        putSettings(brief, settingsBody(null, 2, null, null))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(containsString("mode")));
        putSettings(brief, settingsBody("PARALLEL", 20, null, null)).andExpect(status().isOk());
    }

    @Test
    void settingsRequireABriefUnderReview() throws Exception {
        Submission brief = briefInStage(SubmissionStage.FACT_FINDING);

        putSettings(brief, settingsBody("PARALLEL", 1, null, null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("under review")));
        assertEquals(0, countSettings(brief));
    }

    // ---- assign -------------------------------------------------------------------------

    @Test
    void assigningBeforeSettingsExistIsAConflict() throws Exception {
        Submission brief = underReviewBrief();
        String reviewer = reviewerAccount("r1@example.test");

        assign(brief, reviewer, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Set the review mode and number of reviews first."));
    }

    @Test
    void assigningValidReviewersGivesIncreasingPositions() throws Exception {
        Submission brief = configuredBrief();
        String first = reviewerAccount("first@example.test");
        String second = reviewerAccount("second@example.test");

        assign(brief, first, "Engineering")
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.reviewers.length()").value(1))
                .andExpect(jsonPath("$.reviewers[0].reviewerId").value(first))
                .andExpect(jsonPath("$.reviewers[0].department").value("Engineering"))
                .andExpect(jsonPath("$.reviewers[0].position").value(1))
                .andExpect(jsonPath("$.reviewers[0].status").value("NOT_STARTED"))
                .andExpect(jsonPath("$.reviewers[0].assignedBy").value(COORDINATOR))
                .andExpect(jsonPath("$.reviewers[0].decision").value(nullValue()));

        assign(brief, second, null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reviewers.length()").value(2))
                .andExpect(jsonPath("$.reviewers[1].reviewerId").value(second))
                .andExpect(jsonPath("$.reviewers[1].position").value(2))
                .andExpect(jsonPath("$.reviewers[1].department").value(nullValue()));
    }

    @Test
    void reviewerIdIsStoredExactlyAsTheAccountEmail() throws Exception {
        Submission brief = configuredBrief();
        users.saveAndFlush(new ApiUser(UUID.randomUUID(), "Mixed.Case@Example.test", "hash", ApiUserRole.REVIEWER,
                Instant.parse("2026-11-01T08:00:00Z")));

        assign(brief, "  mixed.case@EXAMPLE.TEST ", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reviewers[0].reviewerId").value("Mixed.Case@Example.test"));

        assertEquals("Mixed.Case@Example.test", jdbcTemplate.queryForObject(
                "SELECT reviewer_id FROM review_assignments WHERE brief_id = ?", String.class, brief.getId()));
    }

    @Test
    void unknownNonReviewerAndDisabledAccountsAreRejected() throws Exception {
        Submission brief = configuredBrief();
        account("officer@example.test", ApiUserRole.FACT_FINDING_OFFICER);
        String disabled = reviewerAccount("disabled@example.test");
        jdbcTemplate.update("UPDATE api_users SET enabled = false WHERE email = ?", disabled);
        entityManager.clear();

        for (String candidate : List.of("nobody@example.test", "officer@example.test", disabled, "  ")) {
            assign(brief, candidate, null)
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(422))
                    .andExpect(jsonPath("$.detail").value(containsString("reviewerId")));
        }
        assertEquals(0, countAssignments(brief));
    }

    @Test
    void sameReviewerTwiceIsAConflict() throws Exception {
        Submission brief = configuredBrief();
        String reviewer = reviewerAccount("twice@example.test");
        assign(brief, reviewer, null).andExpect(status().isCreated());

        assign(brief, reviewer.toUpperCase(), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("This reviewer is already assigned to this brief."));
        assertEquals(1, countAssignments(brief));
    }

    @Test
    void twentyFirstAssignmentIsAConflict() throws Exception {
        Submission brief = configuredBrief();
        for (int i = 1; i <= 20; i++) {
            assign(brief, reviewerAccount("bulk-" + i + "@example.test"), null).andExpect(status().isCreated());
        }

        assign(brief, reviewerAccount("bulk-21@example.test"), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("at most 20 reviewers")));
        assertEquals(20, countAssignments(brief));
    }

    @Test
    void departmentLongerThanOneHundredCharactersIsRejected() throws Exception {
        Submission brief = configuredBrief();
        String reviewer = reviewerAccount("dept@example.test");

        assign(brief, reviewer, "d".repeat(101))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(containsString("department")));
        assign(brief, reviewer, "d".repeat(100)).andExpect(status().isCreated());
    }

    @Test
    void assigningRequiresABriefUnderReview() throws Exception {
        Submission brief = briefInStage(SubmissionStage.REGISTERED);

        assign(brief, reviewerAccount("stage@example.test"), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("under review")));
    }

    // ---- remove -------------------------------------------------------------------------

    @Test
    void removingAnUnsubmittedReviewerKeepsOtherPositionsAndNextAssignmentGetsMaxPlusOne() throws Exception {
        Submission brief = configuredBrief();
        String one = reviewerAccount("one@example.test");
        String two = reviewerAccount("two@example.test");
        String three = reviewerAccount("three@example.test");
        assign(brief, one, null).andExpect(status().isCreated());
        assign(brief, two, null).andExpect(status().isCreated());
        assign(brief, three, null).andExpect(status().isCreated());

        remove(brief, two)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewers.length()").value(2))
                .andExpect(jsonPath("$.reviewers[0].reviewerId").value(one))
                .andExpect(jsonPath("$.reviewers[0].position").value(1))
                .andExpect(jsonPath("$.reviewers[1].reviewerId").value(three))
                .andExpect(jsonPath("$.reviewers[1].position").value(3));

        assign(brief, reviewerAccount("four@example.test"), null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reviewers[2].reviewerId").value("four@example.test"))
                .andExpect(jsonPath("$.reviewers[2].position").value(4));
    }

    @Test
    void removingASubmittedReviewerIsAConflictAndLeavesTheRow() throws Exception {
        Submission brief = configuredBrief();
        ReviewAssignment submitted = submittedAssignment(brief, "done@example.test", 1);
        Map<String, Object> before = assignmentRow(submitted.getId());

        remove(brief, "done@example.test")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("cannot be removed")));

        assertEquals(before, assignmentRow(submitted.getId()));
    }

    @Test
    void removingSomeoneNotAssignedIsNotFound() throws Exception {
        Submission configured = configuredBrief();
        Submission unconfigured = underReviewBrief();

        for (Submission brief : List.of(configured, unconfigured)) {
            remove(brief, "stranger@example.test")
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.detail").value("That reviewer is not assigned to this brief."));
        }
    }

    @Test
    void emailWithSpecialCharactersInThePathIsHandled() throws Exception {
        Submission brief = configuredBrief();
        String plus = reviewerAccount("first.last+review@example.test");
        String other = reviewerAccount("o'brien+2@example.test");
        assign(brief, plus, null).andExpect(status().isCreated());
        assign(brief, other, null).andExpect(status().isCreated());

        mockMvc.perform(delete(URI.create("/cases/" + brief.getId() + "/reviewers/first.last%2Breview%40example.test"))
                        .with(coordinator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewers.length()").value(1))
                .andExpect(jsonPath("$.reviewers[0].reviewerId").value(other));

        remove(brief, other)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewers").isEmpty());
    }

    // ---- access and general --------------------------------------------------------------

    @Test
    void administratorCanUseAllThreeWriteEndpoints() throws Exception {
        Submission brief = underReviewBrief();
        String reviewer = reviewerAccount("admin-flow@example.test");

        putSettings(brief, settingsBody("PARALLEL", 1, null, null), "admin@example.test", "ADMINISTRATOR")
                .andExpect(status().isOk());
        mockMvc.perform(withBody(post(REVIEWERS, brief.getId()), Map.of("reviewerId", reviewer))
                        .with(user("admin@example.test").roles("ADMINISTRATOR")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reviewers[0].assignedBy").value("admin@example.test"));
        mockMvc.perform(delete(REVIEWER, brief.getId(), reviewer)
                        .with(user("admin@example.test").roles("ADMINISTRATOR")))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "FACT_FINDING_OFFICER", "REVIEWER", "SUBMITTER", "RECEPTIONIST",
            "SECTOR_OFFICER", "IP_LIAISON", "OBSERVER"
    })
    void otherRolesAreForbiddenOnAllWriteEndpoints(String role) throws Exception {
        Submission brief = configuredBrief();
        String reviewer = reviewerAccount("target@example.test");
        assign(brief, reviewer, null).andExpect(status().isCreated());
        RequestPostProcessor other = user("other@example.test").roles(role);
        long settingsVersion = settingsVersionOf(brief);

        for (MockHttpServletRequestBuilder request : List.of(
                withBody(put(SETTINGS, brief.getId()), settingsBody("SEQUENTIAL", 3, null, settingsVersion)),
                withBody(post(REVIEWERS, brief.getId()), Map.of("reviewerId", reviewerAccount("x-" + role.toLowerCase() + "@example.test"))),
                delete(REVIEWER, brief.getId(), reviewer))) {
            mockMvc.perform(request.with(other))
                    .andExpect(status().isForbidden())
                    .andExpect(header().string("WWW-Authenticate", "Bearer"))
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.title").value("Forbidden"))
                    .andExpect(jsonPath("$.detail").value("You do not have permission to perform this action."));
        }
        assertEquals(1, countAssignments(brief));
        assertEquals(settingsVersion, settingsVersionOf(brief));
    }

    @Test
    void unauthenticatedRequestsAreUnauthorized() throws Exception {
        for (MockHttpServletRequestBuilder request : List.of(
                withBody(put(SETTINGS, UNKNOWN_BRIEF), settingsBody("PARALLEL", 1, null, null)),
                withBody(post(REVIEWERS, UNKNOWN_BRIEF), Map.of("reviewerId", "r@example.test")),
                delete(REVIEWER, UNKNOWN_BRIEF, "r@example.test"))) {
            mockMvc.perform(request)
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string("WWW-Authenticate", "Bearer"))
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.detail").value("A valid bearer token is required."));
        }
    }

    @Test
    void unknownBriefIsNotFound() throws Exception {
        for (MockHttpServletRequestBuilder request : List.of(
                withBody(put(SETTINGS, UNKNOWN_BRIEF), settingsBody("PARALLEL", 1, null, null)),
                withBody(post(REVIEWERS, UNKNOWN_BRIEF), Map.of("reviewerId", "r@example.test")),
                delete(REVIEWER, UNKNOWN_BRIEF, "r@example.test"))) {
            mockMvc.perform(request.with(coordinator()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title").value("Submission not found"))
                    .andExpect(jsonPath("$.detail").value("The requested submission was not found."));
        }
    }

    @Test
    void malformedBriefIdIsBadRequest() throws Exception {
        for (MockHttpServletRequestBuilder request : List.of(
                withBody(put("/cases/not-a-uuid/review-settings"), settingsBody("PARALLEL", 1, null, null)),
                withBody(post("/cases/not-a-uuid/reviewers"), Map.of("reviewerId", "r@example.test")),
                delete("/cases/not-a-uuid/reviewers/r@example.test"))) {
            mockMvc.perform(request.with(coordinator()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Invalid request"))
                    .andExpect(jsonPath("$.detail").value("A request parameter has an invalid value."));
        }
    }

    @Test
    void malformedJsonBodyIsBadRequest() throws Exception {
        Submission brief = configuredBrief();

        for (MockHttpServletRequestBuilder request : List.of(
                put(SETTINGS, brief.getId()), post(REVIEWERS, brief.getId()))) {
            mockMvc.perform(request.with(coordinator())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"mode\": \"PARALLEL\", "))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Malformed request body"));
        }
        putSettings(brief, Map.of("mode", "NOT_A_MODE", "reviewsNeeded", 1, "version", 0))
                .andExpect(status().isBadRequest());
    }

    @Test
    void serverOwnedFieldsInTheBodyAreIgnored() throws Exception {
        Submission brief = underReviewBrief();
        Map<String, Object> settings = settingsBody("PARALLEL", 2, null, null);
        settings.put("round", 7);
        settings.put("setBy", "attacker@example.test");
        putSettings(brief, settings)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.round").value(1));
        assertEquals(COORDINATOR, jdbcTemplate.queryForObject(
                "SELECT set_by FROM review_settings WHERE brief_id = ?", String.class, brief.getId()));

        String reviewer = reviewerAccount("owned@example.test");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("reviewerId", reviewer);
        body.put("round", 9);
        body.put("position", 99);
        body.put("status", "SUBMITTED");
        body.put("assignedBy", "attacker@example.test");
        mockMvc.perform(withBody(post(REVIEWERS, brief.getId()), body).with(coordinator()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reviewers[0].position").value(1))
                .andExpect(jsonPath("$.reviewers[0].status").value("NOT_STARTED"))
                .andExpect(jsonPath("$.reviewers[0].assignedBy").value(COORDINATOR));
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT round, position, status, assigned_by FROM review_assignments WHERE brief_id = ?",
                brief.getId());
        assertEquals(Map.of("round", 1, "position", 1, "status", "NOT_STARTED", "assigned_by", COORDINATOR), row);
    }

    @Test
    void getReviewersMatchesEachWriteAndStageIsUnchanged() throws Exception {
        Submission brief = underReviewBrief();
        String reviewer = reviewerAccount("echo@example.test");

        String afterSettings = body(putSettings(brief, settingsBody("PARALLEL", 1, null, null)));
        assertEquals(json(afterSettings), json(body(getPanel(brief))));
        assertEquals("UNDER_REVIEW", storedStage(brief));

        String afterAssign = body(assign(brief, reviewer, "Science").andExpect(status().isCreated()));
        assertEquals(json(afterAssign), json(body(getPanel(brief))));
        assertEquals("UNDER_REVIEW", storedStage(brief));

        String afterRemove = body(remove(brief, reviewer).andExpect(status().isOk()));
        assertEquals(json(afterRemove), json(body(getPanel(brief))));
        assertEquals("UNDER_REVIEW", storedStage(brief));
    }

    // ---- helpers -------------------------------------------------------------------------

    private ResultActions putSettings(Submission brief, Map<String, Object> body) throws Exception {
        return putSettings(brief, body, COORDINATOR, "INNOVATION_COORDINATOR");
    }

    private ResultActions putSettings(Submission brief, Map<String, Object> body, String principal, String role)
            throws Exception {
        return mockMvc.perform(withBody(put(SETTINGS, brief.getId()), body).with(user(principal).roles(role)));
    }

    private ResultActions assign(Submission brief, String reviewerId, String department) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("reviewerId", reviewerId);
        if (department != null) {
            body.put("department", department);
        }
        return mockMvc.perform(withBody(post(REVIEWERS, brief.getId()), body).with(coordinator()));
    }

    private ResultActions remove(Submission brief, String reviewerId) throws Exception {
        return mockMvc.perform(delete(REVIEWER, brief.getId(), reviewerId).with(coordinator()));
    }

    private ResultActions getPanel(Submission brief) throws Exception {
        return mockMvc.perform(get(REVIEWERS, brief.getId()).with(coordinator())).andExpect(status().isOk());
    }

    private MockHttpServletRequestBuilder withBody(MockHttpServletRequestBuilder request, Map<String, Object> body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body));
    }

    private static Map<String, Object> settingsBody(String mode, Integer reviewsNeeded, String dueDate, Long version) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("mode", mode);
        body.put("reviewsNeeded", reviewsNeeded);
        body.put("dueDate", dueDate);
        body.put("version", version);
        return body;
    }

    private long version(ResultActions result) throws Exception {
        return json(body(result.andExpect(status().isOk()))).get("version").asLong();
    }

    private static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }

    private JsonNode json(String body) {
        return objectMapper.readTree(body);
    }

    private static RequestPostProcessor coordinator() {
        return user(COORDINATOR).roles("INNOVATION_COORDINATOR");
    }

    private static LocalDate today() {
        return LocalDate.now(HUB_ZONE);
    }

    private String reviewerAccount(String email) {
        return account(email, ApiUserRole.REVIEWER);
    }

    private String account(String email, ApiUserRole role) {
        users.saveAndFlush(new ApiUser(UUID.randomUUID(), email, "not-a-real-hash", role,
                Instant.parse("2026-11-01T08:00:00Z")));
        return email;
    }

    private ReviewAssignment submittedAssignment(Submission brief, String reviewer, int position) {
        ReviewAssignment assignment = new ReviewAssignment(brief.getId(), 1, reviewer, null, position, COORDINATOR);
        assignment.setStatus(ReviewAssignmentStatus.SUBMITTED);
        ReviewAssignment saved = assignments.saveAndFlush(assignment);
        reviews.saveAndFlush(new Review(saved.getId(), ReviewDecision.RECOMMENDED, "Fine.", Instant.now()));
        return saved;
    }

    private Map<String, Object> assignmentRow(Long id) {
        return jdbcTemplate.queryForMap("SELECT * FROM review_assignments WHERE id = ?", id);
    }

    private int countSettings(Submission brief) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM review_settings WHERE brief_id = ?", Integer.class, brief.getId());
    }

    private int countAssignments(Submission brief) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM review_assignments WHERE brief_id = ?", Integer.class, brief.getId());
    }

    private long settingsVersionOf(Submission brief) {
        return jdbcTemplate.queryForObject(
                "SELECT version FROM review_settings WHERE brief_id = ?", Long.class, brief.getId());
    }

    private String storedStage(Submission brief) {
        return jdbcTemplate.queryForObject("SELECT stage FROM submissions WHERE id = ?", String.class, brief.getId());
    }

    private Submission configuredBrief() throws Exception {
        Submission brief = underReviewBrief();
        putSettings(brief, settingsBody("PARALLEL", 2, null, null)).andExpect(status().isOk());
        return brief;
    }

    private Submission underReviewBrief() {
        return briefInStage(SubmissionStage.UNDER_REVIEW);
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
        brief.register("CASE-RC-" + UUID.randomUUID().toString().substring(0, 8),
                Instant.parse("2026-11-01T09:00:00Z"), "receptionist@example.test");
        Instant time = Instant.parse("2026-11-01T10:00:00Z");
        for (SubmissionStage stage : List.of(
                SubmissionStage.REGISTERED, SubmissionStage.FACT_FINDING, SubmissionStage.UNDER_REVIEW)) {
            brief.transitionTo(stage, time);
            if (stage == target) {
                break;
            }
        }
        return submissions.saveAndFlush(brief);
    }
}

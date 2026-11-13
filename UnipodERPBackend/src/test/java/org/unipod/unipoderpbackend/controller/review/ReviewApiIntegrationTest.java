package org.unipod.unipoderpbackend.controller.review;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.dto.review.ReviewersPanelResponse;
import org.unipod.unipoderpbackend.model.review.Review;
import org.unipod.unipoderpbackend.model.review.ReviewAssignment;
import org.unipod.unipoderpbackend.model.review.ReviewAssignmentStatus;
import org.unipod.unipoderpbackend.model.review.ReviewDecision;
import org.unipod.unipoderpbackend.model.review.ReviewFollowUpNote;
import org.unipod.unipoderpbackend.model.review.ReviewMode;
import org.unipod.unipoderpbackend.model.review.ReviewSettings;
import org.unipod.unipoderpbackend.model.submission.Submission;
import org.unipod.unipoderpbackend.model.submission.SubmissionCategory;
import org.unipod.unipoderpbackend.repository.review.ReviewAssignmentRepository;
import org.unipod.unipoderpbackend.repository.review.ReviewFollowUpNoteRepository;
import org.unipod.unipoderpbackend.repository.review.ReviewRepository;
import org.unipod.unipoderpbackend.repository.review.ReviewSettingsRepository;
import org.unipod.unipoderpbackend.repository.submission.SubmissionRepository;
import org.unipod.unipoderpbackend.service.review.ReviewQuorum;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReviewApiIntegrationTest {
    private static final UUID UNKNOWN_BRIEF = UUID.fromString("00000000-0000-0000-0000-00000000beef");
    private static final String QUORUM = "/cases/{id}/review-quorum";
    private static final String PANEL = "/cases/{id}/reviewers";
    private static final String COORDINATOR = "coord@example.test";
    private static final String SECRET_COMMENT = "Confidential reviewer comment that must never leak";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SubmissionRepository submissions;

    @Autowired
    private ReviewSettingsRepository settings;

    @Autowired
    private ReviewAssignmentRepository assignments;

    @Autowired
    private ReviewRepository reviews;

    @Autowired
    private ReviewFollowUpNoteRepository notes;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    // ---- quorum -------------------------------------------------------------------------

    @Test
    void quorumWithoutSettingsIsNotConfigured() throws Exception {
        Submission brief = createBrief("CASE-RV-1");

        mockMvc.perform(get(QUORUM, brief.getId()).with(coordinator()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.configured").value(false))
                .andExpect(jsonPath("$.round").value(nullValue()))
                .andExpect(jsonPath("$.required").value(0))
                .andExpect(jsonPath("$.completed").value(0))
                .andExpect(jsonPath("$.met").value(false));
    }

    @Test
    void quorumNeedingTwoWithNoneSubmittedIsNotMet() throws Exception {
        Submission brief = createBrief("CASE-RV-2");
        settings(brief, 1, 2);
        assignment(brief, 1, "r1@example.test", 1, null);
        assignment(brief, 1, "r2@example.test", 2, null);

        expectQuorum(brief, 1, 2, 0, false);
    }

    @Test
    void quorumWithOneOfTwoSubmittedIsNotMet() throws Exception {
        Submission brief = createBrief("CASE-RV-3");
        settings(brief, 1, 2);
        assignment(brief, 1, "r1@example.test", 1, ReviewDecision.RECOMMENDED);
        assignment(brief, 1, "r2@example.test", 2, null);

        expectQuorum(brief, 1, 2, 1, false);
    }

    @Test
    void quorumWithTwoOfTwoSubmittedIsMet() throws Exception {
        Submission brief = createBrief("CASE-RV-4");
        settings(brief, 1, 2);
        assignment(brief, 1, "r1@example.test", 1, ReviewDecision.RECOMMENDED);
        assignment(brief, 1, "r2@example.test", 2, ReviewDecision.CHANGES_NEEDED);

        expectQuorum(brief, 1, 2, 2, true);
    }

    @Test
    void quorumWithMoreSubmittedThanNeededIsMet() throws Exception {
        Submission brief = createBrief("CASE-RV-5");
        settings(brief, 1, 2);
        assignment(brief, 1, "r1@example.test", 1, ReviewDecision.RECOMMENDED);
        assignment(brief, 1, "r2@example.test", 2, ReviewDecision.NOT_RECOMMENDED);
        assignment(brief, 1, "r3@example.test", 3, ReviewDecision.RECOMMENDED);

        expectQuorum(brief, 1, 2, 3, true);
    }

    @Test
    void quorumUsesTheNewRoundOnceItIsConfigured() throws Exception {
        Submission brief = createBrief("CASE-RV-6");
        settings(brief, 1, 2);
        assignment(brief, 1, "r1@example.test", 1, ReviewDecision.RECOMMENDED);
        assignment(brief, 1, "r2@example.test", 2, ReviewDecision.RECOMMENDED);
        expectQuorum(brief, 1, 2, 2, true);

        settings(brief, 2, 2);

        expectQuorum(brief, 2, 2, 0, false);
    }

    @Test
    void quorumCountsOnlyTheLatestRoundsSubmissions() throws Exception {
        Submission brief = createBrief("CASE-RV-7");
        settings(brief, 1, 2);
        assignment(brief, 1, "r1@example.test", 1, ReviewDecision.RECOMMENDED);
        assignment(brief, 1, "r2@example.test", 2, ReviewDecision.RECOMMENDED);
        assignment(brief, 1, "r3@example.test", 3, ReviewDecision.RECOMMENDED);
        settings(brief, 2, 2);
        assignment(brief, 2, "r1@example.test", 1, ReviewDecision.RECOMMENDED);
        assignment(brief, 2, "r2@example.test", 2, null);

        expectQuorum(brief, 2, 2, 1, false);
    }

    // ---- panel --------------------------------------------------------------------------

    @Test
    void panelWithoutSettingsIsNotConfiguredAndEmpty() throws Exception {
        Submission brief = createBrief("CASE-RV-8");

        mockMvc.perform(get(PANEL, brief.getId()).with(coordinator()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.briefId").value(brief.getId().toString()))
                .andExpect(jsonPath("$.configured").value(false))
                .andExpect(jsonPath("$.round").value(nullValue()))
                .andExpect(jsonPath("$.mode").value(nullValue()))
                .andExpect(jsonPath("$.reviewsNeeded").value(nullValue()))
                .andExpect(jsonPath("$.dueDate").value(nullValue()))
                .andExpect(jsonPath("$.quorum.required").value(0))
                .andExpect(jsonPath("$.quorum.completed").value(0))
                .andExpect(jsonPath("$.quorum.met").value(false))
                .andExpect(jsonPath("$.reviewers").isArray())
                .andExpect(jsonPath("$.reviewers").isEmpty());
    }

    @Test
    void panelListsReviewersByPositionEvenWhenInsertedOutOfOrder() throws Exception {
        Submission brief = createBrief("CASE-RV-9");
        settings(brief, 1, 2);
        assignment(brief, 1, "third@example.test", 3, null);
        assignment(brief, 1, "first@example.test", 1, null);
        assignment(brief, 1, "second@example.test", 2, null);

        mockMvc.perform(get(PANEL, brief.getId()).with(coordinator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewers.length()").value(3))
                .andExpect(jsonPath("$.reviewers[0].position").value(1))
                .andExpect(jsonPath("$.reviewers[0].reviewerId").value("first@example.test"))
                .andExpect(jsonPath("$.reviewers[1].position").value(2))
                .andExpect(jsonPath("$.reviewers[1].reviewerId").value("second@example.test"))
                .andExpect(jsonPath("$.reviewers[2].position").value(3))
                .andExpect(jsonPath("$.reviewers[2].reviewerId").value("third@example.test"));
    }

    @Test
    void panelShowsDecisionAndSubmissionTimeOnlyForSubmittedReviewers() throws Exception {
        Submission brief = createBrief("CASE-RV-10");
        settings(brief, 1, 2);
        assignment(brief, 1, "done@example.test", 1, ReviewDecision.CHANGES_NEEDED);
        assignment(brief, 1, "waiting@example.test", 2, null);

        mockMvc.perform(get(PANEL, brief.getId()).with(coordinator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewers[0].reviewerId").value("done@example.test"))
                .andExpect(jsonPath("$.reviewers[0].department").value("Engineering"))
                .andExpect(jsonPath("$.reviewers[0].status").value("SUBMITTED"))
                .andExpect(jsonPath("$.reviewers[0].assignedBy").value(COORDINATOR))
                .andExpect(jsonPath("$.reviewers[0].decision").value("CHANGES_NEEDED"))
                .andExpect(jsonPath("$.reviewers[0].submittedAt").value("2026-11-10T09:30:00Z"))
                .andExpect(jsonPath("$.reviewers[1].reviewerId").value("waiting@example.test"))
                .andExpect(jsonPath("$.reviewers[1].status").value("NOT_STARTED"))
                .andExpect(jsonPath("$.reviewers[1].decision").value(nullValue()))
                .andExpect(jsonPath("$.reviewers[1].submittedAt").value(nullValue()))
                .andExpect(jsonPath("$.quorum.required").value(2))
                .andExpect(jsonPath("$.quorum.completed").value(1))
                .andExpect(jsonPath("$.quorum.met").value(false));
    }

    @Test
    void panelNeverContainsTheReviewComment() throws Exception {
        Submission brief = createBrief("CASE-RV-11");
        settings(brief, 1, 1);
        assignment(brief, 1, "done@example.test", 1, ReviewDecision.RECOMMENDED);

        String body = mockMvc.perform(get(PANEL, brief.getId()).with(coordinator()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<String> fieldNames = new ArrayList<>();
        collectFieldNames(objectMapper.readTree(body), fieldNames);
        assertFalse(fieldNames.contains("comment"), "Unexpected comment field in " + body);
        assertFalse(body.contains(SECRET_COMMENT), "Review comment text leaked: " + body);
        assertFalse(body.contains("\"comment\""), body);
    }

    @Test
    void panelShowsSettingsOfTheLatestRound() throws Exception {
        Submission brief = createBrief("CASE-RV-12");
        settings.saveAndFlush(new ReviewSettings(
                brief.getId(), 1, ReviewMode.PARALLEL, 1, LocalDate.parse("2026-11-20"), COORDINATOR));
        assignment(brief, 1, "old@example.test", 1, ReviewDecision.RECOMMENDED);
        settings.saveAndFlush(new ReviewSettings(
                brief.getId(), 2, ReviewMode.SEQUENTIAL, 3, LocalDate.parse("2026-12-15"), COORDINATOR));
        assignment(brief, 2, "new@example.test", 1, null);

        mockMvc.perform(get(PANEL, brief.getId()).with(coordinator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.round").value(2))
                .andExpect(jsonPath("$.mode").value("SEQUENTIAL"))
                .andExpect(jsonPath("$.reviewsNeeded").value(3))
                .andExpect(jsonPath("$.dueDate").value("2026-12-15"))
                .andExpect(jsonPath("$.quorum.required").value(3))
                .andExpect(jsonPath("$.quorum.completed").value(0))
                .andExpect(jsonPath("$.quorum.met").value(false))
                .andExpect(jsonPath("$.reviewers.length()").value(1))
                .andExpect(jsonPath("$.reviewers[0].reviewerId").value("new@example.test"));
    }

    // ---- security and errors -------------------------------------------------------------

    @Test
    void administratorCanReadBothEndpoints() throws Exception {
        Submission brief = createBrief("CASE-RV-13");
        settings(brief, 1, 1);

        mockMvc.perform(get(QUORUM, brief.getId()).with(user("admin@example.test").roles("ADMINISTRATOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true));
        mockMvc.perform(get(PANEL, brief.getId()).with(user("admin@example.test").roles("ADMINISTRATOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "FACT_FINDING_OFFICER", "REVIEWER", "SUBMITTER", "RECEPTIONIST",
            "SECTOR_OFFICER", "IP_LIAISON", "OBSERVER"
    })
    void otherRolesAreForbiddenOnBothEndpoints(String role) throws Exception {
        Submission brief = createBrief("CASE-RV-14");
        settings(brief, 1, 1);

        for (String path : List.of(QUORUM, PANEL)) {
            mockMvc.perform(get(path, brief.getId()).with(user("other@example.test").roles(role)))
                    .andExpect(status().isForbidden())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.title").value("Forbidden"))
                    .andExpect(jsonPath("$.detail").value("You do not have permission to perform this action."));
        }
    }

    @Test
    void unknownBriefReturnsNotFoundOnBothEndpoints() throws Exception {
        for (String path : List.of(QUORUM, PANEL)) {
            mockMvc.perform(get(path, UNKNOWN_BRIEF).with(coordinator()))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.title").value("Submission not found"))
                    .andExpect(jsonPath("$.detail").value("The requested submission was not found."));
        }
    }

    @Test
    void malformedBriefIdReturnsBadRequestOnBothEndpoints() throws Exception {
        for (String path : List.of("/cases/not-a-uuid/review-quorum", "/cases/not-a-uuid/reviewers")) {
            mockMvc.perform(get(path).with(coordinator()))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.title").value("Invalid request"))
                    .andExpect(jsonPath("$.detail").value("A request parameter has an invalid value."));
        }
    }

    @Test
    void unauthenticatedRequestReturnsUnauthorizedOnBothEndpoints() throws Exception {
        for (String path : List.of(QUORUM, PANEL)) {
            mockMvc.perform(get(path, UNKNOWN_BRIEF))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string("WWW-Authenticate", "Bearer"))
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.title").value("Unauthorized"))
                    .andExpect(jsonPath("$.detail").value("A valid bearer token is required."));
        }
    }

    // ---- read-only and JSON shape --------------------------------------------------------

    @Test
    void readingBothEndpointsWritesNothing() throws Exception {
        Submission configured = createBrief("CASE-RV-16A");
        settings(configured, 1, 2);
        assignment(configured, 1, "r1@example.test", 1, ReviewDecision.RECOMMENDED);
        assignment(configured, 1, "r2@example.test", 2, null);
        Long reviewId = reviews.findByAssignmentId(
                assignments.findByBriefIdAndRoundOrderByPositionAsc(configured.getId(), 1).get(0).getId()
        ).orElseThrow().getId();
        notes.saveAndFlush(new ReviewFollowUpNote(reviewId, "Follow-up", "r1@example.test"));
        Submission unconfigured = createBrief("CASE-RV-16B");

        List<Long> before = reviewTableCounts();
        Long settingsVersionBefore = settingsVersion(configured);

        for (Submission brief : List.of(configured, unconfigured)) {
            mockMvc.perform(get(QUORUM, brief.getId()).with(coordinator())).andExpect(status().isOk());
            mockMvc.perform(get(PANEL, brief.getId()).with(coordinator())).andExpect(status().isOk());
        }
        mockMvc.perform(get(QUORUM, UNKNOWN_BRIEF).with(coordinator())).andExpect(status().isNotFound());
        mockMvc.perform(get(PANEL, UNKNOWN_BRIEF).with(coordinator())).andExpect(status().isNotFound());

        assertEquals(before, reviewTableCounts());
        assertEquals(settingsVersionBefore, settingsVersion(configured));
        assertTrue(settings.findTopByBriefIdOrderByRoundDesc(unconfigured.getId()).isEmpty());
    }

    @Test
    void quorumJsonFieldsMatchTheRecordExactly() throws Exception {
        Submission brief = createBrief("CASE-RV-18A");
        settings(brief, 1, 1);

        JsonNode json = readJson(QUORUM, brief);

        assertEquals(componentNames(ReviewQuorum.class), new HashSet<>(json.propertyNames()));
    }

    @Test
    void panelJsonFieldsMatchTheRecordsExactly() throws Exception {
        Submission brief = createBrief("CASE-RV-18B");
        settings(brief, 1, 1);
        assignment(brief, 1, "r1@example.test", 1, ReviewDecision.RECOMMENDED);

        JsonNode json = readJson(PANEL, brief);

        assertEquals(componentNames(ReviewersPanelResponse.class), new HashSet<>(json.propertyNames()));
        assertEquals(
                componentNames(ReviewersPanelResponse.Quorum.class),
                new HashSet<>(json.get("quorum").propertyNames()));
        assertEquals(
                componentNames(ReviewersPanelResponse.Reviewer.class),
                new HashSet<>(json.get("reviewers").get(0).propertyNames()));
    }

    // ---- helpers -------------------------------------------------------------------------

    private void expectQuorum(Submission brief, int round, int required, int completed, boolean met)
            throws Exception {
        mockMvc.perform(get(QUORUM, brief.getId()).with(coordinator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.round").value(round))
                .andExpect(jsonPath("$.required").value(required))
                .andExpect(jsonPath("$.completed").value(completed))
                .andExpect(jsonPath("$.met").value(met));
    }

    private JsonNode readJson(String path, Submission brief) throws Exception {
        String body = mockMvc.perform(get(path, brief.getId()).with(coordinator()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private static Set<String> componentNames(Class<?> record) {
        return Arrays.stream(record.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(Collectors.toSet());
    }

    private static void collectFieldNames(JsonNode node, List<String> names) {
        if (node.isObject()) {
            for (String name : node.propertyNames()) {
                names.add(name);
                collectFieldNames(node.get(name), names);
            }
        } else if (node.isArray()) {
            for (JsonNode element : node) {
                collectFieldNames(element, names);
            }
        }
    }

    private List<Long> reviewTableCounts() {
        return List.of("review_settings", "review_assignments", "reviews", "review_follow_up_notes").stream()
                .map(table -> jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Long.class))
                .toList();
    }

    private Long settingsVersion(Submission brief) {
        return jdbcTemplate.queryForObject(
                "SELECT version FROM review_settings WHERE brief_id = ? AND round = 1", Long.class, brief.getId());
    }

    private static RequestPostProcessor coordinator() {
        return user(COORDINATOR).roles("INNOVATION_COORDINATOR");
    }

    private void settings(Submission brief, int round, int reviewsNeeded) {
        settings.saveAndFlush(new ReviewSettings(
                brief.getId(), round, ReviewMode.PARALLEL, reviewsNeeded, null, COORDINATOR));
    }

    private void assignment(Submission brief, int round, String reviewer, int position, ReviewDecision decision) {
        ReviewAssignment assignment = new ReviewAssignment(
                brief.getId(), round, reviewer, "Engineering", position, COORDINATOR);
        if (decision != null) {
            assignment.setStatus(ReviewAssignmentStatus.SUBMITTED);
        }
        ReviewAssignment saved = assignments.saveAndFlush(assignment);
        if (decision != null) {
            reviews.saveAndFlush(new Review(
                    saved.getId(), decision, SECRET_COMMENT, Instant.parse("2026-11-10T09:30:00Z")));
        }
    }

    private Submission createBrief(String code) {
        Submission brief = new Submission(
                UUID.randomUUID(),
                SubmissionCategory.GENERAL,
                "Brief " + code,
                "Purpose of " + code,
                "Problem addressed by " + code,
                "receptionist@example.test",
                "submitter@example.test",
                null,
                null,
                null,
                null,
                Instant.parse("2026-11-01T08:00:00Z")
        );
        brief.register(code, Instant.parse("2026-11-01T09:00:00Z"), "receptionist@example.test");
        return submissions.saveAndFlush(brief);
    }
}

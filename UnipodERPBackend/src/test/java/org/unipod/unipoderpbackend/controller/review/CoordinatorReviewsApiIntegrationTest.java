package org.unipod.unipoderpbackend.controller.review;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.model.auth.ApiUser;
import org.unipod.unipoderpbackend.model.auth.ApiUserRole;
import org.unipod.unipoderpbackend.model.review.Review;
import org.unipod.unipoderpbackend.model.review.ReviewAssignment;
import org.unipod.unipoderpbackend.model.review.ReviewAssignmentStatus;
import org.unipod.unipoderpbackend.model.review.ReviewDecision;
import org.unipod.unipoderpbackend.model.review.ReviewFollowUpNote;
import org.unipod.unipoderpbackend.model.review.ReviewMode;
import org.unipod.unipoderpbackend.model.review.ReviewSettings;
import org.unipod.unipoderpbackend.model.submission.Submission;
import org.unipod.unipoderpbackend.model.submission.SubmissionCategory;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;
import org.unipod.unipoderpbackend.repository.auth.ApiUserRepository;
import org.unipod.unipoderpbackend.repository.review.ReviewAssignmentRepository;
import org.unipod.unipoderpbackend.repository.review.ReviewFollowUpNoteRepository;
import org.unipod.unipoderpbackend.repository.review.ReviewRepository;
import org.unipod.unipoderpbackend.repository.review.ReviewSettingsRepository;
import org.unipod.unipoderpbackend.repository.submission.SubmissionRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CoordinatorReviewsApiIntegrationTest {
    private static final UUID UNKNOWN_BRIEF = UUID.fromString("00000000-0000-0000-0000-00000000c0de");
    private static final String REVIEWS = "/cases/{id}/reviews";
    private static final String COORDINATOR = "coord@example.test";
    private static final String SUBMITTER = "submitter@example.test";
    private static final String MARKER = "SECRET-MARKER-xyz";
    private static final Instant T0 = Instant.parse("2026-11-10T08:00:00Z");

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
    private ApiUserRepository users;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ObjectMapper objectMapper;

    // ---- 1, 2, 3: contents ---------------------------------------------------------------

    @Test
    void briefWithoutSettingsIsNotConfigured() throws Exception {
        Submission brief = underReviewBrief();

        list(brief)
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.briefId").value(brief.getId().toString()))
                .andExpect(jsonPath("$.configured").value(false))
                .andExpect(jsonPath("$.round").value(nullValue()))
                .andExpect(jsonPath("$.mode").value(nullValue()))
                .andExpect(jsonPath("$.reviewsNeeded").value(nullValue()))
                .andExpect(jsonPath("$.dueDate").value(nullValue()))
                .andExpect(jsonPath("$.latestRound").value(nullValue()))
                .andExpect(jsonPath("$.quorum.required").value(0))
                .andExpect(jsonPath("$.quorum.completed").value(0))
                .andExpect(jsonPath("$.quorum.met").value(false))
                .andExpect(jsonPath("$.reviews").isEmpty())
                .andExpect(jsonPath("$.notSubmittedCount").value(0));
    }

    @Test
    void settingsWithoutSubmittedReviewsShowOnlyTheCount() throws Exception {
        Submission brief = underReviewBrief();
        settings(brief, 1, 2, LocalDate.of(2027, 1, 15));
        assign(brief, 1, "a@example.test", 1);
        assign(brief, 1, "b@example.test", 2);
        assign(brief, 1, "c@example.test", 3);

        String body = body(list(brief)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.round").value(1))
                .andExpect(jsonPath("$.mode").value("PARALLEL"))
                .andExpect(jsonPath("$.reviewsNeeded").value(2))
                .andExpect(jsonPath("$.dueDate").value("2027-01-15"))
                .andExpect(jsonPath("$.latestRound").value(1))
                .andExpect(jsonPath("$.reviews").isEmpty())
                .andExpect(jsonPath("$.notSubmittedCount").value(3))
                .andExpect(jsonPath("$.quorum.required").value(2))
                .andExpect(jsonPath("$.quorum.completed").value(0))
                .andExpect(jsonPath("$.quorum.met").value(false)));
        for (String email : List.of("a@example.test", "b@example.test", "c@example.test")) {
            assertFalse(body.contains(email), body);
        }
    }

    @Test
    void submittedReviewsAreListedAndTheOtherReviewerOnlyCounted() throws Exception {
        Submission brief = underReviewBrief();
        settings(brief, 1, 3, null);
        submitted(brief, 1, "first@example.test", 1, "Engineering", ReviewDecision.RECOMMENDED, "Strong case.", T0);
        submitted(brief, 1, "second@example.test", 2, null, ReviewDecision.CHANGES_NEEDED, "Needs costing.",
                T0.plusSeconds(60));
        assign(brief, 1, "waiting@example.test", 3);

        String body = body(list(brief)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviews", hasSize(2)))
                .andExpect(jsonPath("$.reviews[0].reviewerId").value("first@example.test"))
                .andExpect(jsonPath("$.reviews[0].department").value("Engineering"))
                .andExpect(jsonPath("$.reviews[0].position").value(1))
                .andExpect(jsonPath("$.reviews[0].decision").value("RECOMMENDED"))
                .andExpect(jsonPath("$.reviews[0].comment").value("Strong case."))
                .andExpect(jsonPath("$.reviews[0].submittedAt").value("2026-11-10T08:00:00Z"))
                .andExpect(jsonPath("$.reviews[0].followUpNotes").isEmpty())
                .andExpect(jsonPath("$.reviews[1].reviewerId").value("second@example.test"))
                .andExpect(jsonPath("$.reviews[1].department").value(nullValue()))
                .andExpect(jsonPath("$.reviews[1].position").value(2))
                .andExpect(jsonPath("$.reviews[1].decision").value("CHANGES_NEEDED"))
                .andExpect(jsonPath("$.reviews[1].comment").value("Needs costing."))
                .andExpect(jsonPath("$.notSubmittedCount").value(1)));
        assertFalse(body.contains("waiting@example.test"), body);
    }

    // ---- 4, 5, 6: order, notes, decisions ------------------------------------------------

    @Test
    void reviewsAreOrderedBySubmissionTimeThenAssignmentId() throws Exception {
        Submission brief = underReviewBrief();
        settings(brief, 1, 4, null);
        submitted(brief, 1, "pos1@example.test", 1, null, ReviewDecision.RECOMMENDED, "c1", T0.plusSeconds(300));
        submitted(brief, 1, "pos2@example.test", 2, null, ReviewDecision.RECOMMENDED, "c2", T0);
        submitted(brief, 1, "pos3@example.test", 3, null, ReviewDecision.RECOMMENDED, "c3", T0.plusSeconds(100));
        submitted(brief, 1, "pos4@example.test", 4, null, ReviewDecision.RECOMMENDED, "c4", T0.plusSeconds(100));

        JsonNode items = json(body(list(brief).andExpect(status().isOk()))).get("reviews");
        assertEquals(List.of("pos2@example.test", "pos3@example.test", "pos4@example.test", "pos1@example.test"),
                field(items, "reviewerId"));
    }

    @Test
    void followUpNotesAppearUnderTheirOwnReviewInCreatedOrder() throws Exception {
        Submission brief = underReviewBrief();
        settings(brief, 1, 2, null);
        Review withNotes = submitted(brief, 1, "noted@example.test", 1, null, ReviewDecision.RECOMMENDED, "c", T0);
        submitted(brief, 1, "plain@example.test", 2, null, ReviewDecision.RECOMMENDED, "c", T0.plusSeconds(10));
        long late = note(withNotes, "late", "noted@example.test");
        long tieA = note(withNotes, "tie A", "noted@example.test");
        long tieB = note(withNotes, "tie B", "noted@example.test");
        long early = note(withNotes, "early", "noted@example.test");
        setNoteTime(late, T0.plusSeconds(500));
        setNoteTime(tieA, T0.plusSeconds(200));
        setNoteTime(tieB, T0.plusSeconds(200));
        setNoteTime(early, T0.plusSeconds(100));

        list(brief)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviews[0].reviewerId").value("noted@example.test"))
                .andExpect(jsonPath("$.reviews[0].followUpNotes", hasSize(4)))
                .andExpect(jsonPath("$.reviews[0].followUpNotes[0].id").value(early))
                .andExpect(jsonPath("$.reviews[0].followUpNotes[0].note").value("early"))
                .andExpect(jsonPath("$.reviews[0].followUpNotes[0].createdBy").value("noted@example.test"))
                .andExpect(jsonPath("$.reviews[0].followUpNotes[0].createdAt").value("2026-11-10T08:01:40Z"))
                .andExpect(jsonPath("$.reviews[0].followUpNotes[1].id").value(tieA))
                .andExpect(jsonPath("$.reviews[0].followUpNotes[2].id").value(tieB))
                .andExpect(jsonPath("$.reviews[0].followUpNotes[3].id").value(late))
                .andExpect(jsonPath("$.reviews[1].reviewerId").value("plain@example.test"))
                .andExpect(jsonPath("$.reviews[1].followUpNotes").isEmpty());
    }

    @Test
    void allThreeDecisionsAreReturned() throws Exception {
        Submission brief = underReviewBrief();
        settings(brief, 1, 3, null);
        int position = 0;
        for (ReviewDecision decision : ReviewDecision.values()) {
            position++;
            submitted(brief, 1, decision.name().toLowerCase() + "@example.test", position, null, decision, "c",
                    T0.plusSeconds(position));
        }
        JsonNode items = json(body(list(brief).andExpect(status().isOk()))).get("reviews");
        assertEquals(List.of("RECOMMENDED", "CHANGES_NEEDED", "NOT_RECOMMENDED"), field(items, "decision"));
    }

    // ---- 7, 8: quorum and rounds ---------------------------------------------------------

    @Test
    void quorumFollowsSubmissionsAndMatchesTheQuorumEndpoint() throws Exception {
        Submission brief = underReviewBrief();
        settings(brief, 1, 2, null);
        submitted(brief, 1, "one@example.test", 1, null, ReviewDecision.RECOMMENDED, "c", T0);
        ReviewAssignment second = assign(brief, 1, "two@example.test", 2);

        list(brief).andExpect(jsonPath("$.quorum.required").value(2))
                .andExpect(jsonPath("$.quorum.completed").value(1))
                .andExpect(jsonPath("$.quorum.met").value(false));
        assertQuorumMatches(brief);

        second.setStatus(ReviewAssignmentStatus.SUBMITTED);
        assignments.saveAndFlush(second);
        reviews.saveAndFlush(new Review(second.getId(), ReviewDecision.RECOMMENDED, "c", T0.plusSeconds(5)));

        list(brief).andExpect(jsonPath("$.quorum.completed").value(2))
                .andExpect(jsonPath("$.quorum.met").value(true))
                .andExpect(jsonPath("$.notSubmittedCount").value(0));
        assertQuorumMatches(brief);
    }

    @Test
    void defaultShowsTheLatestRoundAndEarlierRoundsKeepTheirOwnData() throws Exception {
        Submission brief = underReviewBrief();
        settings(brief, 1, 2, LocalDate.of(2027, 1, 1));
        submitted(brief, 1, "r1-a@example.test", 1, null, ReviewDecision.NOT_RECOMMENDED, "Round one A", T0);
        submitted(brief, 1, "r1-b@example.test", 2, null, ReviewDecision.CHANGES_NEEDED, "Round one B",
                T0.plusSeconds(10));
        settings.saveAndFlush(new ReviewSettings(brief.getId(), 2, ReviewMode.SEQUENTIAL, 3,
                LocalDate.of(2027, 2, 1), COORDINATOR));
        submitted(brief, 2, "r2-a@example.test", 1, null, ReviewDecision.RECOMMENDED, "Round two A",
                T0.plusSeconds(20));
        assign(brief, 2, "r2-b@example.test", 2);

        String latest = body(list(brief)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.round").value(2))
                .andExpect(jsonPath("$.latestRound").value(2))
                .andExpect(jsonPath("$.mode").value("SEQUENTIAL"))
                .andExpect(jsonPath("$.reviewsNeeded").value(3))
                .andExpect(jsonPath("$.dueDate").value("2027-02-01"))
                .andExpect(jsonPath("$.reviews", hasSize(1)))
                .andExpect(jsonPath("$.reviews[0].reviewerId").value("r2-a@example.test"))
                .andExpect(jsonPath("$.quorum.required").value(3))
                .andExpect(jsonPath("$.quorum.completed").value(1))
                .andExpect(jsonPath("$.notSubmittedCount").value(1)));
        assertFalse(latest.contains("Round one"), latest);
        assertFalse(latest.contains("r1-"), latest);

        mockMvc.perform(get(REVIEWS, brief.getId()).param("round", "1").with(coordinator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.round").value(1))
                .andExpect(jsonPath("$.latestRound").value(2))
                .andExpect(jsonPath("$.mode").value("PARALLEL"))
                .andExpect(jsonPath("$.reviewsNeeded").value(2))
                .andExpect(jsonPath("$.dueDate").value("2027-01-01"))
                .andExpect(jsonPath("$.reviews", hasSize(2)))
                .andExpect(jsonPath("$.reviews[0].comment").value("Round one A"))
                .andExpect(jsonPath("$.reviews[1].comment").value("Round one B"))
                .andExpect(jsonPath("$.quorum.required").value(2))
                .andExpect(jsonPath("$.quorum.completed").value(2))
                .andExpect(jsonPath("$.quorum.met").value(true))
                .andExpect(jsonPath("$.notSubmittedCount").value(0));
        mockMvc.perform(get(REVIEWS, brief.getId()).param("round", "2").with(coordinator()))
                .andExpect(jsonPath("$.reviews", hasSize(1)));
    }

    // ---- 9, 10, 11: parameters and security ----------------------------------------------

    @Test
    void invalidAndMissingRoundsAreRejected() throws Exception {
        Submission brief = underReviewBrief();
        settings(brief, 1, 1, null);
        Submission unconfigured = underReviewBrief();

        mockMvc.perform(get(REVIEWS, brief.getId()).param("round", "0").with(coordinator()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("round")));
        mockMvc.perform(get(REVIEWS, brief.getId()).param("round", "-3").with(coordinator()))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(get(REVIEWS, brief.getId()).param("round", "abc").with(coordinator()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"))
                .andExpect(jsonPath("$.detail").value("A request parameter has an invalid value."));
        for (Submission target : List.of(brief, unconfigured)) {
            mockMvc.perform(get(REVIEWS, target.getId()).param("round", "5").with(coordinator()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value("That review round does not exist for this brief."));
        }
    }

    @Test
    void administratorCanRead() throws Exception {
        Submission brief = underReviewBrief();
        settings(brief, 1, 1, null);
        submitted(brief, 1, "r@example.test", 1, null, ReviewDecision.RECOMMENDED, "Admin can see this.", T0);

        mockMvc.perform(get(REVIEWS, brief.getId()).with(user("admin@example.test").roles("ADMINISTRATOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviews[0].comment").value("Admin can see this."));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "REVIEWER", "FACT_FINDING_OFFICER", "SUBMITTER", "RECEPTIONIST", "SECTOR_OFFICER", "IP_LIAISON", "OBSERVER"
    })
    void otherRolesAreForbidden(String role) throws Exception {
        Submission brief = underReviewBrief();
        settings(brief, 1, 1, null);
        submitted(brief, 1, "r@example.test", 1, null, ReviewDecision.RECOMMENDED, "Internal.", T0);

        mockMvc.perform(get(REVIEWS, brief.getId()).with(user("r@example.test").roles(role)))
                .andExpect(status().isForbidden())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.title").value("Forbidden"))
                .andExpect(jsonPath("$.detail").value("You do not have permission to perform this action."));
    }

    @Test
    void unauthenticatedUnknownAndMalformedRequests() throws Exception {
        mockMvc.perform(get(REVIEWS, UNKNOWN_BRIEF))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("A valid bearer token is required."));
        mockMvc.perform(get(REVIEWS, UNKNOWN_BRIEF).with(coordinator()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Submission not found"))
                .andExpect(jsonPath("$.detail").value("The requested submission was not found."));
        mockMvc.perform(get("/cases/not-a-uuid/reviews").with(coordinator()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"));
    }

    // ---- 12: the POST on the same path still belongs to the reviewer ---------------------

    @Test
    void postOnTheSamePathIsStillTheReviewersSubmit() throws Exception {
        Submission brief = underReviewBrief();
        settings(brief, 1, 1, null);
        assign(brief, 1, "poster@example.test", 1);

        mockMvc.perform(post(REVIEWS, brief.getId()).with(user("poster@example.test").roles("REVIEWER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"RECOMMENDED\",\"comment\":\"Posted.\",\"assignmentVersion\":0}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reviewerId").value("poster@example.test"));
        mockMvc.perform(post(REVIEWS, brief.getId()).with(coordinator())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"RECOMMENDED\",\"comment\":\"x\",\"assignmentVersion\":0}"))
                .andExpect(status().isForbidden());
        list(brief).andExpect(jsonPath("$.reviews[0].comment").value("Posted."));
    }

    // ---- 13: read-only -------------------------------------------------------------------

    @Test
    void readingNeverWrites() throws Exception {
        Submission brief = underReviewBrief();
        settings(brief, 1, 2, null);
        Review review = submitted(brief, 1, "r@example.test", 1, null, ReviewDecision.RECOMMENDED, "c", T0);
        note(review, "n", "r@example.test");
        assign(brief, 1, "w@example.test", 2);
        Map<String, Object> before = snapshot();

        list(brief).andExpect(status().isOk());
        mockMvc.perform(get(REVIEWS, brief.getId()).param("round", "1").with(coordinator())).andExpect(status().isOk());
        mockMvc.perform(get(REVIEWS, brief.getId()).param("round", "9").with(coordinator()))
                .andExpect(status().isNotFound());

        assertEquals(before, snapshot());
    }

    // ---- 15: comments and notes stay out of every other response -------------------------

    @Test
    void commentAndNoteTextAppearHereButNowhereElse() throws Exception {
        Submission brief = underReviewBrief();
        mockMvc.perform(put("/cases/{id}/review-settings", brief.getId()).with(coordinator())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"PARALLEL\",\"reviewsNeeded\":1}"))
                .andExpect(status().isOk());
        users.saveAndFlush(new ApiUser(UUID.randomUUID(), "marked@example.test", "not-a-real-hash",
                ApiUserRole.REVIEWER, Instant.parse("2026-11-01T08:00:00Z")));
        mockMvc.perform(post("/cases/{id}/reviewers", brief.getId()).with(coordinator())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reviewerId\":\"marked@example.test\"}"))
                .andExpect(status().isCreated());
        RequestPostProcessor reviewer = user("marked@example.test").roles("REVIEWER");
        mockMvc.perform(post(REVIEWS, brief.getId()).with(reviewer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"RECOMMENDED\",\"comment\":\"Comment " + MARKER + "\",\"assignmentVersion\":0}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/cases/{id}/my-review/follow-up-notes", brief.getId()).with(reviewer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"Note " + MARKER + "\"}"))
                .andExpect(status().isCreated());

        String here = body(list(brief).andExpect(status().isOk()));
        assertTrue(here.contains("Comment " + MARKER), here);
        assertTrue(here.contains("Note " + MARKER), here);

        RequestPostProcessor admin = user("admin@example.test").roles("ADMINISTRATOR");
        List<String> elsewhere = List.of(
                body(mockMvc.perform(get("/cases/{id}/reviewers", brief.getId()).with(coordinator()))
                        .andExpect(status().isOk())),
                body(mockMvc.perform(get("/cases/{id}/activity", brief.getId()).with(admin))
                        .andExpect(status().isOk())),
                body(mockMvc.perform(get("/cases/{id}/activity", brief.getId()).with(user(SUBMITTER).roles("SUBMITTER")))
                        .andExpect(status().isOk())),
                body(mockMvc.perform(get("/activity").param("entityId", brief.getId().toString()).with(admin))
                        .andExpect(status().isOk())),
                body(mockMvc.perform(get("/activity").with(admin)).andExpect(status().isOk())));
        for (String response : elsewhere) {
            assertFalse(response.contains(MARKER), response);
        }
    }

    // ---- 16: contract --------------------------------------------------------------------

    @Test
    void jsonFieldNamesMatchTheRecordsExactly() throws Exception {
        Submission brief = underReviewBrief();
        settings(brief, 1, 1, null);
        Review review = submitted(brief, 1, "r@example.test", 1, "Dept", ReviewDecision.RECOMMENDED, "c", T0);
        note(review, "n", "r@example.test");

        JsonNode response = json(body(list(brief).andExpect(status().isOk())));
        assertEquals(Set.of("briefId", "configured", "round", "mode", "reviewsNeeded", "dueDate", "latestRound",
                "quorum", "reviews", "notSubmittedCount"), names(response));
        assertEquals(Set.of("required", "completed", "met"), names(response.get("quorum")));
        JsonNode item = response.get("reviews").get(0);
        assertEquals(Set.of("reviewerId", "department", "position", "decision", "comment", "submittedAt",
                "followUpNotes"), names(item));
        assertEquals(Set.of("id", "note", "createdBy", "createdAt"), names(item.get("followUpNotes").get(0)));
    }

    // ---- helpers -------------------------------------------------------------------------

    private ResultActions list(Submission brief) throws Exception {
        return mockMvc.perform(get(REVIEWS, brief.getId()).with(coordinator()));
    }

    private void assertQuorumMatches(Submission brief) throws Exception {
        JsonNode here = json(body(list(brief))).get("quorum");
        JsonNode endpoint = json(body(mockMvc.perform(get("/cases/{id}/review-quorum", brief.getId()).with(coordinator()))
                .andExpect(status().isOk())));
        assertEquals(endpoint.get("required").asInt(), here.get("required").asInt());
        assertEquals(endpoint.get("completed").asInt(), here.get("completed").asInt());
        assertEquals(endpoint.get("met").asBoolean(), here.get("met").asBoolean());
    }

    private static RequestPostProcessor coordinator() {
        return user(COORDINATOR).roles("INNOVATION_COORDINATOR");
    }

    private void settings(Submission brief, int round, int reviewsNeeded, LocalDate dueDate) {
        settings.saveAndFlush(new ReviewSettings(brief.getId(), round, ReviewMode.PARALLEL, reviewsNeeded, dueDate,
                COORDINATOR));
    }

    private ReviewAssignment assign(Submission brief, int round, String reviewer, int position) {
        return assignments.saveAndFlush(new ReviewAssignment(brief.getId(), round, reviewer, null, position, COORDINATOR));
    }

    private Review submitted(Submission brief, int round, String reviewer, int position, String department,
                             ReviewDecision decision, String comment, Instant submittedAt) {
        ReviewAssignment assignment = new ReviewAssignment(brief.getId(), round, reviewer, department, position,
                COORDINATOR);
        assignment.setStatus(ReviewAssignmentStatus.SUBMITTED);
        ReviewAssignment saved = assignments.saveAndFlush(assignment);
        return reviews.saveAndFlush(new Review(saved.getId(), decision, comment, submittedAt));
    }

    private long note(Review review, String text, String author) {
        return notes.saveAndFlush(new ReviewFollowUpNote(review.getId(), text, author)).getId();
    }

    private void setNoteTime(long noteId, Instant createdAt) {
        entityManager.flush();
        jdbcTemplate.update("UPDATE review_follow_up_notes SET created_at = ? WHERE id = ?",
                Timestamp.from(createdAt), noteId);
        entityManager.clear();
    }

    private Map<String, Object> snapshot() {
        entityManager.flush();
        return jdbcTemplate.queryForMap("""
                SELECT (SELECT count(*) FROM submissions) AS submissions,
                       (SELECT count(*) FROM review_settings) AS settings,
                       (SELECT count(*) FROM review_assignments) AS assignments,
                       (SELECT count(*) FROM reviews) AS reviews,
                       (SELECT count(*) FROM review_follow_up_notes) AS notes,
                       (SELECT count(*) FROM audit_log) AS audit,
                       (SELECT coalesce(sum(version), 0) FROM review_assignments) AS assignment_versions,
                       (SELECT coalesce(sum(version), 0) FROM reviews) AS review_versions
                """);
    }

    private static Set<String> names(JsonNode node) {
        return new HashSet<>(node.propertyNames());
    }

    private static List<String> field(JsonNode items, String name) {
        List<String> values = new ArrayList<>();
        items.forEach(item -> values.add(item.get(name).asString()));
        return values;
    }

    private static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }

    private JsonNode json(String body) {
        return objectMapper.readTree(body);
    }

    private Submission underReviewBrief() {
        Submission brief = new Submission(
                UUID.randomUUID(), SubmissionCategory.GENERAL, "Coordinator reviews brief", "Purpose", "Problem",
                "receptionist@example.test", SUBMITTER, null, null, null, null, Instant.parse("2026-11-01T08:00:00Z"));
        brief.applyIntakeDetails(null, null, null, null, null, null, "2026-01");
        brief.register("CASE-CR-" + UUID.randomUUID().toString().substring(0, 8),
                Instant.parse("2026-11-01T09:00:00Z"), "receptionist@example.test");
        Instant time = Instant.parse("2026-11-01T10:00:00Z");
        brief.transitionTo(SubmissionStage.FORWARDED, time);
        brief.transitionTo(SubmissionStage.IN_FACT_FINDING, time);
        brief.transitionTo(SubmissionStage.IN_REVIEW, time);
        return submissions.saveAndFlush(brief);
    }
}

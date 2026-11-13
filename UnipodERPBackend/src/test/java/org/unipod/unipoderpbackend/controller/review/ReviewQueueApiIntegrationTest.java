package org.unipod.unipoderpbackend.controller.review;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
import org.unipod.unipoderpbackend.model.review.ReviewAssignment;
import org.unipod.unipoderpbackend.model.review.ReviewMode;
import org.unipod.unipoderpbackend.model.review.ReviewSettings;
import org.unipod.unipoderpbackend.model.submission.Submission;
import org.unipod.unipoderpbackend.model.submission.SubmissionCategory;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;
import org.unipod.unipoderpbackend.repository.review.ReviewAssignmentRepository;
import org.unipod.unipoderpbackend.repository.review.ReviewSettingsRepository;
import org.unipod.unipoderpbackend.repository.submission.SubmissionRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReviewQueueApiIntegrationTest {
    private static final String QUEUE = "/queues/reviews";
    private static final String COORDINATOR = "coord@example.test";
    private static final String REVIEWER = "reviewer@example.test";
    private static final String OTHER = "other-reviewer@example.test";
    private static final String THIRD = "third-reviewer@example.test";
    private static final LocalDate DUE = LocalDate.of(2027, 3, 1);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SubmissionRepository submissions;

    @Autowired
    private ReviewSettingsRepository settings;

    @Autowired
    private ReviewAssignmentRepository assignments;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    // ---- contents and counters ----------------------------------------------------------

    @Test
    void reviewerWithoutAssignmentsGetsAnEmptyQueue() throws Exception {
        queue(REVIEWER)
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.assignedToMe").value(0))
                .andExpect(jsonPath("$.waitingForMyReview").value(0))
                .andExpect(jsonPath("$.rows.items").isEmpty())
                .andExpect(jsonPath("$.rows.page").value(0))
                .andExpect(jsonPath("$.rows.size").value(25))
                .andExpect(jsonPath("$.rows.totalElements").value(0))
                .andExpect(jsonPath("$.rows.totalPages").value(0));
    }

    @Test
    void reviewerAssignedToTwoBriefsSeesBothWithEveryField() throws Exception {
        Submission solar = brief("Solar dryer", SubmissionCategory.STUDENT, SubmissionStage.IN_REVIEW);
        Submission mill = brief("Grain mill", SubmissionCategory.GENERAL, SubmissionStage.IN_REVIEW);
        settings(solar, 1, ReviewMode.PARALLEL, DUE);
        settings(mill, 1, ReviewMode.PARALLEL, DUE.plusDays(7));
        assign(solar, 1, REVIEWER, 1);
        assign(mill, 1, OTHER, 1);
        assign(mill, 1, REVIEWER, 2);

        queue(REVIEWER)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedToMe").value(2))
                .andExpect(jsonPath("$.waitingForMyReview").value(2))
                .andExpect(jsonPath("$.rows.items", hasSize(2)))
                .andExpect(jsonPath("$.rows.totalElements").value(2))
                .andExpect(jsonPath("$.rows.items[0].briefId").value(solar.getId().toString()))
                .andExpect(jsonPath("$.rows.items[0].title").value("Solar dryer"))
                .andExpect(jsonPath("$.rows.items[0].trackingReference").value(solar.getCode()))
                .andExpect(jsonPath("$.rows.items[0].briefType").value("student"))
                .andExpect(jsonPath("$.rows.items[0].round").value(1))
                .andExpect(jsonPath("$.rows.items[0].position").value(1))
                .andExpect(jsonPath("$.rows.items[0].dueDate").value("2027-03-01"))
                .andExpect(jsonPath("$.rows.items[0].status").value("NOT_STARTED"))
                .andExpect(jsonPath("$.rows.items[0].stage").value("IN_REVIEW"))
                .andExpect(jsonPath("$.rows.items[0].canSubmit").value(true))
                .andExpect(jsonPath("$.rows.items[1].briefId").value(mill.getId().toString()))
                .andExpect(jsonPath("$.rows.items[1].title").value("Grain mill"))
                .andExpect(jsonPath("$.rows.items[1].trackingReference").value(mill.getCode()))
                .andExpect(jsonPath("$.rows.items[1].briefType").value("general"))
                .andExpect(jsonPath("$.rows.items[1].position").value(2))
                .andExpect(jsonPath("$.rows.items[1].dueDate").value("2027-03-08"))
                .andExpect(jsonPath("$.rows.items[1].canSubmit").value(true));
    }

    @Test
    void submittedRowStaysButCannotBeSubmittedAgain() throws Exception {
        Submission first = brief("First", SubmissionCategory.GENERAL, SubmissionStage.IN_REVIEW);
        Submission second = brief("Second", SubmissionCategory.GENERAL, SubmissionStage.IN_REVIEW);
        settings(first, 1, ReviewMode.PARALLEL, DUE);
        settings(second, 1, ReviewMode.PARALLEL, DUE.plusDays(1));
        assign(first, 1, REVIEWER, 1);
        assign(second, 1, REVIEWER, 1);

        submitReview(first, REVIEWER);

        queue(REVIEWER)
                .andExpect(jsonPath("$.assignedToMe").value(2))
                .andExpect(jsonPath("$.waitingForMyReview").value(1))
                .andExpect(jsonPath("$.rows.items[0].briefId").value(first.getId().toString()))
                .andExpect(jsonPath("$.rows.items[0].status").value("SUBMITTED"))
                .andExpect(jsonPath("$.rows.items[0].canSubmit").value(false))
                .andExpect(jsonPath("$.rows.items[1].status").value("NOT_STARTED"))
                .andExpect(jsonPath("$.rows.items[1].canSubmit").value(true));
    }

    @Test
    void onlyTheCallersOwnRowsAppear() throws Exception {
        Submission shared = brief("Shared", SubmissionCategory.GENERAL, SubmissionStage.IN_REVIEW);
        Submission theirs = brief("Theirs only", SubmissionCategory.GENERAL, SubmissionStage.IN_REVIEW);
        settings(shared, 1, ReviewMode.PARALLEL, DUE);
        settings(theirs, 1, ReviewMode.PARALLEL, DUE);
        assign(shared, 1, OTHER, 1);
        assign(shared, 1, REVIEWER, 2);
        assign(theirs, 1, OTHER, 1);

        String body = body(queue(REVIEWER)
                .andExpect(jsonPath("$.assignedToMe").value(1))
                .andExpect(jsonPath("$.rows.items", hasSize(1)))
                .andExpect(jsonPath("$.rows.items[0].briefId").value(shared.getId().toString())));
        assertFalse(body.contains(OTHER), body);
        assertFalse(body.contains(theirs.getId().toString()), body);
        assertFalse(body.contains(REVIEWER), body);

        queue(OTHER).andExpect(jsonPath("$.assignedToMe").value(2));
    }

    // ---- rounds and release rule ---------------------------------------------------------

    @Test
    void onlyTheLatestRoundCounts() throws Exception {
        Submission brief = brief("Two rounds", SubmissionCategory.GENERAL, SubmissionStage.IN_REVIEW);
        settings(brief, 1, ReviewMode.PARALLEL, DUE);
        assign(brief, 1, REVIEWER, 1);
        queue(REVIEWER).andExpect(jsonPath("$.assignedToMe").value(1));

        settings(brief, 2, ReviewMode.PARALLEL, DUE.plusDays(30));
        assign(brief, 2, OTHER, 1);
        queue(REVIEWER)
                .andExpect(jsonPath("$.assignedToMe").value(0))
                .andExpect(jsonPath("$.rows.items").isEmpty());

        assign(brief, 2, REVIEWER, 2);
        queue(REVIEWER)
                .andExpect(jsonPath("$.assignedToMe").value(1))
                .andExpect(jsonPath("$.rows.items", hasSize(1)))
                .andExpect(jsonPath("$.rows.items[0].round").value(2))
                .andExpect(jsonPath("$.rows.items[0].position").value(2))
                .andExpect(jsonPath("$.rows.items[0].dueDate").value("2027-03-31"));
    }

    @Test
    void sequentialRowsAreReleasedOnlyAfterEveryEarlierReviewerSubmits() throws Exception {
        Submission brief = brief("In turn", SubmissionCategory.GENERAL, SubmissionStage.IN_REVIEW);
        settings(brief, 1, ReviewMode.SEQUENTIAL, DUE);
        assign(brief, 1, OTHER, 1);
        assign(brief, 1, REVIEWER, 2);
        assign(brief, 1, THIRD, 3);

        queue(OTHER).andExpect(jsonPath("$.assignedToMe").value(1))
                .andExpect(jsonPath("$.rows.items[0].canSubmit").value(true));
        queue(REVIEWER).andExpect(jsonPath("$.assignedToMe").value(0))
                .andExpect(jsonPath("$.waitingForMyReview").value(0))
                .andExpect(jsonPath("$.rows.items").isEmpty());
        queue(THIRD).andExpect(jsonPath("$.assignedToMe").value(0));

        submitReview(brief, OTHER);

        queue(REVIEWER)
                .andExpect(jsonPath("$.assignedToMe").value(1))
                .andExpect(jsonPath("$.waitingForMyReview").value(1))
                .andExpect(jsonPath("$.rows.items[0].position").value(2))
                .andExpect(jsonPath("$.rows.items[0].canSubmit").value(true));
        queue(THIRD).andExpect(jsonPath("$.assignedToMe").value(0));

        submitReview(brief, REVIEWER);
        queue(THIRD).andExpect(jsonPath("$.assignedToMe").value(1))
                .andExpect(jsonPath("$.rows.items[0].canSubmit").value(true));
    }

    @Test
    void sequentialWithAGapReleasesTheLowestRemainingPositionImmediately() throws Exception {
        Submission brief = brief("Gap", SubmissionCategory.GENERAL, SubmissionStage.IN_REVIEW);
        settings(brief, 1, ReviewMode.SEQUENTIAL, DUE);
        assign(brief, 1, REVIEWER, 2);
        assign(brief, 1, THIRD, 3);

        queue(REVIEWER)
                .andExpect(jsonPath("$.assignedToMe").value(1))
                .andExpect(jsonPath("$.rows.items[0].position").value(2))
                .andExpect(jsonPath("$.rows.items[0].canSubmit").value(true));
        queue(THIRD).andExpect(jsonPath("$.assignedToMe").value(0));
    }

    @Test
    void parallelRowsAllAppearAtOnce() throws Exception {
        Submission brief = brief("Together", SubmissionCategory.GENERAL, SubmissionStage.IN_REVIEW);
        settings(brief, 1, ReviewMode.PARALLEL, DUE);
        assign(brief, 1, OTHER, 1);
        assign(brief, 1, REVIEWER, 2);
        assign(brief, 1, THIRD, 3);

        for (String reviewer : List.of(OTHER, REVIEWER, THIRD)) {
            queue(reviewer)
                    .andExpect(jsonPath("$.assignedToMe").value(1))
                    .andExpect(jsonPath("$.waitingForMyReview").value(1))
                    .andExpect(jsonPath("$.rows.items[0].canSubmit").value(true));
        }
    }

    @Test
    void briefNotUnderReviewShowsItsStageAndCannotBeSubmitted() throws Exception {
        Submission returned = brief("Returned", SubmissionCategory.GENERAL, SubmissionStage.IN_REVIEW);
        settings(returned, 1, ReviewMode.PARALLEL, DUE);
        assign(returned, 1, REVIEWER, 1);
        returned.transitionTo(SubmissionStage.IMPROVEMENT_REQUESTED, Instant.parse("2026-11-02T10:00:00Z"));
        submissions.saveAndFlush(returned);
        Submission open = brief("Open", SubmissionCategory.GENERAL, SubmissionStage.IN_REVIEW);
        settings(open, 1, ReviewMode.PARALLEL, DUE.plusDays(1));
        assign(open, 1, REVIEWER, 1);

        queue(REVIEWER)
                .andExpect(jsonPath("$.assignedToMe").value(2))
                .andExpect(jsonPath("$.waitingForMyReview").value(1))
                .andExpect(jsonPath("$.rows.items[0].briefId").value(returned.getId().toString()))
                .andExpect(jsonPath("$.rows.items[0].stage").value("IMPROVEMENT_REQUESTED"))
                .andExpect(jsonPath("$.rows.items[0].status").value("NOT_STARTED"))
                .andExpect(jsonPath("$.rows.items[0].canSubmit").value(false))
                .andExpect(jsonPath("$.rows.items[1].canSubmit").value(true));
    }

    // ---- ordering and paging ------------------------------------------------------------

    @Test
    void orderedByDueDateWithNoDueDateLastAndNewestAssignmentFirstOnTies() throws Exception {
        Submission noDueOld = queued("no due, older", null);
        Submission late = queued("due late", DUE.plusDays(20));
        Submission tieOld = queued("tie, older", DUE);
        Submission tieNew = queued("tie, newer", DUE);
        Submission noDueNew = queued("no due, newer", null);

        JsonNode items = json(body(queue(REVIEWER))).get("rows").get("items");
        assertEquals(List.of(tieNew.getId(), tieOld.getId(), late.getId(), noDueNew.getId(), noDueOld.getId()),
                briefIds(items));
        assertTrue(items.get(3).get("dueDate").isNull());
        assertTrue(items.get(4).get("dueDate").isNull());
    }

    @Test
    void pagesSplitTheQueueWithoutOverlapAndCountersCoverAllRows() throws Exception {
        for (int i = 0; i < 25; i++) {
            Submission brief = brief("Paged " + i, SubmissionCategory.GENERAL, SubmissionStage.IN_REVIEW);
            settings(brief, 1, ReviewMode.PARALLEL, DUE.plusDays(i % 4));
            assign(brief, 1, REVIEWER, 1);
            if (i % 5 == 0) {
                brief.transitionTo(SubmissionStage.IMPROVEMENT_REQUESTED, Instant.parse("2026-11-02T10:00:00Z"));
                submissions.saveAndFlush(brief);
            }
        }

        Set<UUID> seen = new HashSet<>();
        int[] expectedSizes = {10, 10, 5};
        for (int page = 0; page < 3; page++) {
            JsonNode response = json(body(queue(REVIEWER, Map.of("page", String.valueOf(page), "size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.assignedToMe").value(25))
                    .andExpect(jsonPath("$.waitingForMyReview").value(20))
                    .andExpect(jsonPath("$.rows.page").value(page))
                    .andExpect(jsonPath("$.rows.size").value(10))
                    .andExpect(jsonPath("$.rows.totalElements").value(25))
                    .andExpect(jsonPath("$.rows.totalPages").value(3))));
            List<UUID> ids = briefIds(response.get("rows").get("items"));
            assertEquals(expectedSizes[page], ids.size());
            for (UUID id : ids) {
                assertTrue(seen.add(id), "row appeared twice: " + id);
            }
        }
        assertEquals(25, seen.size());
        queue(REVIEWER, Map.of("page", "3", "size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedToMe").value(25))
                .andExpect(jsonPath("$.rows.items").isEmpty());
    }

    // ---- identity and references ---------------------------------------------------------

    @Test
    void tokenEmailWithDifferentLetterCaseStillMatches() throws Exception {
        Submission brief = brief("Case", SubmissionCategory.GENERAL, SubmissionStage.IN_REVIEW);
        settings(brief, 1, ReviewMode.PARALLEL, DUE);
        assign(brief, 1, REVIEWER, 1);

        queue("Reviewer@EXAMPLE.Test")
                .andExpect(jsonPath("$.assignedToMe").value(1))
                .andExpect(jsonPath("$.rows.items[0].briefId").value(brief.getId().toString()));
    }

    @Test
    void briefWithoutACodeHasANullTrackingReference() throws Exception {
        Submission draft = submissions.saveAndFlush(newSubmission("Not yet registered", SubmissionCategory.GENERAL));
        settings(draft, 1, ReviewMode.PARALLEL, DUE);
        assign(draft, 1, REVIEWER, 1);

        queue(REVIEWER)
                .andExpect(jsonPath("$.rows.items[0].briefId").value(draft.getId().toString()))
                .andExpect(jsonPath("$.rows.items[0].trackingReference").value(nullValue()))
                .andExpect(jsonPath("$.rows.items[0].stage").value("DRAFT"))
                .andExpect(jsonPath("$.rows.items[0].canSubmit").value(false));
    }

    // ---- security and parameters ---------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {
            "INNOVATION_COORDINATOR", "ADMINISTRATOR", "FACT_FINDING_OFFICER", "SUBMITTER",
            "RECEPTIONIST", "SECTOR_OFFICER", "IP_LIAISON", "OBSERVER"
    })
    void otherRolesAreForbidden(String role) throws Exception {
        mockMvc.perform(get(QUEUE).with(user(REVIEWER).roles(role)))
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

    @ParameterizedTest
    @CsvSource({"page, -1", "size, 0", "size, 101"})
    void outOfRangePagingIsRejectedLikeOtherListEndpoints(String parameter, String value) throws Exception {
        for (String path : List.of(QUEUE, "/queues/reception")) {
            RequestPostProcessor caller = path.equals(QUEUE)
                    ? reviewer(REVIEWER) : user(COORDINATOR).roles("INNOVATION_COORDINATOR");
            mockMvc.perform(get(path).param(parameter, value).with(caller))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.title").value("Validation failed"))
                    .andExpect(jsonPath("$.detail").value("One or more request values are invalid."))
                    .andExpect(jsonPath("$.errors[0].field").value(parameter));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"page", "size"})
    void malformedPagingIsABadRequestLikeOtherListEndpoints(String parameter) throws Exception {
        for (String path : List.of(QUEUE, "/queues/reception")) {
            RequestPostProcessor caller = path.equals(QUEUE)
                    ? reviewer(REVIEWER) : user(COORDINATOR).roles("INNOVATION_COORDINATOR");
            mockMvc.perform(get(path).param(parameter, "abc").with(caller))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Invalid request"))
                    .andExpect(jsonPath("$.detail").value("A request parameter has an invalid value."));
        }
    }

    @Test
    void largestAllowedPageSizeIsAccepted() throws Exception {
        queue(REVIEWER, Map.of("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows.size").value(100));
    }

    // ---- read-only and contract ----------------------------------------------------------

    @Test
    void queueNeverWrites() throws Exception {
        Submission brief = brief("Untouched", SubmissionCategory.GENERAL, SubmissionStage.IN_REVIEW);
        settings(brief, 1, ReviewMode.SEQUENTIAL, DUE);
        assign(brief, 1, REVIEWER, 1);
        assign(brief, 1, OTHER, 2);
        submitReview(brief, REVIEWER);
        Map<String, Object> before = snapshot();

        for (String caller : List.of(REVIEWER, OTHER, THIRD)) {
            queue(caller).andExpect(status().isOk());
        }
        queue(REVIEWER, Map.of("page", "1", "size", "1")).andExpect(status().isOk());

        assertEquals(before, snapshot());
    }

    @Test
    void jsonFieldNamesMatchTheRecordsExactly() throws Exception {
        Submission brief = brief("Contract", SubmissionCategory.GENERAL, SubmissionStage.IN_REVIEW);
        settings(brief, 1, ReviewMode.PARALLEL, DUE);
        assign(brief, 1, REVIEWER, 1);

        JsonNode response = json(body(queue(REVIEWER)));
        assertEquals(Set.of("assignedToMe", "waitingForMyReview", "rows"), fieldNames(response));
        assertEquals(Set.of("items", "page", "size", "totalElements", "totalPages"), fieldNames(response.get("rows")));
        assertEquals(Set.of("briefId", "title", "trackingReference", "briefType", "round", "position",
                        "dueDate", "status", "stage", "canSubmit"),
                fieldNames(response.get("rows").get("items").get(0)));
    }

    // ---- helpers -------------------------------------------------------------------------

    private ResultActions queue(String principal) throws Exception {
        return queue(principal, Map.of());
    }

    private ResultActions queue(String principal, Map<String, String> params) throws Exception {
        var request = get(QUEUE).with(reviewer(principal));
        params.forEach(request::param);
        return mockMvc.perform(request);
    }

    private void submitReview(Submission brief, String reviewer) throws Exception {
        long version = json(body(mockMvc.perform(get("/cases/{id}/my-review", brief.getId()).with(reviewer(reviewer)))
                .andExpect(status().isOk()))).get("assignmentVersion").asLong();
        mockMvc.perform(post("/cases/{id}/reviews", brief.getId()).with(reviewer(reviewer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"RECOMMENDED\",\"comment\":\"Fine.\",\"assignmentVersion\":"
                                + version + "}"))
                .andExpect(status().isCreated());
    }

    private Map<String, Object> snapshot() {
        return jdbcTemplate.queryForMap("""
                SELECT (SELECT count(*) FROM submissions) AS submissions,
                       (SELECT count(*) FROM review_settings) AS settings,
                       (SELECT count(*) FROM review_assignments) AS assignments,
                       (SELECT count(*) FROM reviews) AS reviews,
                       (SELECT count(*) FROM review_follow_up_notes) AS notes,
                       (SELECT coalesce(sum(version), 0) FROM review_assignments) AS assignment_versions,
                       (SELECT coalesce(sum(version), 0) FROM review_settings) AS settings_versions
                """);
    }

    private static RequestPostProcessor reviewer(String email) {
        return user(email).roles("REVIEWER");
    }

    private Submission queued(String title, LocalDate dueDate) {
        Submission brief = brief(title, SubmissionCategory.GENERAL, SubmissionStage.IN_REVIEW);
        settings(brief, 1, ReviewMode.PARALLEL, dueDate);
        assign(brief, 1, REVIEWER, 1);
        return brief;
    }

    private void settings(Submission brief, int round, ReviewMode mode, LocalDate dueDate) {
        settings.saveAndFlush(new ReviewSettings(brief.getId(), round, mode, 1, dueDate, COORDINATOR));
    }

    private void assign(Submission brief, int round, String reviewer, int position) {
        assignments.saveAndFlush(new ReviewAssignment(brief.getId(), round, reviewer, "Engineering", position, COORDINATOR));
    }

    private static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }

    private JsonNode json(String body) {
        return objectMapper.readTree(body);
    }

    private static Set<String> fieldNames(JsonNode node) {
        return new HashSet<>(node.propertyNames());
    }

    private static List<UUID> briefIds(JsonNode items) {
        return StreamSupport.stream(items.spliterator(), false)
                .map(item -> UUID.fromString(item.get("briefId").asString()))
                .toList();
    }

    private Submission newSubmission(String title, SubmissionCategory category) {
        return new Submission(
                UUID.randomUUID(),
                category,
                title,
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
    }

    private Submission brief(String title, SubmissionCategory category, SubmissionStage target) {
        Submission brief = newSubmission(title, category);
        if (category == SubmissionCategory.STUDENT) {
            return studentBrief(title, target);
        }
        brief.applyIntakeDetails(null, null, null, null, null, null, "2026-01");
        brief.register("CASE-RQ-" + UUID.randomUUID().toString().substring(0, 8),
                Instant.parse("2026-11-01T09:00:00Z"), "receptionist@example.test");
        return advance(brief, target);
    }

    private Submission studentBrief(String title, SubmissionStage target) {
        Submission brief = new Submission(
                UUID.randomUUID(),
                SubmissionCategory.STUDENT,
                title,
                "Purpose",
                "Problem",
                "receptionist@example.test",
                "submitter@example.test",
                "STU-0001",
                "BSc Engineering",
                "Dr. Supervisor",
                org.unipod.unipoderpbackend.model.submission.RecommendationLetterStatus.VERIFIED,
                Instant.parse("2026-11-01T08:00:00Z")
        );
        brief.applyIntakeDetails(null, null, null, null, null, null, "2026-01");
        brief.register("CASE-RQ-" + UUID.randomUUID().toString().substring(0, 8),
                Instant.parse("2026-11-01T09:00:00Z"), "receptionist@example.test");
        return advance(brief, target);
    }

    private Submission advance(Submission brief, SubmissionStage target) {
        Instant time = Instant.parse("2026-11-01T10:00:00Z");
        List<SubmissionStage> path = new ArrayList<>(List.of(
                SubmissionStage.FORWARDED, SubmissionStage.IN_FACT_FINDING, SubmissionStage.IN_REVIEW));
        for (SubmissionStage stage : path) {
            brief.transitionTo(stage, time);
            if (stage == target) {
                break;
            }
        }
        return submissions.saveAndFlush(brief);
    }
}

package org.unipod.unipoderpbackend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.model.submission.Submission;
import org.unipod.unipoderpbackend.model.submission.SubmissionCategory;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;
import org.unipod.unipoderpbackend.repository.submission.SubmissionRepository;
import tools.jackson.databind.ObjectMapper;

/**
 * Improved versions of a brief are numbered 2, 3, 4, ... per brief (the original brief is version 1),
 * independent of how many information replies raised briefVersion before the first improvement.
 * Information can only be requested while a brief is SUBMITTED, so information replies come before
 * forwarding; the improvement cycles follow through the real endpoints.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RefinementRevisionNumberingTest {
    private static final String SUBMITTER = "numbering-submitter@example.test";
    private static final ZoneId HUB_ZONE = ZoneId.of("Africa/Kampala");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SubmissionRepository submissions;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ObjectMapper objectMapper;

    // (a)
    @Test
    void withoutAnInformationReplyTheImprovedVersionsAreTwoThenThree() throws Exception {
        UUID id = submittedBrief();
        toFactFinding(id);

        askAndAnswer(id, "Cycle one.");
        askAndAnswer(id, "Cycle two.");

        assertEquals(List.of(2, 3), revisionNumbers(id));
        assertNumbersAreUniqueAndIncreasing(id);
    }

    // (b) fails while main numbers revisions by briefVersion (it stores 3)
    @Test
    void afterOneInformationReplyTheFirstImprovedVersionIsStillTwo() throws Exception {
        UUID id = submittedBrief();
        informationReply(id);
        assertEquals(2, briefVersion(id));
        toFactFinding(id);

        askAndAnswer(id, "Cycle one.");

        assertEquals(List.of(2), revisionNumbers(id));
        assertNumbersAreUniqueAndIncreasing(id);
    }

    // (c)
    @Test
    void afterTwoInformationRepliesTheFirstImprovedVersionIsStillTwo() throws Exception {
        UUID id = submittedBrief();
        informationReply(id);
        informationReply(id);
        assertEquals(3, briefVersion(id));
        toFactFinding(id);

        askAndAnswer(id, "Cycle one.");

        assertEquals(List.of(2), revisionNumbers(id));
        assertNumbersAreUniqueAndIncreasing(id);
    }

    // (d) and (e): three cycles after an information reply, one of them answered through /submissions/{id}/reply
    @Test
    void threeCyclesAfterAnInformationReplyAreTwoThreeFourWithoutRepeatsOrGaps() throws Exception {
        UUID id = submittedBrief();
        informationReply(id);
        toFactFinding(id);

        askAndAnswer(id, "Cycle one.");
        ask(id, "Cycle two.");
        mockMvc.perform(post("/submissions/{id}/reply", id).with(submitter())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("note", "Answered through the reply endpoint."))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("RESUBMITTED"));
        askAndAnswer(id, "Cycle three.");

        assertEquals(List.of(2, 3, 4), revisionNumbers(id));
        assertNumbersAreUniqueAndIncreasing(id);
        assertEquals(3, jdbcTemplate.queryForObject(
                "SELECT count(DISTINCT request_id) FROM case_revisions WHERE brief_id = ?", Integer.class, id));
    }

    // (f) briefVersion still rises on every reply, information or improvement
    @Test
    void briefVersionStillRisesOnEveryReply() throws Exception {
        UUID id = submittedBrief();
        assertEquals(1, briefVersion(id));
        informationReply(id);
        assertEquals(2, briefVersion(id));
        toFactFinding(id);

        askAndAnswer(id, "Cycle one.");
        assertEquals(3, briefVersion(id));
        askAndAnswer(id, "Cycle two.");
        assertEquals(4, briefVersion(id));
        mockMvc.perform(get("/cases/{id}", id).with(coordinator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.submission.briefVersion").value(4));

        assertEquals(List.of(2, 3), revisionNumbers(id));
    }

    // ---- helpers ----------------------------------------------------------------------------

    private void askAndAnswer(UUID id, String guidance) throws Exception {
        ask(id, guidance);
        mockMvc.perform(post("/cases/{id}/improved-version", id).with(submitter())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("note", "Answer to: " + guidance))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stage").value("RESUBMITTED"));
    }

    /** Main's RefinementRequestAskRequest; from IN_FACT_FINDING or RESUBMITTED the coordinator may ask. */
    private void ask(UUID id, String guidance) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("guidanceNote", guidance);
        body.put("dueDate", LocalDate.now(HUB_ZONE).plusDays(7).toString());
        body.put("requestedChanges", List.of("Add detail"));
        body.put("version", caseVersion(id));
        mockMvc.perform(post("/cases/{id}/refinement-requests", id).with(coordinator())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isCreated());
    }

    /** Reception asks for more information and the submitter replies: briefVersion + 1, stage back to SUBMITTED. */
    private void informationReply(UUID id) throws Exception {
        mockMvc.perform(post("/submissions/{id}/request-information", id).with(coordinator())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("note", "Please add the supervisor contact."))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("AWAITING_INFORMATION"));
        mockMvc.perform(post("/submissions/{id}/reply", id).with(submitter())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("SUBMITTED"));
    }

    /** Test setup: the forwarding and fact-finding start are not under test, so they use the model transitions. */
    private void toFactFinding(UUID id) {
        entityManager.flush();
        entityManager.clear();
        Submission brief = submissions.findById(id).orElseThrow();
        Instant time = Instant.parse("2026-11-02T10:00:00Z");
        brief.transitionTo(SubmissionStage.FORWARDED, time);
        brief.transitionTo(SubmissionStage.IN_FACT_FINDING, time);
        submissions.saveAndFlush(brief);
    }

    /** A registered (SUBMITTED) general brief owned by SUBMITTER, with the submitter's email. */
    private UUID submittedBrief() {
        Submission brief = new Submission(UUID.randomUUID(), SubmissionCategory.GENERAL, "Numbering brief",
                "Purpose", "Problem", "receptionist@example.test", SUBMITTER, null, null, null, null,
                SUBMITTER, "Numbering Submitter", Instant.parse("2026-11-01T08:00:00Z"));
        brief.applyIntakeDetails(null, null, null, null, null, null, "2026-01");
        brief.register("CASE-RN-" + UUID.randomUUID().toString().substring(0, 8),
                Instant.parse("2026-11-01T09:00:00Z"), "receptionist@example.test");
        return submissions.saveAndFlush(brief).getId();
    }

    private List<Integer> revisionNumbers(UUID id) {
        entityManager.flush();
        return jdbcTemplate.queryForList(
                "SELECT version_number FROM case_revisions WHERE brief_id = ? ORDER BY id", Integer.class, id);
    }

    /** The unique (brief_id, version_number) rule holds and numbers follow the order the answers were stored. */
    private void assertNumbersAreUniqueAndIncreasing(UUID id) {
        List<Integer> numbers = revisionNumbers(id);
        assertEquals(numbers.size(), numbers.stream().distinct().count());
        assertEquals(numbers.stream().sorted().toList(), numbers);
    }

    private int briefVersion(UUID id) {
        entityManager.flush();
        return jdbcTemplate.queryForObject("SELECT brief_version FROM submissions WHERE id = ?", Integer.class, id);
    }

    private long caseVersion(UUID id) throws Exception {
        entityManager.flush();
        return objectMapper.readTree(mockMvc.perform(get("/cases/{id}", id).with(coordinator()))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString())
                .get("submission").get("version").asLong();
    }

    private String json(Object body) {
        return objectMapper.writeValueAsString(body);
    }

    private static RequestPostProcessor coordinator() {
        return user("coord@example.test").roles("INNOVATION_COORDINATOR");
    }

    private static RequestPostProcessor submitter() {
        return user(SUBMITTER).roles("SUBMITTER");
    }
}

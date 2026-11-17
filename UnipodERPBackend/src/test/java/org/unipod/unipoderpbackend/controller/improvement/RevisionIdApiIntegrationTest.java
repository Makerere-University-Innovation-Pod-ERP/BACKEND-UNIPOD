package org.unipod.unipoderpbackend.controller.improvement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.model.factfinding.FactFindingCheck;
import org.unipod.unipoderpbackend.model.factfinding.FactFindingResult;
import org.unipod.unipoderpbackend.model.improvement.CaseRevision;
import org.unipod.unipoderpbackend.model.submission.Submission;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;
import org.unipod.unipoderpbackend.repository.factfinding.FactFindingRepository;
import org.unipod.unipoderpbackend.repository.improvement.CaseRevisionRepository;
import org.unipod.unipoderpbackend.repository.submission.SubmissionRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The revision id in the improved-version response, in the staff refinement history and in the submitter's
 * my-improvement view, so a coordinator can call POST /cases/{id}/revisions/{revisionId}/accept without SQL.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RevisionIdApiIntegrationTest {
    private static final String COORDINATOR = "coord@example.test";
    private static final String OWNER = "revision-id-owner@example.test";
    private static final ZoneId HUB_ZONE = ZoneId.of("Africa/Kampala");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SubmissionRepository submissions;

    @Autowired
    private FactFindingRepository factFinding;

    @Autowired
    private CaseRevisionRepository revisions;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void improvedVersionResponseCarriesTheStoredRevisionId() throws Exception {
        Submission brief = briefInReview();
        ask(brief, "Please improve.");

        JsonNode answered = answer(brief, "Added a budget.");

        CaseRevision stored = revisions.findTopByBriefIdOrderByVersionNumberDesc(brief.getId()).orElseThrow();
        assertTrue(answered.get("revisionId").isIntegralNumber());
        assertEquals(stored.getId(), answered.get("revisionId").asLong());
        assertEquals(stored.getVersionNumber(), answered.get("versionNumber").asInt());
    }

    @Test
    void historyAndMyImprovementShowTheStoredIdOnlyWhereARevisionExists() throws Exception {
        Submission brief = briefInReview();
        ask(brief, "Round one.");
        long firstRevision = answer(brief, "Answer one.").get("revisionId").asLong();
        accept(brief, firstRevision).andExpect(status().isOk());
        ask(brief, "Round two.");

        CaseRevision stored = revisions.findTopByBriefIdOrderByVersionNumberDesc(brief.getId()).orElseThrow();
        assertEquals(stored.getId(), firstRevision);

        // Staff history: cycle 1 has its revision with the stored id; open cycle 2 still has revision null.
        JsonNode history = json(mockMvc.perform(get("/cases/{id}/refinement-history", brief.getId())
                        .with(coordinator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requests[1].revision").value(org.hamcrest.Matchers.nullValue())));
        assertEquals(stored.getId(), history.get("requests").get(0).get("revision").get("id").asLong());
        assertEquals("Answer one.", history.get("requests").get(0).get("revision").get("whatChanged").asString());

        // Submitter view: the answered request carries the same id; the open request has no revision at all.
        JsonNode mine = json(mockMvc.perform(get("/cases/{id}/my-improvement", brief.getId()).with(owner()))
                .andExpect(status().isOk()));
        assertEquals(1, mine.get("previousRequests").size());
        assertEquals(stored.getId(), mine.get("previousRequests").get(0).get("revision").get("id").asLong());
        assertFalse(mine.get("openRequest").has("revision"));
    }

    @Test
    void coordinatorAcceptsUsingTheIdFromRefinementHistory() throws Exception {
        Submission brief = briefInReview();
        ask(brief, "Please add a costed budget.");
        answer(brief, "Added a budget table.");

        JsonNode history = json(mockMvc.perform(get("/cases/{id}/refinement-history", brief.getId())
                .with(coordinator())).andExpect(status().isOk()));
        long revisionId = history.get("requests").get(0).get("revision").get("id").asLong();

        accept(brief, revisionId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revisionId").value(revisionId))
                .andExpect(jsonPath("$.versionNumber").value(2))
                .andExpect(jsonPath("$.briefStage").value("IN_REVIEW"));
    }

    @Test
    void submitterIsStillForbiddenToAcceptWithTheIdTheySee() throws Exception {
        Submission brief = briefInReview();
        ask(brief, "Please improve.");
        answer(brief, "Improved.");
        JsonNode mine = json(mockMvc.perform(get("/cases/{id}/my-improvement", brief.getId()).with(owner()))
                .andExpect(status().isOk()));
        long revisionId = mine.get("previousRequests").get(0).get("revision").get("id").asLong();

        mockMvc.perform(json(post("/cases/{id}/revisions/{revisionId}/accept", brief.getId(), revisionId),
                        Map.of("caseVersion", caseVersion(brief))).with(owner()))
                .andExpect(status().isForbidden());
        assertEquals("RESUBMITTED", json(mockMvc.perform(get("/cases/{id}", brief.getId()).with(coordinator())))
                .get("submission").get("stage").asString());
    }

    // ---- helpers (real endpoints; the brief and its fact-finding check are seeded through repositories) ----

    private Submission briefInReview() {
        Submission brief = submissions.saveAndFlush(ImprovementTestBriefs.ownedBy(OWNER, SubmissionStage.IN_REVIEW,
                "CASE-RI-"));
        FactFindingCheck check = new FactFindingCheck(brief.getId(), 1, "officer@example.test");
        check.complete(FactFindingResult.READY_FOR_REVIEW, Instant.parse("2026-11-10T09:00:00Z"));
        factFinding.saveAndFlush(check);
        return brief;
    }

    private void ask(Submission brief, String guidance) throws Exception {
        mockMvc.perform(json(post("/cases/{id}/refinement-requests", brief.getId()),
                        RefinementRequestApiIntegrationTest.body(guidance, LocalDate.now(HUB_ZONE).plusDays(10),
                                List.of("Change one"), caseVersion(brief)))
                        .with(coordinator()))
                .andExpect(status().isCreated());
    }

    private JsonNode answer(Submission brief, String whatChanged) throws Exception {
        long requestVersion = json(mockMvc.perform(get("/cases/{id}/my-improvement", brief.getId()).with(owner()))
                .andExpect(status().isOk())).get("openRequest").get("version").asLong();
        return json(mockMvc.perform(json(post("/cases/{id}/improved-version", brief.getId()),
                        ImprovementResponseApiIntegrationTest.answerBody(whatChanged, requestVersion)).with(owner()))
                .andExpect(status().isCreated()));
    }

    private ResultActions accept(Submission brief, long revisionId) throws Exception {
        return mockMvc.perform(json(post("/cases/{id}/revisions/{revisionId}/accept", brief.getId(), revisionId),
                Map.of("caseVersion", caseVersion(brief))).with(coordinator()));
    }

    private long caseVersion(Submission brief) throws Exception {
        return json(mockMvc.perform(get("/cases/{id}", brief.getId()).with(coordinator()))
                .andExpect(status().isOk())).get("submission").get("version").asLong();
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, Map<String, ?> body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body));
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private static RequestPostProcessor owner() {
        return user(OWNER).roles("SUBMITTER");
    }

    private static RequestPostProcessor coordinator() {
        return user(COORDINATOR).roles("INNOVATION_COORDINATOR");
    }
}

package org.unipod.unipoderpbackend.controller.training;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.model.training.AttendanceStatus;
import org.unipod.unipoderpbackend.model.training.TrainingParticipant;
import org.unipod.unipoderpbackend.model.training.TrainingSession;
import org.unipod.unipoderpbackend.repository.training.TrainingParticipantRepository;
import org.unipod.unipoderpbackend.repository.training.TrainingSessionRepository;
import org.unipod.unipoderpbackend.support.AuditTestSupport;
import org.unipod.unipoderpbackend.support.OperationsTestData;
import org.unipod.unipoderpbackend.support.ZodShape;
import tools.jackson.databind.JsonNode;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BootcampAttendanceApiIntegrationTest {
    private static final Instant FUTURE = Instant.parse("2030-11-20T06:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TrainingSessionRepository sessions;

    @Autowired
    private TrainingParticipantRepository participants;

    @PersistenceContext
    private EntityManager entityManager;

    private UUID telecoms;
    private TrainingSession session;
    private TrainingParticipant martha;
    private TrainingParticipant brian;

    @BeforeEach
    void seed() {
        telecoms = OperationsTestData.sector(jdbcTemplate, "Telecoms");
        session = sessions.saveAndFlush(BootcampTestData.session(telecoms, FUTURE, 25, 0));
        martha = participants.saveAndFlush(BootcampTestData.participant(session.getId(), "S1", "Martha Akello", "m@x.ug", 0));
        brian = participants.saveAndFlush(BootcampTestData.participant(session.getId(), "S2", "Brian Ssali", "b@x.ug", 0));
    }

    @Test
    void savesTheRosterAndReturnsTheWholeSession() throws Exception {
        JsonNode bootcamp = ZodShape.parse(mockMvc.perform(save(roster(martha, "PRESENT", brian, "ABSENT"), admin()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        BootcampTestData.BOOTCAMP_SHAPE.check(bootcamp);
        assertEquals(2, bootcamp.get("attendance").size());
        assertEquals(AttendanceStatus.PRESENT, stored(martha));
        assertEquals(AttendanceStatus.ABSENT, stored(brian));
    }

    @Test
    void savingAgainChangesOnlyWhatDiffersAndAuditsCountsOnly() throws Exception {
        mockMvc.perform(save(roster(martha, "PRESENT", brian, "ABSENT"), admin())).andExpect(status().isOk());
        mockMvc.perform(save(roster(martha, "PRESENT", brian, "PRESENT"), admin())).andExpect(status().isOk());
        mockMvc.perform(save(roster(martha, "PRESENT", brian, "PRESENT"), admin())).andExpect(status().isOk());

        assertEquals(AttendanceStatus.PRESENT, stored(brian));
        List<Map<String, Object>> lines = AuditTestSupport.lines(entityManager, jdbcTemplate, session.getId());
        List<Map<String, Object>> saved = lines.stream()
                .filter(line -> "bootcamp.attendance_saved".equals(line.get("action"))).toList();
        assertEquals(2, saved.size(), "the third save changed nothing and wrote nothing");
        for (Map<String, Object> line : saved) {
            AuditTestSupport.assertNoPersonalData(line, "Martha", "Akello", "Brian", "Ssali", "m@x.ug");
        }
        JsonNode second = ZodShape.parse((String) saved.get(1).get("after_value"));
        assertEquals(1, second.get("changed").asInt());
        assertEquals(2, second.get("present").asInt());
    }

    @Test
    void anAbsentEntryForSomeoneWhoHasNotPaidIsSkippedButPresentIsAConflict() throws Exception {
        TrainingSession paid = sessions.saveAndFlush(BootcampTestData.session(telecoms, FUTURE.plusSeconds(86_400), 25, 50_000));
        TrainingParticipant unpaid = participants.saveAndFlush(
                BootcampTestData.participant(paid.getId(), "U1", "Unpaid", "u@x.ug", 50_000));

        mockMvc.perform(save(paid, "{\"attendance\": [{\"participantId\": \"%s\", \"status\": \"ABSENT\"}]}"
                .formatted(unpaid.getId()), admin())).andExpect(status().isOk());
        assertEquals(AttendanceStatus.UNMARKED, stored(unpaid));

        mockMvc.perform(save(paid, "{\"attendance\": [{\"participantId\": \"%s\", \"status\": \"PRESENT\"}]}"
                .formatted(unpaid.getId()), admin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Attendance can only be recorded for confirmed participants."));
    }

    @Test
    void aParticipantWhoPassedMustStayPresent() throws Exception {
        TrainingSession safety = sessions.saveAndFlush(new TrainingSession(UUID.randomUUID(), telecoms, "Safety",
                "Eng", FUTURE, FUTURE.plusSeconds(3600), 5, "Lab", "", 0, true, true, FUTURE));
        TrainingParticipant passer = participants.saveAndFlush(
                BootcampTestData.participant(safety.getId(), "P1", "Passer", "p@x.ug", 0));
        passer.markAttendance(AttendanceStatus.PRESENT, FUTURE);
        passer.markPassed(true, FUTURE);
        participants.saveAndFlush(passer);

        mockMvc.perform(save(safety, "{\"attendance\": [{\"participantId\": \"%s\", \"status\": \"ABSENT\"}]}"
                .formatted(passer.getId()), admin())).andExpect(status().isConflict());
    }

    @Test
    void someoneNotEnrolledInThisBootcampIsNotFound() throws Exception {
        TrainingSession other = sessions.saveAndFlush(BootcampTestData.session(telecoms, FUTURE, 5, 0));
        TrainingParticipant stranger = participants.saveAndFlush(
                BootcampTestData.participant(other.getId(), "X1", "Stranger", "s@x.ug", 0));

        mockMvc.perform(save(roster(martha, "PRESENT", stranger, "PRESENT"), admin()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Participant not found in this session."));
        mockMvc.perform(save("{\"attendance\": [{\"participantId\": \"%s\", \"status\": \"PRESENT\"}]}"
                .formatted(UUID.randomUUID()), admin())).andExpect(status().isNotFound());
    }

    @Test
    void aPersonListedTwiceInTheRosterIsRefused() throws Exception {
        mockMvc.perform(save(roster(martha, "PRESENT", martha, "ABSENT"), admin()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("Each person can appear only once in the roster."));
        assertEquals(AttendanceStatus.UNMARKED, stored(martha));
    }

    @Test
    void anEmptyRosterIsAcceptedAndChangesNothing() throws Exception {
        mockMvc.perform(save("{\"attendance\": []}", admin())).andExpect(status().isOk());

        assertEquals(AttendanceStatus.UNMARKED, stored(martha));
        assertEquals(0, AuditTestSupport.lines(entityManager, jdbcTemplate, session.getId()).size());
    }

    @Test
    void anUnknownOrOtherDepartmentsSessionIsNotFound() throws Exception {
        mockMvc.perform(post("/telecoms/bootcamps/{id}/attendance", UUID.randomUUID()).with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"attendance\": []}"))
                .andExpect(status().isNotFound());
        UUID textile = OperationsTestData.sector(jdbcTemplate, "Textile");
        TrainingSession elsewhere = sessions.saveAndFlush(BootcampTestData.session(textile, FUTURE, 5, 0));

        mockMvc.perform(save(elsewhere, "{\"attendance\": []}", admin())).andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"attendance\": null}", "{\"attendance\": [null]}",
            "{\"attendance\": [{\"status\": \"PRESENT\"}]}",
            "{\"attendance\": [{\"participantId\": \"00000000-0000-0000-0000-000000000001\"}]}"})
    void refusesAnInvalidRosterWithValidationErrors(String json) throws Exception {
        mockMvc.perform(save(json, admin()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"nope",
            "{\"attendance\": [{\"participantId\": \"abc\", \"status\": \"PRESENT\"}]}",
            "{\"attendance\": [{\"participantId\": \"00000000-0000-0000-0000-000000000001\", \"status\": \"EXCUSED\"}]}",
            "{\"attendance\": [{\"participantId\": \"00000000-0000-0000-0000-000000000001\", \"status\": \"UNMARKED\"}]}",
            "{\"attendance\": [], \"extra\": 1}"})
    void refusesABodyItCannotReadAsMalformed(String json) throws Exception {
        mockMvc.perform(save(json, admin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_BODY"));
    }

    @Test
    void onlyTheTelecomsDepartmentsManagersMaySaveAttendance() throws Exception {
        String ours = "tel-" + UUID.randomUUID() + "@test.local";
        String theirs = "tex-" + UUID.randomUUID() + "@test.local";
        OperationsTestData.member(jdbcTemplate, telecoms, ours);
        OperationsTestData.member(jdbcTemplate, OperationsTestData.sector(jdbcTemplate, "Textile"), theirs);
        String body = roster(martha, "PRESENT", brian, "PRESENT");

        mockMvc.perform(save(body, user(ours).roles("DEPARTMENT_COORDINATOR"))).andExpect(status().isOk());
        mockMvc.perform(save(body, user(theirs).roles("DEPARTMENT_COORDINATOR"))).andExpect(status().isForbidden());
        for (String role : BootcampTestData.NON_MANAGE_ROLES) {
            mockMvc.perform(save(body, as(role))).andExpect(status().isForbidden());
        }
        mockMvc.perform(post("/telecoms/bootcamps/{id}/attendance", session.getId())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
    }

    // ----- helpers

    private AttendanceStatus stored(TrainingParticipant participant) {
        entityManager.flush();
        entityManager.clear();
        return participants.findById(participant.getId()).orElseThrow().getAttendance();
    }

    private static String roster(TrainingParticipant first, String firstStatus, TrainingParticipant second,
                                 String secondStatus) {
        return "{\"attendance\": [{\"participantId\": \"%s\", \"status\": \"%s\"}, {\"participantId\": \"%s\", \"status\": \"%s\"}]}"
                .formatted(first.getId(), firstStatus, second.getId(), secondStatus);
    }

    private MockHttpServletRequestBuilder save(String json, RequestPostProcessor who) {
        return save(session, json, who);
    }

    private static MockHttpServletRequestBuilder save(TrainingSession target, String json, RequestPostProcessor who) {
        return post("/telecoms/bootcamps/{id}/attendance", target.getId()).with(who)
                .contentType(MediaType.APPLICATION_JSON).content(json);
    }

    private static RequestPostProcessor admin() {
        return as("ADMINISTRATOR");
    }

    private static RequestPostProcessor as(String role) {
        return user("bc-" + UUID.randomUUID() + "@test.local").roles(role);
    }
}

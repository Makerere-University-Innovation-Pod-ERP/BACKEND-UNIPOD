package org.unipod.unipoderpbackend.controller.training;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.model.training.TrainingParticipant;
import org.unipod.unipoderpbackend.model.training.TrainingSession;
import org.unipod.unipoderpbackend.repository.training.TrainingParticipantRepository;
import org.unipod.unipoderpbackend.repository.training.TrainingSessionRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TrainingRegistrationApiIntegrationTest {
    private static final String BODY = """
            {"name": "Amina Nakato", "regNumber": "2021/HD01/0001U", "department": "Engineering",
             "contactEmail": "amina@students.test"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TrainingSessionRepository sessions;

    @Autowired
    private TrainingParticipantRepository participants;

    @Autowired
    private Clock clock;

    @PersistenceContext
    private EntityManager entityManager;

    private UUID departmentId;

    @BeforeEach
    void seedDepartment() {
        departmentId = TrainingFixtures.department(jdbcTemplate);
    }

    @Test
    void registersAnAttendeeForAFreeSessionAndConfirmsTheSeat() throws Exception {
        TrainingSession session = openSession(5, 0);

        mockMvc.perform(register(session, BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(session.getId().toString()))
                .andExpect(jsonPath("$.enrolledCount").value(1))
                .andExpect(jsonPath("$.participants", hasSize(1)))
                .andExpect(jsonPath("$.participants[0].name").value("Amina Nakato"))
                .andExpect(jsonPath("$.participants[0].regNumber").value("2021/HD01/0001U"))
                .andExpect(jsonPath("$.participants[0].department").value("Engineering"))
                .andExpect(jsonPath("$.participants[0].contactEmail").value("amina@students.test"))
                .andExpect(jsonPath("$.participants[0].status").value("CONFIRMED"))
                .andExpect(jsonPath("$.participants[0].attendance").value("UNMARKED"))
                .andExpect(jsonPath("$.participants[0].passed").value(false));
    }

    @Test
    void aPaidSessionHoldsTheSeatAsPendingPayment() throws Exception {
        TrainingSession session = openSession(5, 50_000);

        mockMvc.perform(register(session, BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.enrolledCount").value(1))
                .andExpect(jsonPath("$.participants[0].status").value("PENDING_PAYMENT"));
    }

    @Test
    void auditsTheRegistrationWithoutAnyPersonalDetails() throws Exception {
        TrainingSession session = openSession(5, 0);

        mockMvc.perform(register(session, BODY)).andExpect(status().isCreated());

        entityManager.flush();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT actor, actor_role, action, entity_type, department_id, after_value::text AS after_value "
                        + "FROM audit_log WHERE action = 'training.participant.registered' "
                        + "AND after_value ->> 'sessionId' = ?", session.getId().toString());
        assertEquals(1, rows.size());
        Map<String, Object> row = rows.get(0);
        assertEquals("ADMINISTRATOR", row.get("actor_role"));
        assertEquals("training_participant", row.get("entity_type"));
        assertEquals(departmentId, row.get("department_id"));
        String after = row.get("after_value").toString();
        for (String personal : List.of("Amina", "Nakato", "2021/HD01/0001U", "amina@students.test", "Engineering")) {
            assertFalse(after.contains(personal), "audit snapshot leaks: " + personal);
        }
        assertEquals(true, after.contains("\"status\": \"CONFIRMED\""));
    }

    @Test
    void aFullSessionRefusesTheNextRegistration() throws Exception {
        TrainingSession session = openSession(2, 0);
        mockMvc.perform(register(session, body("R-1"))).andExpect(status().isCreated());
        mockMvc.perform(register(session, body("R-2"))).andExpect(status().isCreated());

        mockMvc.perform(register(session, body("R-3")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("This session is full."));

        assertEquals(2, participants.countSeats(session.getId()));
        assertEquals(2, registeredAuditLines(session), "the refused attempt must leave no audit line");
    }

    @Test
    void aSeatHeldAwaitingPaymentStillCountsTowardsCapacity() throws Exception {
        TrainingSession session = openSession(1, 50_000);
        mockMvc.perform(register(session, body("R-1"))).andExpect(status().isCreated());

        mockMvc.perform(register(session, body("R-2"))).andExpect(status().isConflict());
    }

    @Test
    void aCancelledRegistrationFreesItsSeatAndItsRegNumber() throws Exception {
        TrainingSession session = openSession(1, 0);
        TrainingParticipant first = participants.saveAndFlush(new TrainingParticipant(
                UUID.randomUUID(), session.getId(), "First", "R-1", "Engineering", null, 0, clock.instant()));
        mockMvc.perform(register(session, body("R-2"))).andExpect(status().isConflict());

        first.cancel(clock.instant());
        participants.saveAndFlush(first);

        mockMvc.perform(register(session, body("R-1")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.enrolledCount").value(1));
    }

    @Test
    void theSameRegNumberCannotRegisterTwiceIgnoringCase() throws Exception {
        TrainingSession session = openSession(5, 0);
        mockMvc.perform(register(session, BODY)).andExpect(status().isCreated());

        mockMvc.perform(register(session, BODY.replace("2021/HD01/0001U", "2021/hd01/0001u")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("This registration number is already registered for this session."));
    }

    @Test
    void theSameRegNumberCanRegisterForAnotherSession() throws Exception {
        TrainingSession first = openSession(5, 0);
        TrainingSession second = openSession(5, 0);
        mockMvc.perform(register(first, BODY)).andExpect(status().isCreated());

        mockMvc.perform(register(second, BODY)).andExpect(status().isCreated());
    }

    @Test
    void registrationClosesOnceTheSessionHasEnded() throws Exception {
        Instant now = clock.instant();
        TrainingSession finished = sessions.saveAndFlush(TrainingFixtures.session(
                departmentId, now.minus(Duration.ofHours(5)), now.minus(Duration.ofHours(3)), 5, 0, true));

        mockMvc.perform(register(finished, BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Registration is closed for this session."));
    }

    @Test
    void registrationClosesOnceAnUnpublishedSessionHasEndedToo() throws Exception {
        Instant now = clock.instant();
        TrainingSession endedDraft = sessions.saveAndFlush(TrainingFixtures.session(
                departmentId, now.minus(Duration.ofHours(5)), now.minus(Duration.ofHours(3)), 5, 0, false));

        mockMvc.perform(register(endedDraft, BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Registration is closed for this session."));

        assertEquals(0, participants.countSeats(endedDraft.getId()));
        assertEquals(0, registeredAuditLines(endedDraft));
    }

    @Test
    void aSessionInProgressAndADraftStillTakeRegistrations() throws Exception {
        Instant now = clock.instant();
        TrainingSession running = sessions.saveAndFlush(TrainingFixtures.session(
                departmentId, now.minus(Duration.ofMinutes(30)), now.plus(Duration.ofHours(1)), 5, 0, true));
        TrainingSession draft = sessions.saveAndFlush(TrainingFixtures.session(
                departmentId, now.plus(Duration.ofDays(3)), null, 5, 0, false));

        mockMvc.perform(register(running, BODY)).andExpect(status().isCreated());
        mockMvc.perform(register(draft, BODY)).andExpect(status().isCreated());
    }

    @Test
    void anUnknownSessionIsNotFoundAndAMalformedIdIsABadRequest() throws Exception {
        mockMvc.perform(post("/operations/training/sessions/{id}/participants", UUID.randomUUID())
                        .with(administrator())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/operations/training/sessions/not-a-uuid/participants")
                        .with(administrator())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isBadRequest());
    }

    @Test
    void everyRoleWithoutManageAccessIsForbidden() throws Exception {
        TrainingSession session = openSession(5, 0);

        for (String role : TrainingFixtures.NON_MANAGE_ROLES) {
            mockMvc.perform(post("/operations/training/sessions/{id}/participants", session.getId())
                            .with(user(role.toLowerCase() + "@test.local").roles(role))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(BODY))
                    .andExpect(status().isForbidden());
        }
        assertEquals(0, participants.countSeats(session.getId()));
        assertEquals(0, registeredAuditLines(session));
    }

    @Test
    void anAnonymousCallerIsUnauthorized() throws Exception {
        TrainingSession session = openSession(5, 0);

        mockMvc.perform(post("/operations/training/sessions/{id}/participants", session.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidBodies")
    void rejectsAnInvalidBodyWithValidationErrors(String problem, String json) throws Exception {
        TrainingSession session = openSession(5, 0);

        mockMvc.perform(register(session, json))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors").isNotEmpty());
        assertEquals(0, participants.countSeats(session.getId()));
        assertEquals(0, registeredAuditLines(session));
    }

    static Stream<Arguments> invalidBodies() {
        return Stream.of(
                Arguments.of("blank name", BODY.replace("Amina Nakato", "  ")),
                Arguments.of("missing regNumber", BODY.replace("\"regNumber\": \"2021/HD01/0001U\",", "")),
                Arguments.of("blank department", BODY.replace("Engineering", "")),
                Arguments.of("malformed email", BODY.replace("amina@students.test", "not-an-email")),
                Arguments.of("name too long", BODY.replace("Amina Nakato", "x".repeat(201))),
                Arguments.of("regNumber too long", BODY.replace("2021/HD01/0001U", "x".repeat(51))));
    }

    @Test
    void anUnknownFieldIsABadRequest() throws Exception {
        TrainingSession session = openSession(5, 0);

        mockMvc.perform(register(session, BODY.replace("\"department\"", "\"userEmail\": \"a@b.test\", \"department\"")))
                .andExpect(status().isBadRequest());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder register(
            TrainingSession session, String json) {
        return post("/operations/training/sessions/{id}/participants", session.getId())
                .with(administrator())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json);
    }

    private int registeredAuditLines(TrainingSession session) {
        // Pending audit inserts must reach the database before a raw JDBC query can see them.
        entityManager.flush();
        Integer lines = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_log WHERE action = 'training.participant.registered' "
                        + "AND after_value ->> 'sessionId' = ?", Integer.class, session.getId().toString());
        return lines;
    }

    private static String body(String regNumber) {
        return BODY.replace("2021/HD01/0001U", regNumber);
    }

    private TrainingSession openSession(int capacity, int fee) {
        return sessions.saveAndFlush(TrainingFixtures.session(
                departmentId, clock.instant().plus(Duration.ofDays(3)), null, capacity, fee, true));
    }

    private static RequestPostProcessor administrator() {
        return user("registrar-" + UUID.randomUUID() + "@test.local").roles("ADMINISTRATOR");
    }
}

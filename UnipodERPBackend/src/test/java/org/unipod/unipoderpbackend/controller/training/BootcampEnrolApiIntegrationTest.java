package org.unipod.unipoderpbackend.controller.training;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
class BootcampEnrolApiIntegrationTest {
    private static final Instant FUTURE = Instant.parse("2030-11-20T06:00:00Z");
    private static final String BODY = """
            {"name": "Martha Akello", "email": "martha@unipod.test", "studentNumber": "S24/1048"}""";

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

    @BeforeEach
    void seed() {
        telecoms = OperationsTestData.sector(jdbcTemplate, "Telecoms");
    }

    @Test
    void enrolsSomeoneAndReturnsTheWholeSession() throws Exception {
        TrainingSession session = open(25, 0);

        JsonNode bootcamp = ZodShape.parse(mockMvc.perform(enrol(session, BODY, admin()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        BootcampTestData.BOOTCAMP_SHAPE.check(bootcamp);
        assertEquals(session.getId().toString(), bootcamp.get("id").asString());
        assertEquals(1, bootcamp.get("enrolled").size());
        JsonNode person = bootcamp.get("enrolled").get(0);
        assertEquals("Martha Akello", person.get("name").asString());
        assertEquals("martha@unipod.test", person.get("email").asString());
        assertEquals("S24/1048", person.get("studentNumber").asString());
        assertEquals("NOT_REQUESTED", person.get("paymentStatus").asString());
    }

    @Test
    void aPaidBootcampHoldsTheSeatUntilPaymentIsConfirmed() throws Exception {
        TrainingSession session = open(25, 60_000);

        mockMvc.perform(enrol(session, BODY, admin())).andExpect(status().isOk());

        assertEquals("PENDING_PAYMENT", jdbcTemplate.queryForObject(
                "SELECT status FROM training_participants WHERE session_id = ?", String.class, session.getId()));
    }

    @Test
    void aStudentNumberCannotLookLikeAGeneratedPlaceholder() throws Exception {
        TrainingSession session = open(25, 0);

        mockMvc.perform(enrol(session,
                "{\"name\": \"Diana\", \"email\": \"diana@tech.ug\", \"studentNumber\": \"ext-12345678\"}", admin()))
                .andExpect(status().isUnprocessableContent());
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM training_participants WHERE session_id = ?", Integer.class, session.getId()));
    }

    @Test
    void someoneWithoutAStudentNumberGetsAPlaceholderThatTheScreenNeverSees() throws Exception {
        TrainingSession session = open(25, 0);

        JsonNode bootcamp = ZodShape.parse(mockMvc.perform(enrol(session,
                "{\"name\": \"Diana\", \"email\": \"diana@tech.ug\", \"studentNumber\": \"\"}", admin()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        assertTrue(bootcamp.get("enrolled").get(0).get("studentNumber").isNull());
        String stored = jdbcTemplate.queryForObject(
                "SELECT reg_number FROM training_participants WHERE session_id = ?", String.class, session.getId());
        assertTrue(stored.startsWith("EXT-"), stored);
        // A second person without a number does not collide with the first.
        mockMvc.perform(enrol(session, "{\"name\": \"Eve\", \"email\": \"eve@tech.ug\"}", admin()))
                .andExpect(status().isOk());
    }

    @Test
    void auditsTheRegistrationWithoutNamesOrEmails() throws Exception {
        TrainingSession session = open(25, 0);
        mockMvc.perform(enrol(session, BODY, admin())).andExpect(status().isOk());
        UUID participant = jdbcTemplate.queryForObject(
                "SELECT id FROM training_participants WHERE session_id = ?", UUID.class, session.getId());

        List<Map<String, Object>> lines = AuditTestSupport.lines(entityManager, jdbcTemplate, participant);

        assertEquals(1, lines.size());
        assertEquals("training.participant.registered", lines.get(0).get("action"));
        AuditTestSupport.assertNoPersonalData(lines.get(0), "Martha", "Akello", "martha@unipod.test", "S24/1048");
    }

    @Test
    void theLastSeatIsTakenAndTheNextPersonGetsAConflict() throws Exception {
        TrainingSession session = open(1, 0);
        mockMvc.perform(enrol(session, BODY, admin())).andExpect(status().isOk());

        mockMvc.perform(enrol(session,
                        "{\"name\": \"Brian\", \"email\": \"brian@x.ug\", \"studentNumber\": \"S23/2099\"}", admin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("This session is full."));
        assertEquals(1, participants.countSeats(session.getId()));
    }

    @Test
    void theSameStudentNumberTwiceIsAConflict() throws Exception {
        TrainingSession session = open(25, 0);
        mockMvc.perform(enrol(session, BODY, admin())).andExpect(status().isOk());

        mockMvc.perform(enrol(session, BODY.replace("Martha Akello", "Someone Else"), admin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("This registration number is already registered for this session."));
    }

    @Test
    void aFinishedBootcampTakesNoEnrolments() throws Exception {
        TrainingSession past = sessions.saveAndFlush(
                BootcampTestData.session(telecoms, Instant.parse("2020-11-20T06:00:00Z"), 25, 0));

        mockMvc.perform(enrol(past, BODY, admin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Registration is closed for this session."));
    }

    @Test
    void anUnknownSessionOrAnotherDepartmentsSessionIsNotFound() throws Exception {
        mockMvc.perform(post("/telecoms/bootcamps/{id}/enroll", UUID.randomUUID()).with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isNotFound());

        UUID textile = OperationsTestData.sector(jdbcTemplate, "Textile");
        TrainingSession elsewhere = sessions.saveAndFlush(BootcampTestData.session(textile, FUTURE, 10, 0));
        mockMvc.perform(enrol(elsewhere, BODY, admin())).andExpect(status().isNotFound());
        assertEquals(0, participants.countSeats(elsewhere.getId()));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"email\": \"a@b.ug\"}",
            "{\"name\": \"A\"}",
            "{\"name\": \" \", \"email\": \"a@b.ug\"}",
            "{\"name\": \"A\", \"email\": \"not-an-email\"}",
            "{\"name\": \"A\", \"email\": \"\"}"
    })
    void refusesAnInvalidFormWithValidationErrors(String json) throws Exception {
        TrainingSession session = open(25, 0);

        mockMvc.perform(enrol(session, json, admin()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        assertEquals(0, participants.countSeats(session.getId()));
    }

    @Test
    void refusesABodyItCannotReadAsMalformed() throws Exception {
        TrainingSession session = open(25, 0);

        mockMvc.perform(enrol(session, "nope", admin()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MALFORMED_BODY"));
        mockMvc.perform(enrol(session, "{\"name\": \"A\", \"email\": \"a@b.ug\", \"phone\": \"1\"}", admin()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/telecoms/bootcamps/not-an-id/enroll").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest());
    }

    @Test
    void onlyTheTelecomsDepartmentsManagersMayEnrol() throws Exception {
        TrainingSession session = open(25, 0);
        String ours = "tel-" + UUID.randomUUID() + "@test.local";
        String theirs = "tex-" + UUID.randomUUID() + "@test.local";
        OperationsTestData.member(jdbcTemplate, telecoms, ours);
        OperationsTestData.member(jdbcTemplate, OperationsTestData.sector(jdbcTemplate, "Textile"), theirs);

        mockMvc.perform(enrol(session, BODY, user(ours).roles("DEPARTMENT_COORDINATOR"))).andExpect(status().isOk());
        mockMvc.perform(enrol(session, BODY.replace("S24/1048", "S1"), user(theirs).roles("DEPARTMENT_COORDINATOR")))
                .andExpect(status().isForbidden());
        for (String role : BootcampTestData.NON_MANAGE_ROLES) {
            mockMvc.perform(enrol(session, BODY.replace("S24/1048", "S2"), as(role))).andExpect(status().isForbidden());
        }
        mockMvc.perform(post("/telecoms/bootcamps/{id}/enroll", session.getId())
                .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isUnauthorized());
        assertEquals(1, participants.countSeats(session.getId()));
    }

    // ----- helpers

    private TrainingSession open(int capacity, int fee) {
        return sessions.saveAndFlush(BootcampTestData.session(telecoms, FUTURE, capacity, fee));
    }

    private static MockHttpServletRequestBuilder enrol(TrainingSession session, String json, RequestPostProcessor who) {
        return post("/telecoms/bootcamps/{id}/enroll", session.getId()).with(who)
                .contentType(MediaType.APPLICATION_JSON).content(json);
    }

    private static RequestPostProcessor admin() {
        return as("ADMINISTRATOR");
    }

    private static RequestPostProcessor as(String role) {
        return user("bc-" + UUID.randomUUID() + "@test.local").roles(role);
    }
}

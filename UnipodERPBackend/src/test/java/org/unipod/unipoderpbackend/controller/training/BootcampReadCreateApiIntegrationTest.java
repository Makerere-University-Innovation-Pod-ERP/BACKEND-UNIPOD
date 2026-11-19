package org.unipod.unipoderpbackend.controller.training;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class BootcampReadCreateApiIntegrationTest {
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

    @BeforeEach
    void seed() {
        telecoms = OperationsTestData.sector(jdbcTemplate, "Telecoms");
    }

    // ----- list

    @Test
    void listsTheTelecomsDepartmentsSessionsNewestFirstWithTheirParticipants() throws Exception {
        TrainingSession older = sessions.saveAndFlush(BootcampTestData.session(telecoms, FUTURE, 25, 60_000));
        TrainingSession newer = sessions.saveAndFlush(BootcampTestData.session(telecoms, FUTURE.plusSeconds(86_400), 20, 0));
        participants.saveAndFlush(BootcampTestData.participant(older.getId(), "S24/1048", "Martha Akello",
                "martha@unipod.test", 60_000));

        JsonNode list = list();

        BootcampTestData.LIST_SHAPE.check(list);
        JsonNode items = list.get("items");
        assertTrue(indexOf(items, newer.getId()) < indexOf(items, older.getId()), "newest first");
        JsonNode first = items.get(indexOf(items, older.getId()));
        assertEquals(older.getTitle(), first.get("topic").asString());
        assertEquals("Eng. Kaddu", first.get("instructor").asString());
        assertEquals("2030-11-20", first.get("date").asString());
        assertEquals("09:00", first.get("start").asString());
        assertEquals("16:00", first.get("end").asString());
        assertEquals(25, first.get("capacity").asInt());
        assertEquals(60_000, first.get("fee").asInt());
        assertEquals(1, first.get("enrolled").size());
        JsonNode martha = first.get("enrolled").get(0);
        assertEquals("Martha Akello", martha.get("name").asString());
        assertEquals("martha@unipod.test", martha.get("email").asString());
        assertEquals("S24/1048", martha.get("studentNumber").asString());
    }

    @Test
    void leavesOutOtherDepartmentsSessions() throws Exception {
        UUID other = OperationsTestData.sector(jdbcTemplate, "Textile");
        TrainingSession elsewhere = sessions.saveAndFlush(BootcampTestData.session(other, FUTURE, 10, 0));

        assertEquals(-1, indexOf(list().get("items"), elsewhere.getId()));
    }

    @Test
    void anEnrolledPersonWithoutAStudentNumberHasANullOneAndCancelledRegistrationsAreNotListed() throws Exception {
        TrainingSession session = sessions.saveAndFlush(BootcampTestData.session(telecoms, FUTURE, 25, 0));
        participants.saveAndFlush(BootcampTestData.participant(session.getId(), "EXT-1a2b3c4d", "Diana", "d@tech.ug", 0));
        TrainingParticipant gone = participants.saveAndFlush(
                BootcampTestData.participant(session.getId(), "S1", "Gone", "g@tech.ug", 0));
        gone.cancel(FUTURE);
        participants.saveAndFlush(gone);

        JsonNode enrolled = list().get("items").get(indexOf(list().get("items"), session.getId())).get("enrolled");

        assertEquals(1, enrolled.size());
        assertTrue(enrolled.get(0).get("studentNumber").isNull());
    }

    @Test
    void takesEachParticipantsPaymentStatusFromTheirInvoiceOrFromTheConfirmedSeat() throws Exception {
        TrainingSession paid = sessions.saveAndFlush(BootcampTestData.session(telecoms, FUTURE, 25, 60_000));
        TrainingParticipant awaiting = participants.saveAndFlush(
                BootcampTestData.participant(paid.getId(), "A1", "Awaiting", "a@x.ug", 60_000));
        TrainingParticipant checking = participants.saveAndFlush(
                BootcampTestData.participant(paid.getId(), "A2", "Checking", "b@x.ug", 60_000));
        TrainingParticipant rejected = participants.saveAndFlush(
                BootcampTestData.participant(paid.getId(), "A3", "Rejected", "c@x.ug", 60_000));
        TrainingParticipant settled = participants.saveAndFlush(
                BootcampTestData.participant(paid.getId(), "A4", "Settled", "d@x.ug", 60_000));
        TrainingParticipant byHand = participants.saveAndFlush(
                BootcampTestData.participant(paid.getId(), "A5", "By hand", "e@x.ug", 60_000));
        TrainingParticipant unpaid = participants.saveAndFlush(
                BootcampTestData.participant(paid.getId(), "A6", "Not yet", "f@x.ug", 60_000));
        byHand.confirmPayment(FUTURE);
        participants.saveAndFlush(byHand);
        Instant at = Instant.parse("2026-11-01T08:00:00Z");
        OperationsTestData.invoice(jdbcTemplate, "SESSION", awaiting.getId().toString(), "a@x.ug", "WAITING_FOR_PROOF", at);
        OperationsTestData.invoice(jdbcTemplate, "SESSION", checking.getId().toString(), "b@x.ug", "WAITING_FOR_CHECK", at);
        UUID invoice = OperationsTestData.invoice(jdbcTemplate, "SESSION", rejected.getId().toString(), "c@x.ug",
                "WAITING_FOR_PROOF", at);
        OperationsTestData.proof(jdbcTemplate, invoice, "REJECTED", at.plusSeconds(30));
        OperationsTestData.invoice(jdbcTemplate, "SESSION", settled.getId().toString(), "d@x.ug", "RECEIPTED", at);

        JsonNode enrolled = list().get("items").get(indexOf(list().get("items"), paid.getId())).get("enrolled");

        assertEquals("AWAITING_PROOF", paymentOf(enrolled, awaiting));
        assertEquals("PROOF_SUBMITTED", paymentOf(enrolled, checking));
        assertEquals("REJECTED", paymentOf(enrolled, rejected));
        assertEquals("CONFIRMED", paymentOf(enrolled, settled));
        assertEquals("CONFIRMED", paymentOf(enrolled, byHand));
        assertEquals("NOT_REQUESTED", paymentOf(enrolled, unpaid));
    }

    @Test
    void aFreeSessionsParticipantsNeedNoPayment() throws Exception {
        TrainingSession free = sessions.saveAndFlush(BootcampTestData.session(telecoms, FUTURE, 25, 0));
        TrainingParticipant person = participants.saveAndFlush(
                BootcampTestData.participant(free.getId(), "F1", "Free", "f@x.ug", 0));

        JsonNode enrolled = list().get("items").get(indexOf(list().get("items"), free.getId())).get("enrolled");

        assertEquals("NOT_REQUESTED", paymentOf(enrolled, person));
    }

    @Test
    void listsAttendanceAsPresentOrAbsentAndSkipsUnmarkedPeople() throws Exception {
        TrainingSession session = sessions.saveAndFlush(BootcampTestData.session(telecoms, FUTURE, 25, 0));
        TrainingParticipant present = mark(session, "P1", AttendanceStatus.PRESENT);
        TrainingParticipant absent = mark(session, "P2", AttendanceStatus.ABSENT);
        TrainingParticipant excused = mark(session, "P3", AttendanceStatus.EXCUSED);
        TrainingParticipant unmarked = mark(session, "P4", AttendanceStatus.UNMARKED);

        JsonNode attendance = list().get("items").get(indexOf(list().get("items"), session.getId())).get("attendance");

        assertEquals(3, attendance.size());
        assertEquals("PRESENT", statusOf(attendance, present));
        assertEquals("ABSENT", statusOf(attendance, absent));
        assertEquals("ABSENT", statusOf(attendance, excused));
        assertEquals(null, statusOf(attendance, unmarked));
    }

    @Test
    void everyRoleExceptSubmitterMayReadTheList() throws Exception {
        for (String role : BootcampTestData.READ_ROLES) {
            mockMvc.perform(get("/telecoms/bootcamps").with(as(role))).andExpect(status().isOk());
        }
        mockMvc.perform(get("/telecoms/bootcamps").with(as("SUBMITTER"))).andExpect(status().isForbidden());
        mockMvc.perform(get("/telecoms/bootcamps")).andExpect(status().isUnauthorized());
    }

    // ----- create

    @Test
    void createsABootcampAsATelecomsTrainingSession() throws Exception {
        JsonNode created = ZodShape.parse(mockMvc.perform(create("""
                {"topic": "Telecom Systems and Wireless IoT", "instructor": "Faith Namuyanja", "date": "2030-11-20",
                 "start": "09:00", "end": "16:00", "capacity": 25, "fee": 60000}""", admin()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        BootcampTestData.BOOTCAMP_SHAPE.check(created);
        assertEquals("Telecom Systems and Wireless IoT", created.get("topic").asString());
        assertEquals("2030-11-20", created.get("date").asString());
        assertEquals("09:00", created.get("start").asString());
        assertEquals("16:00", created.get("end").asString());
        assertEquals(0, created.get("enrolled").size());
        assertEquals(0, created.get("attendance").size());
        UUID id = UUID.fromString(created.get("id").asString());
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT department_id, venue, fee, published, starts_at FROM training_sessions WHERE id = ?", id);
        assertEquals(telecoms, row.get("department_id"));
        assertEquals("Telecoms Lab", row.get("venue"));
        assertEquals(60_000, row.get("fee"));
        assertEquals(true, row.get("published"));
        assertEquals(Instant.parse("2030-11-20T06:00:00Z"), ((java.sql.Timestamp) row.get("starts_at")).toInstant());
        assertTrue(indexOf(list().get("items"), id) >= 0, "the new bootcamp is listed");
    }

    @Test
    void auditsTheNewBootcampWithoutTheTopicOrTheInstructor() throws Exception {
        JsonNode created = ZodShape.parse(mockMvc.perform(create("""
                {"topic": "Secret Antenna Workshop", "instructor": "Faith Namuyanja", "date": "2030-11-20",
                 "start": "09:00", "end": "16:00", "capacity": 25, "fee": 0}""", admin()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        List<Map<String, Object>> lines = AuditTestSupport.lines(
                entityManager, jdbcTemplate, UUID.fromString(created.get("id").asString()));

        assertEquals(1, lines.size());
        assertEquals("bootcamp.created", lines.get(0).get("action"));
        assertEquals("training_session", lines.get(0).get("entity_type"));
        assertEquals(telecoms, lines.get(0).get("department_id"));
        AuditTestSupport.assertNoPersonalData(lines.get(0), "Secret", "Antenna", "Faith", "Namuyanja");
    }

    @Test
    void aCoordinatorOfTheTelecomsDepartmentMayCreateAndAnotherDepartmentsMayNot() throws Exception {
        String ours = "tel-" + UUID.randomUUID() + "@test.local";
        String theirs = "tex-" + UUID.randomUUID() + "@test.local";
        OperationsTestData.member(jdbcTemplate, telecoms, ours);
        OperationsTestData.member(jdbcTemplate, OperationsTestData.sector(jdbcTemplate, "Textile"), theirs);

        mockMvc.perform(create(validBody(), user(ours).roles("DEPARTMENT_COORDINATOR"))).andExpect(status().isCreated());
        mockMvc.perform(create(validBody(), user(theirs).roles("DEPARTMENT_COORDINATOR"))).andExpect(status().isForbidden());
    }

    @Test
    void everyOtherRoleIsRefusedAndAnAnonymousCallerIsNotSignedIn() throws Exception {
        for (String role : BootcampTestData.NON_MANAGE_ROLES) {
            mockMvc.perform(create(validBody(), as(role))).andExpect(status().isForbidden());
        }
        mockMvc.perform(post("/telecoms/bootcamps").contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"instructor\": \"I\", \"date\": \"2030-11-20\", \"start\": \"09:00\", \"end\": \"16:00\", \"capacity\": 5, \"fee\": 0}",
            "{\"topic\": \"T\", \"date\": \"2030-11-20\", \"start\": \"09:00\", \"end\": \"16:00\", \"capacity\": 5, \"fee\": 0}",
            "{\"topic\": \"T\", \"instructor\": \"I\", \"start\": \"09:00\", \"end\": \"16:00\", \"capacity\": 5, \"fee\": 0}",
            "{\"topic\": \"T\", \"instructor\": \"I\", \"date\": \"2030-11-20\", \"end\": \"16:00\", \"capacity\": 5, \"fee\": 0}",
            "{\"topic\": \"T\", \"instructor\": \"I\", \"date\": \"2030-11-20\", \"start\": \"09:00\", \"capacity\": 5, \"fee\": 0}",
            "{\"topic\": \"T\", \"instructor\": \"I\", \"date\": \"2030-11-20\", \"start\": \"09:00\", \"end\": \"16:00\", \"fee\": 0}",
            "{\"topic\": \"T\", \"instructor\": \"I\", \"date\": \"2030-11-20\", \"start\": \"09:00\", \"end\": \"16:00\", \"capacity\": 5}",
            "{\"topic\": \"  \", \"instructor\": \"I\", \"date\": \"2030-11-20\", \"start\": \"09:00\", \"end\": \"16:00\", \"capacity\": 5, \"fee\": 0}",
            "{\"topic\": \"T\", \"instructor\": \"I\", \"date\": \"2030-11-20\", \"start\": \"09:00\", \"end\": \"16:00\", \"capacity\": 0, \"fee\": 0}",
            "{\"topic\": \"T\", \"instructor\": \"I\", \"date\": \"2030-11-20\", \"start\": \"09:00\", \"end\": \"16:00\", \"capacity\": 5, \"fee\": -1}"
    })
    void refusesAnInvalidFormWithValidationErrors(String json) throws Exception {
        mockMvc.perform(create(json, admin()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "nope",
            "{\"topic\": \"T\", \"instructor\": \"I\", \"date\": \"20 Nov\", \"start\": \"09:00\", \"end\": \"16:00\", \"capacity\": 5, \"fee\": 0}",
            "{\"topic\": \"T\", \"instructor\": \"I\", \"date\": \"2030-11-20\", \"start\": \"9\", \"end\": \"16:00\", \"capacity\": 5, \"fee\": 0}",
            "{\"topic\": \"T\", \"instructor\": \"I\", \"date\": \"2030-11-20\", \"start\": \"09:00\", \"end\": \"16:00\", \"capacity\": \"lots\", \"fee\": 0}",
            "{\"topic\": \"T\", \"instructor\": \"I\", \"date\": \"2030-11-20\", \"start\": \"09:00\", \"end\": \"16:00\", \"capacity\": 5, \"fee\": 0, \"venue\": \"x\"}"
    })
    void refusesABodyItCannotReadAsMalformed(String json) throws Exception {
        mockMvc.perform(create(json, admin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_BODY"));
    }

    @Test
    void aBootcampThatEndsBeforeItStartsIsRefused() throws Exception {
        mockMvc.perform(create("""
                {"topic": "T", "instructor": "I", "date": "2030-11-20", "start": "16:00", "end": "09:00",
                 "capacity": 5, "fee": 0}""", admin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("A session must end after it starts."));
    }

    @Test
    void withoutATelecomsDepartmentNothingCanBeCreated() throws Exception {
        jdbcTemplate.update("UPDATE sectors SET active = FALSE WHERE id = ?", telecoms);

        mockMvc.perform(create(validBody(), admin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("The Telecoms department has not been set up yet."));
        assertEquals(0, list().get("items").size());
    }

    // ----- helpers

    private TrainingParticipant mark(TrainingSession session, String reg, AttendanceStatus attendance) {
        TrainingParticipant participant = participants.saveAndFlush(
                BootcampTestData.participant(session.getId(), reg, "Person " + reg, reg + "@x.ug", 0));
        if (attendance != AttendanceStatus.UNMARKED) {
            participant.markAttendance(attendance, FUTURE);
            participants.saveAndFlush(participant);
        }
        return participant;
    }

    private JsonNode list() throws Exception {
        return ZodShape.parse(mockMvc.perform(get("/telecoms/bootcamps").with(as("OBSERVER")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private static int indexOf(JsonNode items, UUID id) {
        for (int index = 0; index < items.size(); index++) {
            if (id.toString().equals(items.get(index).get("id").asString())) {
                return index;
            }
        }
        return -1;
    }

    private static String paymentOf(JsonNode enrolled, TrainingParticipant participant) {
        for (JsonNode person : enrolled) {
            if (participant.getId().toString().equals(person.get("id").asString())) {
                return person.get("paymentStatus").asString();
            }
        }
        return null;
    }

    private static String statusOf(JsonNode attendance, TrainingParticipant participant) {
        for (JsonNode entry : attendance) {
            if (participant.getId().toString().equals(entry.get("participantId").asString())) {
                return entry.get("status").asString();
            }
        }
        return null;
    }

    private static String validBody() {
        return """
                {"topic": "T", "instructor": "I", "date": "2030-11-20", "start": "09:00", "end": "16:00",
                 "capacity": 5, "fee": 0}""";
    }

    private static MockHttpServletRequestBuilder create(String json, RequestPostProcessor who) {
        return post("/telecoms/bootcamps").with(who).contentType(MediaType.APPLICATION_JSON).content(json);
    }

    private static RequestPostProcessor admin() {
        return as("ADMINISTRATOR");
    }

    private static RequestPostProcessor as(String role) {
        return user("bc-" + UUID.randomUUID() + "@test.local").roles(role);
    }
}

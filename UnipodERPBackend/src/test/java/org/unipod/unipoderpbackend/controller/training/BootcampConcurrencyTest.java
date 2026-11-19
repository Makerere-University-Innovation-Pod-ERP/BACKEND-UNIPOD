package org.unipod.unipoderpbackend.controller.training;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.unipod.unipoderpbackend.model.training.TrainingParticipant;
import org.unipod.unipoderpbackend.model.training.TrainingSession;
import org.unipod.unipoderpbackend.repository.training.TrainingParticipantRepository;
import org.unipod.unipoderpbackend.repository.training.TrainingSessionRepository;
import org.unipod.unipoderpbackend.support.OperationsTestData;

/**
 * Not transactional on purpose: each request commits on its own thread, so the session row lock, not a
 * shared test transaction, protects the last seat and keeps a refused roster from being half applied.
 */
@SpringBootTest
@AutoConfigureMockMvc
class BootcampConcurrencyTest {
    private static final int ATTEMPTS = 8;
    private static final Instant FUTURE = Instant.parse("2030-11-20T06:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TrainingSessionRepository sessions;

    @Autowired
    private TrainingParticipantRepository participants;

    private UUID telecoms;
    private boolean createdSector;
    private UUID sessionId;

    @BeforeEach
    void seed() {
        Integer existing = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM sectors WHERE lower(name) = 'telecoms'", Integer.class);
        createdSector = existing != null && existing == 0;
        telecoms = OperationsTestData.sector(jdbcTemplate, "Telecoms");
    }

    @AfterEach
    void cleanUp() {
        // audit_log is append-only, so the audit lines these requests wrote are left behind.
        if (sessionId != null) {
            jdbcTemplate.update("DELETE FROM training_participants WHERE session_id = ?", sessionId);
            jdbcTemplate.update("DELETE FROM training_sessions WHERE id = ?", sessionId);
        }
        if (createdSector) {
            jdbcTemplate.update("DELETE FROM sectors WHERE id = ?", telecoms);
        }
    }

    @Test
    void twoPeopleEnrollingForTheLastSeatOnlyOneGetsIt() throws Exception {
        sessionId = sessions.saveAndFlush(BootcampTestData.session(telecoms, FUTURE, 1, 0)).getId();

        List<Integer> statuses = race(index -> post("/telecoms/bootcamps/{id}/enroll", sessionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Racer %d\", \"email\": \"r%d@x.ug\", \"studentNumber\": \"R-%d\"}"
                        .formatted(index, index, index)));

        assertEquals(1, statuses.stream().filter(code -> code == 200).count());
        assertEquals(ATTEMPTS - 1, statuses.stream().filter(code -> code == 409).count());
        assertEquals(1, participants.countSeats(sessionId));
    }

    @Test
    void neverSeatsMoreThanTheCapacity() throws Exception {
        sessionId = sessions.saveAndFlush(BootcampTestData.session(telecoms, FUTURE, 3, 0)).getId();

        List<Integer> statuses = race(index -> post("/telecoms/bootcamps/{id}/enroll", sessionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Racer %d\", \"email\": \"r%d@x.ug\"}".formatted(index, index)));

        assertEquals(3, statuses.stream().filter(code -> code == 200).count());
        assertEquals(ATTEMPTS - 3, statuses.stream().filter(code -> code == 409).count());
        assertEquals(3, participants.countSeats(sessionId));
    }

    @Test
    void aRosterThatIsRefusedPartWayChangesNobody() throws Exception {
        TrainingSession paid = sessions.saveAndFlush(BootcampTestData.session(telecoms, FUTURE, 5, 50_000));
        sessionId = paid.getId();
        TrainingParticipant confirmed = BootcampTestData.participant(sessionId, "C1", "Confirmed", "c@x.ug", 0);
        TrainingParticipant unpaid = BootcampTestData.participant(sessionId, "U1", "Unpaid", "u@x.ug", 50_000);
        participants.saveAndFlush(confirmed);
        participants.saveAndFlush(unpaid);

        // The first entry would be applied; the second is refused (PRESENT before payment), so neither may stick.
        int status = mockMvc.perform(post("/telecoms/bootcamps/{id}/attendance", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("{\"attendance\": [{\"participantId\": \"%s\", \"status\": \"PRESENT\"}, "
                                + "{\"participantId\": \"%s\", \"status\": \"PRESENT\"}]}")
                                .formatted(confirmed.getId(), unpaid.getId()))
                        .with(user("racer-" + UUID.randomUUID() + "@test.local").roles("ADMINISTRATOR")))
                .andReturn().getResponse().getStatus();

        assertEquals(409, status);
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM training_participants WHERE session_id = ? AND attendance <> 'UNMARKED'",
                Integer.class, sessionId));
    }

    private List<Integer> race(java.util.function.IntFunction<org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder> requestFor)
            throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(ATTEMPTS);
        CountDownLatch ready = new CountDownLatch(ATTEMPTS);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        try {
            for (int index = 0; index < ATTEMPTS; index++) {
                var request = requestFor.apply(index)
                        .with(user("racer-" + UUID.randomUUID() + "@test.local").roles("ADMINISTRATOR"));
                results.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return mockMvc.perform(request).andReturn().getResponse().getStatus();
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS), "every worker must reach the start line");
            go.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(result.get(30, TimeUnit.SECONDS));
            }
            return statuses;
        } finally {
            pool.shutdownNow();
            pool.awaitTermination(30, TimeUnit.SECONDS);
        }
    }
}

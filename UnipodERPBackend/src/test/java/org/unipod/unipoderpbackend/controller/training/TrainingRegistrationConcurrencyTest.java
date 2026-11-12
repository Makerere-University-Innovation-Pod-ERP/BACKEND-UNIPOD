package org.unipod.unipoderpbackend.controller.training;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.unipod.unipoderpbackend.model.training.TrainingSession;
import org.unipod.unipoderpbackend.repository.training.TrainingSessionRepository;

/**
 * Not transactional on purpose: every request must commit on its own thread, so the database
 * lock (not a shared test transaction) is what keeps the seat count honest.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TrainingRegistrationConcurrencyTest {
    private static final int ATTEMPTS = 8;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TrainingSessionRepository sessions;

    @Autowired
    private Clock clock;

    private UUID departmentId;
    private UUID sessionId;

    @BeforeEach
    void seed() {
        departmentId = TrainingFixtures.department(jdbcTemplate);
    }

    @AfterEach
    void cleanUp() {
        // audit_log is append-only, so the audit lines these requests wrote are left behind.
        if (sessionId != null) {
            jdbcTemplate.update("DELETE FROM training_participants WHERE session_id = ?", sessionId);
            jdbcTemplate.update("DELETE FROM training_sessions WHERE id = ?", sessionId);
        }
        jdbcTemplate.update("DELETE FROM sectors WHERE id = ?", departmentId);
    }

    @Test
    void neverSeatsMoreAttendeesThanTheCapacity() throws Exception {
        int capacity = 3;
        sessionId = saveSession(capacity);

        List<Integer> statuses = raceRegistrations(index -> "R-" + index);

        assertEquals(capacity, statuses.stream().filter(code -> code == 201).count());
        assertEquals(ATTEMPTS - capacity, statuses.stream().filter(code -> code == 409).count());
        assertEquals(capacity, seatsHeld());
    }

    @Test
    void theSameRegNumberRacingForAFreeSeatIsAcceptedOnlyOnce() throws Exception {
        sessionId = saveSession(ATTEMPTS);

        List<Integer> statuses = raceRegistrations(index -> "R-SAME");

        assertEquals(1, statuses.stream().filter(code -> code == 201).count());
        assertEquals(ATTEMPTS - 1, statuses.stream().filter(code -> code == 409).count());
        assertEquals(1, seatsHeld());
    }

    private List<Integer> raceRegistrations(IntFunction<String> regNumberFor) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(ATTEMPTS);
        CountDownLatch ready = new CountDownLatch(ATTEMPTS);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        try {
            for (int index = 0; index < ATTEMPTS; index++) {
                String regNumber = regNumberFor.apply(index);
                Callable<Integer> attempt = () -> {
                    ready.countDown();
                    go.await();
                    return mockMvc.perform(post("/operations/training/sessions/{id}/participants", sessionId)
                                    .with(user("racer-" + UUID.randomUUID() + "@test.local").roles("ADMINISTRATOR"))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("""
                                            {"name": "Racer", "regNumber": "%s", "department": "Engineering"}
                                            """.formatted(regNumber)))
                            .andReturn().getResponse().getStatus();
                };
                results.add(pool.submit(attempt));
            }
            assertTrue(ready.await(30, TimeUnit.SECONDS), "the racing threads did not all start");
            go.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(result.get(60, TimeUnit.SECONDS));
            }
            return statuses;
        } finally {
            pool.shutdownNow();
        }
    }

    private UUID saveSession(int capacity) {
        TrainingSession session = sessions.saveAndFlush(TrainingFixtures.session(
                departmentId, clock.instant().plus(Duration.ofDays(3)), null, capacity, 0, true));
        return session.getId();
    }

    private long seatsHeld() {
        Long seats = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM training_participants WHERE session_id = ? AND status <> 'CANCELLED'",
                Long.class, sessionId);
        return seats;
    }
}

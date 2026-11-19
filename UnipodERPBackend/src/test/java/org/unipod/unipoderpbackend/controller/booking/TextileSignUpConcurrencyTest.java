package org.unipod.unipoderpbackend.controller.booking;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import org.unipod.unipoderpbackend.support.OperationsTestData;

/** Not transactional on purpose: the booking's row lock, not a shared test transaction, protects the last place. */
@SpringBootTest
@AutoConfigureMockMvc
class TextileSignUpConcurrencyTest {
    private static final int ATTEMPTS = 8;
    private static final int PLACES = 3;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private java.util.Map<UUID, String> movedAside = java.util.Map.of();
    private UUID workshop;
    private UUID booking;

    @BeforeEach
    void seed() {
        movedAside = OperationsTestData.moveFacilitiesAside(jdbcTemplate, "Textile");
        workshop = OperationsTestData.facility(jdbcTemplate, "Textile", 30);
        Instant starts = Instant.parse("2030-03-05T06:00:00Z");
        booking = OperationsTestData.booking(jdbcTemplate, workshop, starts, starts.plusSeconds(7200), "APPROVED", PLACES, "Class");
        OperationsTestData.bookingDetail(jdbcTemplate, booking, "TEXTILE", "TRAINING", "a@test.local", null, null, PLACES);
    }

    @AfterEach
    void cleanUp() {
        // audit_log is append-only, so the audit lines these requests wrote are left behind.
        if (workshop != null) {
            jdbcTemplate.update("DELETE FROM bookings WHERE facility_id = ?", workshop);
            jdbcTemplate.update("DELETE FROM facilities WHERE id = ?", workshop);
        }
        OperationsTestData.restoreFacilities(jdbcTemplate, movedAside);
    }

    @Test
    void neverSignsUpMoreThanThePlaces() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(ATTEMPTS);
        CountDownLatch ready = new CountDownLatch(ATTEMPTS);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        try {
            for (int index = 0; index < ATTEMPTS; index++) {
                var request = post("/textile/bookings/{id}/attendees", booking)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"Person " + index + "\"}")
                        .with(user("racer-" + UUID.randomUUID() + "@test.local").roles("ADMINISTRATOR"));
                results.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return mockMvc.perform(request).andReturn().getResponse().getStatus();
                }));
            }
            ready.await(10, TimeUnit.SECONDS);
            go.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(result.get(30, TimeUnit.SECONDS));
            }
            assertEquals(PLACES, statuses.stream().filter(code -> code == 201).count());
            assertEquals(ATTEMPTS - PLACES, statuses.stream().filter(code -> code == 409).count());
            assertEquals(PLACES, jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM booking_attendees WHERE booking_id = ?", Integer.class, booking));
        } finally {
            pool.shutdownNow();
        }
    }
}

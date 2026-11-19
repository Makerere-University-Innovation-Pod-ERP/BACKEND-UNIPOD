package org.unipod.unipoderpbackend.controller.booking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.unipod.unipoderpbackend.support.OperationsTestData;

/**
 * Not transactional on purpose: every request commits on its own thread, so the facility row lock, not a
 * shared test transaction, is what keeps two bookings from taking the same hour.
 */
@SpringBootTest
@AutoConfigureMockMvc
class LabBookingConcurrencyTest {
    private static final int ATTEMPTS = 6;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private java.util.Map<UUID, String> movedAside = java.util.Map.of();
    private UUID lab;

    @BeforeEach
    void seedLab() {
        // Existing facilities of the department are moved aside (and put back) so this one is the one used.
        movedAside = OperationsTestData.moveFacilitiesAside(jdbcTemplate, "Telecoms");
        lab = OperationsTestData.facility(jdbcTemplate, "Telecoms", 10);
    }

    @AfterEach
    void cleanUp() {
        // audit_log is append-only, so the audit lines these requests wrote are left behind.
        if (lab != null) {
            jdbcTemplate.update("DELETE FROM bookings WHERE facility_id = ?", lab);
            jdbcTemplate.update("DELETE FROM facilities WHERE id = ?", lab);
        }
        OperationsTestData.restoreFacilities(jdbcTemplate, movedAside);
    }

    @Test
    void overlappingBookingsRacingForTheSameHourAreAcceptedOnlyOnce() throws Exception {
        List<Integer> statuses = race(index -> body("09:00", "11:00"));

        assertEquals(1, statuses.stream().filter(code -> code == 201).count());
        assertEquals(ATTEMPTS - 1, statuses.stream().filter(code -> code == 409).count());
        assertEquals(1, activeBookings());
    }

    @Test
    void partiallyOverlappingBookingsNeverBothSucceed() throws Exception {
        // 09:00-10:00, 09:30-10:30, 10:00-11:00, ...: neighbours overlap, so at most every other one fits.
        List<Integer> statuses = race(index -> body(
                "%02d:%02d".formatted(9 + index / 2, (index % 2) * 30),
                "%02d:%02d".formatted(10 + index / 2, (index % 2) * 30)));

        long accepted = statuses.stream().filter(code -> code == 201).count();
        assertEquals(accepted, activeBookings(), "every accepted booking is stored, no refused one is");
        assertEquals(0, overlappingPairs(), "no two stored bookings may overlap");
        assertEquals(ATTEMPTS - accepted, statuses.stream().filter(code -> code == 409).count());
    }

    private static String body(String start, String end) {
        return """
                {"date": "2026-12-08", "start": "%s", "end": "%s", "purpose": "Race", "groupSize": 2}""".formatted(start, end);
    }

    private int activeBookings() {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM bookings WHERE facility_id = ? AND status = 'APPROVED'", Integer.class, lab);
    }

    private int overlappingPairs() {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM bookings a JOIN bookings b ON a.facility_id = b.facility_id AND a.id < b.id "
                        + "AND a.starts_at < b.ends_at AND b.starts_at < a.ends_at WHERE a.facility_id = ?",
                Integer.class, lab);
    }

    private List<Integer> race(IntFunction<String> bodyFor) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(ATTEMPTS);
        CountDownLatch ready = new CountDownLatch(ATTEMPTS);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        try {
            for (int index = 0; index < ATTEMPTS; index++) {
                MockHttpServletRequestBuilder request = post("/telecoms/lab-bookings")
                        .contentType(MediaType.APPLICATION_JSON).content(bodyFor.apply(index))
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

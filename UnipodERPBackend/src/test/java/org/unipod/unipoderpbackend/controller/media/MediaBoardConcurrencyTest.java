package org.unipod.unipoderpbackend.controller.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Not transactional on purpose: each request commits on its own thread, so the row lock, not a shared
 * test transaction, decides who moves the card.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MediaBoardConcurrencyTest {
    private static final int ATTEMPTS = 6;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID requestId;

    @AfterEach
    void cleanUp() {
        // audit_log is append-only, so the audit line the winner wrote is left behind.
        if (requestId != null) {
            jdbcTemplate.update("DELETE FROM media_requests WHERE id = ?", requestId);
        }
    }

    @Test
    void ofSeveralPeopleMovingTheSameCardOnlyOneSucceeds() throws Exception {
        requestId = MediaBoardTestData.request(jdbcTemplate, "Race", "REQUESTED", Instant.parse("2026-11-12T05:00:00Z"));

        List<Integer> statuses = race();

        assertEquals(1, statuses.stream().filter(code -> code == 200).count());
        assertEquals(ATTEMPTS - 1, statuses.stream().filter(code -> code == 409).count());
        assertEquals("SCHEDULED", jdbcTemplate.queryForObject(
                "SELECT status FROM media_requests WHERE id = ?", String.class, requestId));
        Integer audited = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_log WHERE entity_id = ? AND action = 'media_request.status_changed'",
                Integer.class, requestId);
        assertEquals(1, audited, "only the request that won may leave an audit line");
    }

    private List<Integer> race() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(ATTEMPTS);
        CountDownLatch ready = new CountDownLatch(ATTEMPTS);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        try {
            for (int index = 0; index < ATTEMPTS; index++) {
                var request = patch("/operations/media/requests/{id}/status", requestId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"SCHEDULED\"}")
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
            return statuses;
        } finally {
            pool.shutdownNow();
        }
    }
}

package org.unipod.unipoderpbackend.controller.partner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.unipod.unipoderpbackend.model.partner.Partner;
import org.unipod.unipoderpbackend.model.partner.PartnerType;
import org.unipod.unipoderpbackend.repository.partner.PartnerRepository;

/** Not transactional: the database, not the test transaction, must decide who wins. */
@SpringBootTest
@AutoConfigureMockMvc
class PartnerUpdateConcurrencyTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PartnerRepository repository;

    @Autowired
    private Clock clock;

    private final List<UUID> created = new ArrayList<>();

    @AfterEach
    void deleteSeededPartners() {
        repository.deleteAllById(created);
    }

    @Test
    void twoEditsFromTheSameVersionLetExactlyOneWin() throws Exception {
        Partner partner = seed("Concurrent Edit");

        List<Integer> statuses = runTogether(
                patchStatus(partner.getId(), "{\"version\": 0, \"notes\": \"edit one\"}"),
                patchStatus(partner.getId(), "{\"version\": 0, \"notes\": \"edit two\"}"));

        assertEquals(List.of(200, 409), statuses.stream().sorted().toList());
        assertEquals(1L, repository.findById(partner.getId()).orElseThrow().getVersion());
    }

    @Test
    void twoRenamesToTheSameNameLetExactlyOneWin() throws Exception {
        Partner first = seed("Rename Race A");
        Partner second = seed("Rename Race B");
        String contested = "Contested " + UUID.randomUUID().toString().substring(0, 8);
        String body = "{\"version\": 0, \"name\": \"" + contested + "\"}";

        List<Integer> statuses = runTogether(
                patchStatus(first.getId(), body),
                patchStatus(second.getId(), body));

        assertEquals(List.of(200, 409), statuses.stream().sorted().toList());
    }

    @Test
    void overlappingMouRenewalsNeverFailWithAServerError() throws Exception {
        Partner partner = seed("Concurrent Renewal");

        List<Integer> statuses = runTogether(
                putMouStatus(partner.getId(), "{\"renewalDate\": \"2030-01-01\", \"documentReference\": \"A\"}"),
                putMouStatus(partner.getId(), "{\"renewalDate\": \"2031-01-01\", \"documentReference\": \"B\"}"));

        // Without a version precondition the later request may succeed after the first commits,
        // or lose the race and get 409; either way one whole renewal is stored and nothing returns 500.
        assertTrue(statuses.stream().allMatch(code -> code == 200 || code == 409), statuses::toString);
        assertTrue(statuses.contains(200), statuses::toString);
        Partner stored = repository.findById(partner.getId()).orElseThrow();
        assertTrue(
                (LocalDate.of(2030, 1, 1).equals(stored.getRenewalDate()) && "A".equals(stored.getDocumentReference()))
                        || (LocalDate.of(2031, 1, 1).equals(stored.getRenewalDate())
                                && "B".equals(stored.getDocumentReference())),
                () -> stored.getRenewalDate() + " / " + stored.getDocumentReference());
    }

    private Callable<Integer> putMouStatus(UUID id, String body) {
        return () -> mockMvc.perform(MockMvcRequestBuilders.put("/operations/partners/{id}/mou", id)
                        .with(user("manager-" + UUID.randomUUID() + "@test.local").roles("DEPARTMENT_COORDINATOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn().getResponse().getStatus();
    }

    private Callable<Integer> patchStatus(UUID id, String body) {
        return () -> mockMvc.perform(MockMvcRequestBuilders.patch("/operations/partners/{id}", id)
                        .with(user("manager-" + UUID.randomUUID() + "@test.local").roles("DEPARTMENT_COORDINATOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn().getResponse().getStatus();
    }

    // Both calls wait at a gate and are released together to maximise overlap.
    private static List<Integer> runTogether(Callable<Integer> first, Callable<Integer> second) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (Callable<Integer> call : List.of(first, second)) {
                results.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return call.call();
                }));
            }
            ready.await();
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

    private Partner seed(String prefix) {
        Partner partner = repository.saveAndFlush(new Partner(UUID.randomUUID(),
                prefix + " " + UUID.randomUUID().toString().substring(0, 8), PartnerType.FUNDER,
                null, null, null, null, clock.instant()));
        created.add(partner.getId());
        return partner;
    }
}

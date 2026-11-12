package org.unipod.unipoderpbackend.controller.partner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.unipod.unipoderpbackend.model.audit.AuditEvent;
import org.unipod.unipoderpbackend.model.partner.Partner;
import org.unipod.unipoderpbackend.model.partner.PartnerType;
import org.unipod.unipoderpbackend.repository.partner.PartnerRepository;
import org.unipod.unipoderpbackend.service.audit.AuditService;

/** Not transactional: proves a failed audit write rolls the partner change back with it. */
@SpringBootTest
@AutoConfigureMockMvc
class PartnerAuditAtomicityTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PartnerRepository repository;

    @Autowired
    private Clock clock;

    // A plain mock: stubbing a spy would run the real MANDATORY transaction check.
    @MockitoBean
    private AuditService audit;

    private final List<UUID> created = new ArrayList<>();

    @AfterEach
    void deleteSeededPartners() {
        repository.deleteAllById(created);
    }

    @Test
    void failedAuditWriteRollsBackPartnerCreation() throws Exception {
        doThrow(new IllegalStateException("audit unavailable")).when(audit).log(any(AuditEvent.class));
        String name = "Atomic Create " + UUID.randomUUID().toString().substring(0, 8);

        int status = mockMvc.perform(MockMvcRequestBuilders.post("/operations/partners")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"" + name + "\", \"type\": \"FUNDER\"}"))
                .andReturn().getResponse().getStatus();

        assertEquals(500, status);
        assertTrue(repository.findAllByRenewalOrder().stream().noneMatch(p -> name.equals(p.getName())));
    }

    @Test
    void failedAuditWriteRollsBackPartnerUpdate() throws Exception {
        Partner partner = seed();
        doThrow(new IllegalStateException("audit unavailable")).when(audit).log(any(AuditEvent.class));

        int status = mockMvc.perform(MockMvcRequestBuilders.patch("/operations/partners/{id}", partner.getId())
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\": 0, \"notes\": \"should not persist\"}"))
                .andReturn().getResponse().getStatus();

        assertEquals(500, status);
        Partner stored = repository.findById(partner.getId()).orElseThrow();
        assertNull(stored.getNotes());
        assertEquals(0L, stored.getVersion());
    }

    @Test
    void failedAuditWriteRollsBackMouRenewal() throws Exception {
        Partner partner = seed();
        doThrow(new IllegalStateException("audit unavailable")).when(audit).log(any(AuditEvent.class));

        int status = mockMvc.perform(MockMvcRequestBuilders.put("/operations/partners/{id}/mou", partner.getId())
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"renewalDate\": \"2031-01-01\", \"documentReference\": \"MOU-X\"}"))
                .andReturn().getResponse().getStatus();

        assertEquals(500, status);
        Partner stored = repository.findById(partner.getId()).orElseThrow();
        assertNull(stored.getRenewalDate());
        assertNull(stored.getDocumentReference());
    }

    private Partner seed() {
        Partner partner = repository.saveAndFlush(new Partner(UUID.randomUUID(),
                "Atomic " + UUID.randomUUID().toString().substring(0, 8), PartnerType.FUNDER,
                null, null, null, null, clock.instant()));
        created.add(partner.getId());
        return partner;
    }

    private static RequestPostProcessor admin() {
        return user("atomic-" + UUID.randomUUID() + "@test.local").roles("ADMINISTRATOR");
    }
}

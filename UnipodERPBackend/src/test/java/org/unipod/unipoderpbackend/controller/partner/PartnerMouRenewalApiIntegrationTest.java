package org.unipod.unipoderpbackend.controller.partner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.model.partner.Partner;
import org.unipod.unipoderpbackend.model.partner.PartnerType;
import org.unipod.unipoderpbackend.repository.partner.PartnerRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PartnerMouRenewalApiIntegrationTest {
    private static final String[] NON_MANAGE_ROLES = {
            "INNOVATION_COORDINATOR", "RECEPTIONIST", "FACT_FINDING_OFFICER", "REVIEWER",
            "SECTOR_OFFICER", "IP_LIAISON", "FINANCE_OFFICER", "OBSERVER", "SUBMITTER"
    };

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PartnerRepository repository;

    @Autowired
    private Clock clock;

    @Test
    void renewingAnExpiredMouMakesThePartnerActiveAndStoresTheDocumentReference() throws Exception {
        Partner partner = seed(LocalDate.now(clock).minusDays(20));
        LocalDate renewed = LocalDate.now(clock).plusDays(365);

        mockMvc.perform(put(partner.getId(), """
                        {"renewalDate": "%s", "documentReference": "MOU-2027-001"}
                        """.formatted(renewed), manager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(partner.getId().toString()))
                .andExpect(jsonPath("$.renewalDate").value(renewed.toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.name").value(partner.getName()));

        assertEquals("MOU-2027-001", repository.findById(partner.getId()).orElseThrow().getDocumentReference());
    }

    @Test
    void renewalDateCanBeSetOnAPartnerThatHadNone() throws Exception {
        Partner partner = seed(null);

        mockMvc.perform(put(partner.getId(), """
                        {"renewalDate": "%s"}
                        """.formatted(LocalDate.now(clock).plusDays(45)), manager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RENEWAL_DUE"));
    }

    @Test
    void omittingTheDocumentReferenceClearsThePreviousOne() throws Exception {
        Partner partner = seed(null);
        String date = LocalDate.now(clock).plusDays(100).toString();
        mockMvc.perform(put(partner.getId(), "{\"renewalDate\": \"" + date + "\", \"documentReference\": \"MOU-1\"}",
                manager())).andExpect(status().isOk());

        mockMvc.perform(put(partner.getId(), "{\"renewalDate\": \"" + date + "\"}", manager()))
                .andExpect(status().isOk());

        assertNull(repository.findById(partner.getId()).orElseThrow().getDocumentReference());
    }

    @Test
    void administratorCanRecordARenewal() throws Exception {
        Partner partner = seed(null);

        mockMvc.perform(put(partner.getId(), "{\"renewalDate\": \"2030-01-01\"}",
                        user(actor()).roles("ADMINISTRATOR")))
                .andExpect(status().isOk());
    }

    @Test
    void otherRolesAreForbidden() throws Exception {
        Partner partner = seed(null);

        for (String role : NON_MANAGE_ROLES) {
            mockMvc.perform(put(partner.getId(), "{\"renewalDate\": \"2030-01-01\"}", user(actor()).roles(role)))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void unknownPartnerReturnsNotFound() throws Exception {
        mockMvc.perform(put(UUID.randomUUID(), "{\"renewalDate\": \"2030-01-01\"}", manager()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Partner not found."));
    }

    @Test
    void rejectsInvalidValuesWithValidationError() throws Exception {
        Partner partner = seed(null);
        String[] invalidBodies = {
                "{}",
                "{\"renewalDate\": null}",
                "{\"documentReference\": \"MOU-1\"}",
                "{\"renewalDate\": \"2030-01-01\", \"documentReference\": \"" + "r".repeat(501) + "\"}"
        };
        for (String body : invalidBodies) {
            mockMvc.perform(put(partner.getId(), body, manager()))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
    }

    @Test
    void validationErrorNamesTheOffendingField() throws Exception {
        Partner partner = seed(null);

        mockMvc.perform(put(partner.getId(), "{}", manager()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[*].field", hasItem("renewalDate")));
    }

    @Test
    void rejectsMalformedBodiesWithBadRequest() throws Exception {
        Partner partner = seed(null);
        String[] malformedBodies = {
                "{\"renewalDate\": \"29 Oct 2026\"}",
                "{\"renewalDate\": \"2030-01-01\", \"status\": \"ACTIVE\"}",
                "not json"
        };
        for (String body : malformedBodies) {
            mockMvc.perform(put(partner.getId(), body, manager()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("MALFORMED_BODY"));
        }
    }

    @Test
    void malformedIdReturnsBadRequest() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.put("/operations/partners/not-a-uuid/mou")
                        .with(manager())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"renewalDate\": \"2030-01-01\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void renewalDatesOutsideTheSupportedRangeAreRejectedWithBadRequest() throws Exception {
        Partner partner = seed(null);

        mockMvc.perform(put(partner.getId(), """
                        {"renewalDate": "+999999999-12-31"}
                        """, manager()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Renewal date must be between 1900 and 2999."));
    }

    private static RequestBuilder put(UUID id, String body, RequestPostProcessor principal) {
        return MockMvcRequestBuilders.put("/operations/partners/{id}/mou", id)
                .with(principal)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private Partner seed(LocalDate renewalDate) {
        return repository.saveAndFlush(new Partner(UUID.randomUUID(),
                "Mou Co " + UUID.randomUUID().toString().substring(0, 8), PartnerType.FUNDER, renewalDate,
                null, null, null, clock.instant()));
    }

    private static String actor() {
        return "manager-" + UUID.randomUUID() + "@test.local";
    }

    private static RequestPostProcessor manager() {
        return user(actor()).roles("ADMINISTRATOR");
    }
}

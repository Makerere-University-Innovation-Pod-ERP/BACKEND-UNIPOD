package org.unipod.unipoderpbackend.controller.partner;

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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.model.partner.Partner;
import org.unipod.unipoderpbackend.model.partner.PartnerType;
import org.unipod.unipoderpbackend.repository.partner.PartnerRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PartnerUpdateApiIntegrationTest {
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
    void updatesOnlyTheSentFieldsAndBumpsTheVersion() throws Exception {
        Partner partner = seed(uniqueName("Before"));
        String newName = uniqueName("After");

        patch(partner, """
                {"version": 0, "name": "%s", "type": "ACADEMIC_PARTNER", "renewalDate": "2028-05-06",
                 "notes": "Updated note"}
                """.formatted(newName))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(partner.getId().toString()))
                .andExpect(jsonPath("$.name").value(newName))
                .andExpect(jsonPath("$.type").value("ACADEMIC_PARTNER"))
                .andExpect(jsonPath("$.renewalDate").value("2028-05-06"))
                .andExpect(jsonPath("$.notes").value("Updated note"))
                .andExpect(jsonPath("$.contactPerson").value("Jane Doe"))
                .andExpect(jsonPath("$.contactEmail").value("jane@partner.test"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void statusFollowsTheNewRenewalDate() throws Exception {
        Partner partner = seed(uniqueName("Status"));

        patch(partner, """
                {"version": 0, "renewalDate": "%s"}
                """.formatted(LocalDate.now(clock).plusDays(10)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXPIRING_SOON"));
    }

    @Test
    void blankTextClearsContactDetails() throws Exception {
        Partner partner = seed(uniqueName("Clear"));

        patch(partner, """
                {"version": 0, "contactPerson": "", "contactEmail": "", "notes": ""}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contactPerson").doesNotExist())
                .andExpect(jsonPath("$.contactEmail").doesNotExist())
                .andExpect(jsonPath("$.notes").doesNotExist());
    }

    @Test
    void renamingToTheSameNameInAnotherCaseIsAllowed() throws Exception {
        String name = uniqueName("Case Change");
        Partner partner = seed(name);

        patch(partner, """
                {"version": 0, "name": "%s"}
                """.formatted(name.toUpperCase()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(name.toUpperCase()));
    }

    @Test
    void bothManageRolesCanUpdate() throws Exception {
        Partner first = seed(uniqueName("Admin"));
        Partner second = seed(uniqueName("Coordinator"));

        mockMvc.perform(patchRequest(first.getId(), "{\"version\": 0, \"notes\": \"x\"}",
                        user(actor()).roles("ADMINISTRATOR")))
                .andExpect(status().isOk());
        mockMvc.perform(patchRequest(second.getId(), "{\"version\": 0, \"notes\": \"x\"}",
                        user(actor()).roles("DEPARTMENT_COORDINATOR")))
                .andExpect(status().isOk());
    }

    @Test
    void otherRolesAreForbidden() throws Exception {
        Partner partner = seed(uniqueName("Forbidden"));

        for (String role : NON_MANAGE_ROLES) {
            mockMvc.perform(patchRequest(partner.getId(), "{\"version\": 0, \"notes\": \"x\"}",
                            user(actor()).roles(role)))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void unknownPartnerReturnsNotFound() throws Exception {
        mockMvc.perform(patchRequest(UUID.randomUUID(), "{\"version\": 0, \"notes\": \"x\"}", manager()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Partner not found."));
    }

    @Test
    void rejectsInvalidValuesWithValidationError() throws Exception {
        Partner partner = seed(uniqueName("Invalid"));
        String[] invalidBodies = {
                "{\"notes\": \"no version\"}",
                "{\"version\": 0, \"name\": \"" + "n".repeat(256) + "\"}",
                "{\"version\": 0, \"contactEmail\": \"not-an-email\"}",
                "{\"version\": 0, \"notes\": \"" + "x".repeat(2001) + "\"}"
        };
        for (String body : invalidBodies) {
            mockMvc.perform(patchRequest(partner.getId(), body, manager()))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
    }

    @Test
    void validationErrorNamesTheOffendingField() throws Exception {
        Partner partner = seed(uniqueName("Field"));

        mockMvc.perform(patchRequest(partner.getId(), "{\"notes\": \"no version\"}", manager()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[*].field", hasItem("version")));
    }

    @Test
    void blankNameIsRejectedByTheDomainWithBadRequest() throws Exception {
        Partner partner = seed(uniqueName("Blank"));

        patch(partner, "{\"version\": 0, \"name\": \"   \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Partner name is required."));
    }

    @Test
    void rejectsMalformedBodiesWithBadRequest() throws Exception {
        Partner partner = seed(uniqueName("Malformed"));
        String[] malformedBodies = {
                "{\"version\": 0, \"type\": \"VENDOR\"}",
                "{\"version\": 0, \"renewalDate\": \"29 Oct 2026\"}",
                "{\"version\": 0, \"status\": \"EXPIRED\"}",
                "{\"version\": \"zero\"}"
        };
        for (String body : malformedBodies) {
            mockMvc.perform(patchRequest(partner.getId(), body, manager()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("MALFORMED_BODY"));
        }
    }

    @Test
    void malformedIdReturnsBadRequest() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders
                        .patch("/operations/partners/not-a-uuid")
                        .with(manager())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\": 0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void staleVersionReturnsConflict() throws Exception {
        Partner partner = seed(uniqueName("Stale"));

        patch(partner, "{\"version\": 0, \"notes\": \"first edit\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));

        patch(partner, "{\"version\": 0, \"notes\": \"second edit from an old copy\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail")
                        .value("This partner was changed by someone else. Reload it and try again."));
    }

    @Test
    void renamingToAnotherPartnersNameReturnsConflict() throws Exception {
        String taken = uniqueName("Taken");
        seed(taken);
        Partner partner = seed(uniqueName("Renamed"));

        patch(partner, """
                {"version": 0, "name": "  %s  "}
                """.formatted(taken.toLowerCase()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("A partner with this name already exists."));
    }

    private ResultActions patch(Partner partner, String body) throws Exception {
        return mockMvc.perform(patchRequest(partner.getId(), body, manager()));
    }

    private static RequestBuilder patchRequest(
            UUID id, String body, RequestPostProcessor principal) {
        return MockMvcRequestBuilders
                .patch("/operations/partners/{id}", id)
                .with(principal)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private Partner seed(String name) {
        return repository.saveAndFlush(new Partner(UUID.randomUUID(), name, PartnerType.FUNDER,
                LocalDate.now(clock).plusDays(300), "Jane Doe", "jane@partner.test", "Original note", clock.instant()));
    }

    private static String uniqueName(String prefix) {
        return prefix + " " + UUID.randomUUID().toString().substring(0, 8);
    }

    private static String actor() {
        return "manager-" + UUID.randomUUID() + "@test.local";
    }

    private static RequestPostProcessor manager() {
        return user(actor()).roles("DEPARTMENT_COORDINATOR");
    }
}

package org.unipod.unipoderpbackend.controller.partner;

import static org.hamcrest.Matchers.contains;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PartnerCreateApiIntegrationTest {
    private static final String[] MANAGE_ROLES = {"ADMINISTRATOR", "DEPARTMENT_COORDINATOR"};
    private static final String[] NON_MANAGE_ROLES = {
            "INNOVATION_COORDINATOR", "RECEPTIONIST", "FACT_FINDING_OFFICER", "REVIEWER",
            "SECTOR_OFFICER", "IP_LIAISON", "FINANCE_OFFICER", "OBSERVER", "SUBMITTER"
    };

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createsPartnerWithAllDetails() throws Exception {
        String name = uniqueName("Full Details Ltd");

        mockMvc.perform(post("/operations/partners")
                        .with(manager())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "type": "INDUSTRY_PARTNER",
                                  "renewalDate": "2027-03-01",
                                  "contactPerson": "Jane Doe",
                                  "contactEmail": "jane@partner.test",
                                  "notes": "Prefers email"
                                }
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name").value(name))
                .andExpect(jsonPath("$.type").value("INDUSTRY_PARTNER"))
                .andExpect(jsonPath("$.renewalDate").value("2027-03-01"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.contactPerson").value("Jane Doe"))
                .andExpect(jsonPath("$.contactEmail").value("jane@partner.test"))
                .andExpect(jsonPath("$.notes").value("Prefers email"));
    }

    @Test
    void createsPartnerWithOnlyRequiredFieldsAndShowsItInTheList() throws Exception {
        String name = uniqueName("Minimal Ltd");

        mockMvc.perform(post("/operations/partners")
                        .with(manager())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "  %s  ", "type": "FUNDER"}
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(name))
                .andExpect(jsonPath("$.renewalDate").doesNotExist())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(get("/operations/partners").with(manager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == '" + name + "')].type").value(contains("FUNDER")));
    }

    @Test
    void derivesStatusFromTheRenewalDateOnCreate() throws Exception {
        mockMvc.perform(post("/operations/partners")
                        .with(manager())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "%s", "type": "ACADEMIC_PARTNER", "renewalDate": "2000-01-01"}
                                """.formatted(uniqueName("Lapsed"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("EXPIRED"));
    }

    @Test
    void bothManageRolesCanCreate() throws Exception {
        for (String role : MANAGE_ROLES) {
            mockMvc.perform(post("/operations/partners")
                            .with(user(actor()).roles(role))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name": "%s", "type": "FUNDER"}
                                    """.formatted(uniqueName(role))))
                    .andExpect(status().isCreated());
        }
    }

    @Test
    void otherRolesAreForbidden() throws Exception {
        for (String role : NON_MANAGE_ROLES) {
            mockMvc.perform(post("/operations/partners")
                            .with(user(actor()).roles(role))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name": "%s", "type": "FUNDER"}
                                    """.formatted(uniqueName(role))))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void rejectsInvalidRequestsWithBadRequest() throws Exception {
        String[] invalidBodies = {
                "{\"type\": \"FUNDER\"}",
                "{\"name\": \"   \", \"type\": \"FUNDER\"}",
                "{\"name\": \"No Type Ltd\"}",
                "{\"name\": \"Bad Type Ltd\", \"type\": \"VENDOR\"}",
                "{\"name\": \"Bad Date Ltd\", \"type\": \"FUNDER\", \"renewalDate\": \"29 Oct 2026\"}",
                "{\"name\": \"Bad Email Ltd\", \"type\": \"FUNDER\", \"contactEmail\": \"not-an-email\"}",
                "{\"name\": \"" + "n".repeat(256) + "\", \"type\": \"FUNDER\"}",
                "{\"name\": \"Long Notes Ltd\", \"type\": \"FUNDER\", \"notes\": \"" + "x".repeat(2001) + "\"}",
                "{\"name\": \"Unknown Field Ltd\", \"type\": \"FUNDER\", \"status\": \"EXPIRED\"}",
                "not json"
        };
        for (String body : invalidBodies) {
            mockMvc.perform(post("/operations/partners")
                            .with(manager())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void duplicateNameIgnoringCaseReturnsConflict() throws Exception {
        String name = uniqueName("Duplicate Ltd");
        mockMvc.perform(post("/operations/partners")
                        .with(manager())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "%s", "type": "FUNDER"}
                                """.formatted(name)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/operations/partners")
                        .with(manager())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": " %s ", "type": "INDUSTRY_PARTNER"}
                                """.formatted(name.toUpperCase())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("A partner with this name already exists."));
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

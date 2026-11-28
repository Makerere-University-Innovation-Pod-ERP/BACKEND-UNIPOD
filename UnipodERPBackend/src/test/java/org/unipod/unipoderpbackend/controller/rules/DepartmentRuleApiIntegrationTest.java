package org.unipod.unipoderpbackend.controller.rules;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.repository.rules.DepartmentRuleRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DepartmentRuleApiIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DepartmentRuleRepository rules;

    @BeforeEach
    void clearRules() {
        rules.deleteAll();
    }

    @Test
    void administratorsCanCreatePublishAndRetireWhileOtherRolesOnlySeePublishedRules() throws Exception {
        String createResponse = mockMvc.perform(post("/rules")
                        .with(user("admin@test.local").roles("ADMINISTRATOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Original work",
                                  "body": "Attribute all sources.",
                                  "scope": "GENERAL",
                                  "scopeId": null,
                                  "mustAccept": true,
                                  "published": false
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.published").value(false))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(createResponse, "$.id");

        mockMvc.perform(get("/rules").with(user("submitter@test.local").roles("SUBMITTER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(patch("/rules/{id}", id)
                        .with(user("admin@test.local").roles("ADMINISTRATOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"published\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.published").value(true))
                .andExpect(jsonPath("$.version").value(1));

        mockMvc.perform(get("/rules").with(user("submitter@test.local").roles("SUBMITTER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(id));

        mockMvc.perform(post("/rules/{id}/retire", id)
                        .with(user("admin@test.local").roles("ADMINISTRATOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.published").value(false));

        mockMvc.perform(get("/rules").with(user("submitter@test.local").roles("SUBMITTER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void onlyAdministratorsCanCreateRules() throws Exception {
        mockMvc.perform(post("/rules")
                        .with(user("submitter@test.local").roles("SUBMITTER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Original work",
                                  "body": "Attribute all sources.",
                                  "scope": "GENERAL",
                                  "mustAccept": true
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void departmentRulesRequireAnActiveDepartment() throws Exception {
        mockMvc.perform(post("/rules")
                        .with(user("admin@test.local").roles("ADMINISTRATOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Department rule",
                                  "body": "Follow this requirement.",
                                  "scope": "SECTOR",
                                  "mustAccept": false
                                }
                                """))
                .andExpect(status().isUnprocessableContent());
    }
}

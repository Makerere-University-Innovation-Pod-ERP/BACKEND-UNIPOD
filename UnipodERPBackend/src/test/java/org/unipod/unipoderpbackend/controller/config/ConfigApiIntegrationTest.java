package org.unipod.unipoderpbackend.controller.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ConfigApiIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void signedInUsersReadTheSeededSettings() throws Exception {
        mockMvc.perform(get("/config").with(user("student@mak.ac.ug").roles("SUBMITTER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caseCodeStudentPrefix").value("STU"))
                .andExpect(jsonPath("$.caseCodeGeneralPrefix").value("GEN"))
                .andExpect(jsonPath("$.caseCodeSequenceDigits").value(6))
                .andExpect(jsonPath("$.defaultReviewMode").value("PARALLEL"))
                .andExpect(jsonPath("$.defaultRequiredReviews").value(2))
                .andExpect(jsonPath("$.recommendationLetterRequiredFor[0]").value("student"))
                .andExpect(jsonPath("$.departments").isArray());
    }

    @Test
    void configNeedsALogin() throws Exception {
        mockMvc.perform(get("/config"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}

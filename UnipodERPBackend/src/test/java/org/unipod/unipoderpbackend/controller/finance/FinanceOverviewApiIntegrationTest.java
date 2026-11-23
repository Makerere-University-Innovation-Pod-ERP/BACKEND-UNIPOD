package org.unipod.unipoderpbackend.controller.finance;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.model.auth.ApiUserRole;
import org.unipod.unipoderpbackend.repository.sector.SectorMemberRepository;
import org.unipod.unipoderpbackend.service.auth.AuthenticationService;
import org.unipod.unipoderpbackend.service.sector.SectorService;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FinanceOverviewApiIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AuthenticationService authentication;
    @Autowired private SectorService sectors;
    @Autowired private SectorMemberRepository members;

    private FinanceTestSupport support;
    private String finance;
    private String submitter;

    @BeforeEach
    void setUp() throws Exception {
        support = new FinanceTestSupport(mockMvc, objectMapper, authentication, sectors, members);
        finance = support.user("overview-finance@example.test", ApiUserRole.FINANCE_OFFICER);
        submitter = support.user("overview-submitter@example.test", ApiUserRole.SUBMITTER);
    }

    @Test
    void financeOverviewReturnsRequiredShape() throws Exception {
        mockMvc.perform(get("/finance/overview").header("Authorization", finance))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eyebrow").value("FINANCE OFFICER"))
                .andExpect(jsonPath("$.greeting").isString())
                .andExpect(jsonPath("$.subtitle").isString())
                .andExpect(jsonPath("$.metrics").isArray())
                .andExpect(jsonPath("$.pipelineStages").isArray())
                .andExpect(jsonPath("$.needsAttention").isArray())
                .andExpect(jsonPath("$.income.currency").value("UGX"))
                .andExpect(jsonPath("$.income.amountFormatted").isString())
                .andExpect(jsonPath("$.income.amount").isNumber())
                .andExpect(jsonPath("$.income.bars").isArray())
                .andExpect(jsonPath("$.facilities").isArray())
                .andExpect(jsonPath("$.recentActivities").isArray());
    }

    @Test
    void submitterCannotReadFinanceOverview() throws Exception {
        mockMvc.perform(get("/finance/overview").header("Authorization", submitter))
                .andExpect(status().isForbidden());
    }
}

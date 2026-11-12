package org.unipod.unipoderpbackend.controller.partner;

import static org.hamcrest.Matchers.contains;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.model.partner.Partner;
import org.unipod.unipoderpbackend.model.partner.PartnerType;
import org.unipod.unipoderpbackend.repository.partner.PartnerRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PartnerApiIntegrationTest {
    private static final String[] READ_ROLES = {
            "ADMINISTRATOR", "DEPARTMENT_COORDINATOR", "INNOVATION_COORDINATOR", "RECEPTIONIST",
            "FACT_FINDING_OFFICER", "REVIEWER", "SECTOR_OFFICER", "IP_LIAISON", "FINANCE_OFFICER", "OBSERVER"
    };

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PartnerRepository repository;

    @Autowired
    private Clock clock;

    @Test
    void listReturnsPartnersOrderedWithDerivedStatusAndFrontendFields() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        LocalDate today = LocalDate.now(clock);
        Partner undated = seed("Zed Undated " + suffix, null);
        Partner active = seed("Active Co " + suffix, today.plusDays(200));
        Partner due = seed("Due Co " + suffix, today.plusDays(45));
        Partner expiring = seed("Expiring Co " + suffix, today.plusDays(10));
        Partner expired = seed("Expired Co " + suffix, today.minusDays(5));

        mockMvc.perform(get("/operations/partners").with(coordinator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name =~ /.*" + suffix + "/)].id").value(contains(
                        expired.getId().toString(), expiring.getId().toString(), due.getId().toString(),
                        active.getId().toString(), undated.getId().toString())))
                .andExpect(jsonPath("$[?(@.id == '" + expired.getId() + "')].status").value(contains("EXPIRED")))
                .andExpect(jsonPath("$[?(@.id == '" + expiring.getId() + "')].status")
                        .value(contains("EXPIRING_SOON")))
                .andExpect(jsonPath("$[?(@.id == '" + due.getId() + "')].status").value(contains("RENEWAL_DUE")))
                .andExpect(jsonPath("$[?(@.id == '" + active.getId() + "')].status").value(contains("ACTIVE")))
                .andExpect(jsonPath("$[?(@.id == '" + undated.getId() + "')].status").value(contains("ACTIVE")))
                .andExpect(jsonPath("$[?(@.id == '" + undated.getId() + "')].renewalDate")
                        .value(contains((Object) null)))
                .andExpect(jsonPath("$[?(@.id == '" + active.getId() + "')].renewalDate")
                        .value(contains(today.plusDays(200).toString())))
                .andExpect(jsonPath("$[?(@.id == '" + active.getId() + "')].type").value(contains("FUNDER")))
                .andExpect(jsonPath("$[?(@.id == '" + active.getId() + "')].contactPerson")
                        .value(contains("Contact " + suffix)));
    }

    @Test
    void listBreaksTiesByNameIgnoringCase() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        LocalDate sameDay = LocalDate.now(clock).plusDays(90);
        Partner datedB = seed("bravo Dated " + suffix, sameDay);
        Partner datedA = seed("Alpha Dated " + suffix, sameDay);
        Partner undatedB = seed("bravo Undated " + suffix, null);
        Partner undatedA = seed("Alpha Undated " + suffix, null);

        mockMvc.perform(get("/operations/partners").with(coordinator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name =~ /.*" + suffix + "/)].id").value(contains(
                        datedA.getId().toString(), datedB.getId().toString(),
                        undatedA.getId().toString(), undatedB.getId().toString())));
    }

    @Test
    void overviewCountsPartnersNeedingAttention() throws Exception {
        String before = mockMvc.perform(get("/operations/partners/overview").with(coordinator()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        int totalBefore = JsonPath.read(before, "$.totalPartners");
        int dueBefore = JsonPath.read(before, "$.renewalDueCount");

        String suffix = UUID.randomUUID().toString().substring(0, 8);
        LocalDate today = LocalDate.now(clock);
        seed("Overview Active " + suffix, today.plusDays(300));
        seed("Overview Undated " + suffix, null);
        seed("Overview Due " + suffix, today.plusDays(60));
        seed("Overview Expiring " + suffix, today.plusDays(30));
        seed("Overview Expired " + suffix, today.minusDays(1));

        mockMvc.perform(get("/operations/partners/overview").with(coordinator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPartners").value(totalBefore + 5))
                .andExpect(jsonPath("$.renewalDueCount").value(dueBefore + 3))
                .andExpect(jsonPath("$.partners.length()").value(totalBefore + 5));
    }

    @Test
    void everyRoleExceptSubmitterCanRead() throws Exception {
        for (String role : READ_ROLES) {
            mockMvc.perform(get("/operations/partners").with(user(actor()).roles(role)))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/operations/partners/overview").with(user(actor()).roles(role)))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void submitterIsForbidden() throws Exception {
        mockMvc.perform(get("/operations/partners").with(user(actor()).roles("SUBMITTER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/operations/partners/overview").with(user(actor()).roles("SUBMITTER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousIsRejected() throws Exception {
        mockMvc.perform(get("/operations/partners"))
                .andExpect(status().isUnauthorized());
    }

    private Partner seed(String name, LocalDate renewalDate) {
        return repository.saveAndFlush(new Partner(UUID.randomUUID(), name, PartnerType.FUNDER, renewalDate,
                "Contact " + name.substring(name.length() - 8), null, null, Instant.now(clock)));
    }

    private static String actor() {
        return "reader-" + UUID.randomUUID() + "@test.local";
    }

    private static RequestPostProcessor coordinator() {
        return user("coordinator-" + UUID.randomUUID() + "@test.local").roles("DEPARTMENT_COORDINATOR");
    }
}

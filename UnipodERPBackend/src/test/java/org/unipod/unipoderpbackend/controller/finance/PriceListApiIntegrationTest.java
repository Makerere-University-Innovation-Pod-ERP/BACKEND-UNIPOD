package org.unipod.unipoderpbackend.controller.finance;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
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
class PriceListApiIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AuthenticationService authentication;
    @Autowired private SectorService sectors;
    @Autowired private SectorMemberRepository members;

    private FinanceTestSupport support;
    private UUID textile;
    private UUID multimedia;
    private String finance;
    private String textileHead;
    private String submitter;

    @BeforeEach
    void setUp() throws Exception {
        support = new FinanceTestSupport(mockMvc, objectMapper, authentication, sectors, members);
        textile = support.department("PL Textile");
        multimedia = support.department("PL Multimedia");
        finance = support.user("pl-finance@example.test", ApiUserRole.FINANCE_OFFICER);
        textileHead = support.user("pl-head@example.test", ApiUserRole.DEPARTMENT_COORDINATOR, textile);
        submitter = support.user("pl-submitter@example.test", ApiUserRole.SUBMITTER);
    }

    @Test
    void financeCreatesActivePricesAndChangesAreAudited() throws Exception {
        String id = create(finance, textile, "Embroidery session", 300000).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.priceUgx").value(300000))
                .andReturn().getResponse().getContentAsString();
        String itemId = support.read(id, "id");

        mockMvc.perform(put("/price-list-items/" + itemId).header("Authorization", finance)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"priceUgx\": 350000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priceUgx").value(350000));

        String admin = support.user("pl-admin@example.test", ApiUserRole.ADMINISTRATOR);
        mockMvc.perform(get("/activity").param("action", "price_list_item.changed").param("entityId", itemId)
                        .header("Authorization", admin))
                .andExpect(jsonPath("$.items[0].before.priceUgx").value(300000))
                .andExpect(jsonPath("$.items[0].after.priceUgx").value(350000));
    }

    @Test
    void departmentHeadsProposeOnlyForTheirOwnDepartment() throws Exception {
        String body = create(textileHead, textile, "Pattern cutting", 400000).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PROPOSED"))
                .andReturn().getResponse().getContentAsString();

        create(textileHead, multimedia, "Studio hour", 500000)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("OUTSIDE_DEPARTMENT"));

        mockMvc.perform(put("/price-list-items/" + support.read(body, "id")).header("Authorization", textileHead)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"ACTIVE\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/price-list-items/" + support.read(body, "id")).header("Authorization", finance)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void submittersSeeOnlyActivePricesAndCannotChangeThem() throws Exception {
        create(finance, textile, "Active service", 300000).andExpect(status().isCreated());
        create(textileHead, textile, "Proposed service", 300000).andExpect(status().isCreated());

        mockMvc.perform(get("/price-list-items").param("departmentId", textile.toString()).header("Authorization", submitter))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].service").value("Active service"));

        create(submitter, textile, "Free stuff", 1)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ROLE_NOT_PERMITTED"));
    }

    @Test
    void priceMustBePositive() throws Exception {
        create(finance, textile, "Nothing", 0)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("priceUgx"));
    }

    private org.springframework.test.web.servlet.ResultActions create(String token, UUID department, String service, long price)
            throws Exception {
        return mockMvc.perform(post("/price-list-items").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(support.json(Map.of("departmentId", department.toString(), "service", service,
                        "unit", "PER_SESSION", "priceUgx", price))));
    }
}

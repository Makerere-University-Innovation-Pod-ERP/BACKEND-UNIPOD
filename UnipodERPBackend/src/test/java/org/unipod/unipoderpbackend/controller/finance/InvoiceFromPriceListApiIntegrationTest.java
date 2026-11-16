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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.model.auth.ApiUserRole;
import org.unipod.unipoderpbackend.repository.sector.SectorMemberRepository;
import org.unipod.unipoderpbackend.service.auth.AuthenticationService;
import org.unipod.unipoderpbackend.service.sector.SectorService;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class InvoiceFromPriceListApiIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AuthenticationService authentication;
    @Autowired private SectorService sectors;
    @Autowired private SectorMemberRepository members;

    private FinanceTestSupport support;
    private String finance;
    private String alice;
    private String itemId;

    @BeforeEach
    void setUp() throws Exception {
        support = new FinanceTestSupport(mockMvc, objectMapper, authentication, sectors, members);
        UUID textile = support.department("IFP Textile");
        finance = support.user("ifp-finance@example.test", ApiUserRole.FINANCE_OFFICER);
        alice = support.user("ifp-alice@example.test", ApiUserRole.SUBMITTER);
        itemId = support.read(mockMvc.perform(post("/price-list-items").header("Authorization", finance)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(support.json(Map.of("departmentId", textile.toString(), "service", "Embroidery",
                                "unit", "PER_SESSION", "priceUgx", 300000))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "id");
    }

    @Test
    void financeBillsFromAPriceAndTheCustomerSeesItInTheirInvoices() throws Exception {
        String body = invoice(finance, 3)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(900000))
                .andExpect(jsonPath("$.department").value("IFP Textile"))
                .andExpect(jsonPath("$.description").value("Embroidery x 3 (per session)"))
                .andExpect(jsonPath("$.status").value("WAITING_FOR_PROOF"))
                .andReturn().getResponse().getContentAsString();

        mockMvc.perform(get("/me/invoices/" + support.read(body, "id")).header("Authorization", alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(900000));
    }

    @Test
    void submittersCannotBillThemselves() throws Exception {
        invoice(alice, 1).andExpect(status().isForbidden());
    }

    @Test
    void retiredPricesCannotBeBilled() throws Exception {
        mockMvc.perform(put("/price-list-items/" + itemId).header("Authorization", finance)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"RETIRED\"}"))
                .andExpect(status().isOk());
        invoice(finance, 1).andExpect(status().isConflict());
    }

    private ResultActions invoice(String token, int quantity) throws Exception {
        return mockMvc.perform(post("/price-list-items/" + itemId + "/invoice").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(support.json(Map.of("customerEmail", "ifp-alice@example.test", "customerName", "Alice",
                        "quantity", quantity, "sourceType", "SESSION"))));
    }
}

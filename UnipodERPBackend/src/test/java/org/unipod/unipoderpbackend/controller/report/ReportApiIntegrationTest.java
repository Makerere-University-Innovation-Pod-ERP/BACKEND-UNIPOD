package org.unipod.unipoderpbackend.controller.report;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.dto.auth.CreateApiUserRequest;
import org.unipod.unipoderpbackend.dto.sector.SectorRequest;
import org.unipod.unipoderpbackend.model.auth.ApiUserRole;
import org.unipod.unipoderpbackend.service.auth.AuthenticationService;
import org.unipod.unipoderpbackend.service.sector.SectorService;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReportApiIntegrationTest {
    private static final String PASSWORD = "report-test-password-1";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AuthenticationService authentication;
    @Autowired private SectorService sectors;

    private String coordinator;
    private String finance;
    private String alice;
    private UUID department;

    @BeforeEach
    void setUp() throws Exception {
        coordinator = user("rep-coordinator@example.test", ApiUserRole.INNOVATION_COORDINATOR);
        finance = user("rep-finance@example.test", ApiUserRole.FINANCE_OFFICER);
        alice = user("rep-alice@example.test", ApiUserRole.SUBMITTER);
        department = sectors.create(new SectorRequest("=REP Telecoms", "telecoms@example.com", true)).id();
    }

    @Test
    void managersReadTheSubmissionReports() throws Exception {
        mockMvc.perform(get("/reports/submissions-by-stage").header("Authorization", coordinator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").isNumber())
                .andExpect(jsonPath("$.byStage.DRAFT").isNumber())
                .andExpect(jsonPath("$.byStage.COMPLETED").isNumber());
        mockMvc.perform(get("/reports/student-vs-public").header("Authorization", coordinator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.student").isNumber())
                .andExpect(jsonPath("$.general").isNumber())
                .andExpect(jsonPath("$.serviceRequests").isNumber());
        mockMvc.perform(get("/reports/completion-rate").header("Authorization", coordinator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completionRate").isNumber());
    }

    @Test
    void submittersCannotReadReports() throws Exception {
        mockMvc.perform(get("/reports/submissions-by-stage").header("Authorization", alice))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ROLE_NOT_PERMITTED"));
    }

    @Test
    void incomeTrendFillsEmptyWeeksWithZeroAndCountsOnlyConfirmedPayments() throws Exception {
        String invoice = paidInvoiceOf(250000);
        unpaidInvoiceOf(999000);
        mockMvc.perform(get("/reports/income-trend").header("Authorization", coordinator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weeks").value(12))
                .andExpect(jsonPath("$.series.length()").value(12))
                .andExpect(jsonPath("$.series[0].incomeUgx").value(0))
                .andExpect(jsonPath("$.series[11].incomeUgx").value(250000))
                .andExpect(jsonPath("$.totalUgx").value(250000));
        mockMvc.perform(get("/reports/income-trend").param("weeks", "60").header("Authorization", coordinator))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void departmentWorkloadCountsOpenInvoices() throws Exception {
        unpaidInvoiceOf(100000);
        mockMvc.perform(get("/reports/department-workload").header("Authorization", coordinator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.departments[?(@.departmentName == '=REP Telecoms')].openInvoices").value(1));
    }

    @Test
    void exportsAreCsvAndOnlyTheRequesterCanDownloadThem() throws Exception {
        unpaidInvoiceOf(100000);
        String body = mockMvc.perform(post("/exports").header("Authorization", coordinator)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"report\": \"DEPARTMENT_WORKLOAD\", \"format\": \"CSV\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileName").value(containsString("department-workload")))
                .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(body).get("id").asString();

        mockMvc.perform(get("/exports/" + id).header("Authorization", coordinator))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(content().string(containsString("department,open_cases,open_invoices")))
                .andExpect(content().string(containsString("'=REP Telecoms,0,1")));
        mockMvc.perform(get("/exports/" + id).header("Authorization", finance))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/exports").header("Authorization", alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"report\": \"INCOME_TREND\", \"format\": \"CSV\"}"))
                .andExpect(status().isForbidden());
    }

    private String paidInvoiceOf(long amount) throws Exception {
        String invoice = unpaidInvoiceOf(amount);
        String uploaded = mockMvc.perform(multipart("/invoices/" + invoice + "/evidence")
                        .file(new MockMultipartFile("file", "proof.pdf", "application/pdf", "%PDF-1.4 proof".getBytes()))
                        .header("Authorization", alice)
                        .param("reference", "FT" + amount).param("amount", String.valueOf(amount))
                        .param("paidOn", LocalDate.now().toString()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String proofId = objectMapper.readTree(uploaded).get("paymentProof").get("id").asString();
        mockMvc.perform(post("/invoices/" + invoice + "/verify").param("proofId", proofId).header("Authorization", finance))
                .andExpect(status().isOk());
        return invoice;
    }

    private String unpaidInvoiceOf(long amount) throws Exception {
        String item = read(mockMvc.perform(post("/price-list-items").header("Authorization", finance)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("departmentId", department.toString(),
                                "service", "Service " + amount, "unit", "PER_ITEM", "priceUgx", amount))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        return read(mockMvc.perform(post("/price-list-items/" + item + "/invoice").header("Authorization", finance)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "customerEmail", "rep-alice@example.test", "sourceType", "REQUEST"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private String read(String body) throws Exception {
        return objectMapper.readTree(body).get("id").asString();
    }

    private String user(String email, ApiUserRole role) throws Exception {
        authentication.createUser(new CreateApiUserRequest(email, PASSWORD, role));
        String response = mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + objectMapper.readTree(response).get("accessToken").asString();
    }
}

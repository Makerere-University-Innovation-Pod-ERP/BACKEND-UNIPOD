package org.unipod.unipoderpbackend.controller;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.dto.auth.CreateApiUserRequest;
import org.unipod.unipoderpbackend.model.auth.ApiUserRole;
import org.unipod.unipoderpbackend.service.auth.AuthenticationService;
import tools.jackson.databind.ObjectMapper;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SubmitterInvoicesBookingsApiIntegrationTest {
    private static final String PASSWORD = "w3-finance-booking-password";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthenticationService authentication;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void submitterCanPayInvoiceDownloadReceiptAndRequestBooking() throws Exception {
        String submitterEmail = "w3-finance-" + UUID.randomUUID() + "@example.test";
        createUser(submitterEmail, ApiUserRole.SUBMITTER);
        String submitterToken = login(submitterEmail, PASSWORD);

        String otherEmail = "w3-finance-other-" + UUID.randomUUID() + "@example.test";
        createUser(otherEmail, ApiUserRole.SUBMITTER);
        String otherToken = login(otherEmail, PASSWORD);

        String financeEmail = "w3-finance-officer-" + UUID.randomUUID() + "@example.test";
        createUser(financeEmail, ApiUserRole.FINANCE_OFFICER);
        String financeToken = login(financeEmail, PASSWORD);

        String coordinatorEmail = "w3-facilities-" + UUID.randomUUID() + "@example.test";
        createUser(coordinatorEmail, ApiUserRole.INNOVATION_COORDINATOR);
        String coordinatorToken = login(coordinatorEmail, PASSWORD);

        String invoiceBody = objectMapper.writeValueAsString(Map.of(
                "customerEmail", submitterEmail,
                "customerName", "W3 Submitter",
                "department", "Innovation",
                "description", "Facility booking half day",
                "amount", 250000,
                "sourceType", "BOOKING",
                "sourceId", "BK-1"
        ));
        String invoiceResponse = mockMvc.perform(post("/invoices")
                        .header("Authorization", "Bearer " + financeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invoiceBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("WAITING_FOR_PROOF"))
                .andExpect(jsonPath("$.amount").value(250000))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String invoiceId = objectMapper.readTree(invoiceResponse).get("id").asText();
        String invoiceNumber = objectMapper.readTree(invoiceResponse).get("number").asText();

        mockMvc.perform(get("/me/invoices")
                        .header("Authorization", "Bearer " + submitterToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].number").value(invoiceNumber));

        mockMvc.perform(get("/me/invoices/{id}", invoiceId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/me/invoices")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        MockMultipartFile proofFile = new MockMultipartFile(
                "file", "proof.pdf", "application/pdf", "%PDF-1.4 W3 proof".getBytes(StandardCharsets.UTF_8)
        );
        String proofResponse = mockMvc.perform(multipart("/me/invoices/{id}/proof", invoiceId)
                        .file(proofFile)
                        .param("reference", "BANK-REF-7788")
                        .param("amount", "250000")
                        .param("paidOn", "2026-11-13")
                        .header("Authorization", "Bearer " + submitterToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("WAITING_FOR_CHECK"))
                .andExpect(jsonPath("$.paymentProof.reference").value("BANK-REF-7788"))
                .andExpect(jsonPath("$.paymentProof.status").value("PENDING"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String proofId = objectMapper.readTree(proofResponse).get("paymentProof").get("id").asText();

        MvcResult proofDownload = mockMvc.perform(get("/me/invoices/{id}/proof/file", invoiceId)
                        .header("Authorization", "Bearer " + submitterToken))
                .andExpect(request().asyncStarted())
                .andReturn();
        mockMvc.perform(asyncDispatch(proofDownload))
                .andExpect(status().isOk())
                .andExpect(content().bytes("%PDF-1.4 W3 proof".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(get("/me/invoices/{id}/proof/file", invoiceId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/invoices/{id}/verify", invoiceId)
                        .param("proofId", proofId)
                        .header("Authorization", "Bearer " + financeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));

        String receiptResponse = mockMvc.perform(post("/invoices/{id}/receipt", invoiceId)
                        .header("Authorization", "Bearer " + financeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECEIPTED"))
                .andExpect(jsonPath("$.receipt.receiptNumber").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String receiptNumber = objectMapper.readTree(receiptResponse)
                .get("receipt").get("receiptNumber").asText();

        mockMvc.perform(get("/me/invoices/{id}/receipt", invoiceId)
                        .header("Authorization", "Bearer " + submitterToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receiptNumber").value(receiptNumber));

        mockMvc.perform(get("/me/invoices/{id}/receipt/download", invoiceId)
                        .header("Authorization", "Bearer " + submitterToken))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(receiptNumber)));

        mockMvc.perform(get("/me/invoices/{id}/receipt", invoiceId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/finance/summary")
                        .header("Authorization", "Bearer " + financeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receipted").isNumber())
                .andExpect(jsonPath("$.collectedUgx").isNumber());

        mockMvc.perform(get("/finance/summary")
                        .header("Authorization", "Bearer " + submitterToken))
                .andExpect(status().isForbidden());

        String aliasInvoiceResponse = mockMvc.perform(post("/invoices")
                        .header("Authorization", "Bearer " + financeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "customerEmail", submitterEmail,
                                "description", "Evidence alias invoice",
                                "amount", 50000,
                                "sourceType", "REQUEST"
                        ))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String aliasInvoiceId = objectMapper.readTree(aliasInvoiceResponse).get("id").asText();

        mockMvc.perform(multipart("/invoices/{id}/evidence", aliasInvoiceId)
                        .file(new MockMultipartFile(
                                "file", "evidence.pdf", "application/pdf",
                                "%PDF-1.4 alias evidence".getBytes(StandardCharsets.UTF_8)
                        ))
                        .param("reference", "BANK-REF-ALIAS")
                        .param("amount", "50000")
                        .param("paidOn", "2026-11-13")
                        .header("Authorization", "Bearer " + submitterToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("WAITING_FOR_CHECK"));

        String facilityBody = objectMapper.writeValueAsString(Map.of(
                "name", "W3 Studio " + UUID.randomUUID(),
                "location", "Innovation Block",
                "capacity", 20,
                "status", "AVAILABLE",
                "department", "Innovation"
        ));
        String facilityResponse = mockMvc.perform(post("/facilities")
                        .header("Authorization", "Bearer " + coordinatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(facilityBody))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String facilityId = objectMapper.readTree(facilityResponse).get("id").asText();

        String bookingBody = objectMapper.writeValueAsString(Map.of(
                "facilityId", facilityId,
                "purpose", "Record a student podcast",
                "startsAt", "2026-12-01T09:00:00Z",
                "endsAt", "2026-12-01T10:00:00Z",
                "attendees", 5
        ));
        String bookingResponse = mockMvc.perform(post("/me/bookings")
                        .header("Authorization", "Bearer " + submitterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.facilityId").value(facilityId))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String bookingId = objectMapper.readTree(bookingResponse).get("id").asText();

        mockMvc.perform(get("/me/bookings")
                        .header("Authorization", "Bearer " + submitterToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(bookingId));

        mockMvc.perform(get("/me/bookings/{id}", bookingId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/me/bookings")
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody))
                .andExpect(status().isConflict());

        mockMvc.perform(patch("/facilities/{id}/status", facilityId)
                        .header("Authorization", "Bearer " + coordinatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"MAINTENANCE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MAINTENANCE"));

        String laterBookingBody = objectMapper.writeValueAsString(Map.of(
                "facilityId", facilityId,
                "purpose", "Maintenance check",
                "startsAt", "2026-12-02T09:00:00Z",
                "endsAt", "2026-12-02T10:00:00Z",
                "attendees", 2
        ));
        mockMvc.perform(post("/me/bookings")
                        .header("Authorization", "Bearer " + submitterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(laterBookingBody))
                .andExpect(status().isConflict());
    }

    private void createUser(String email, ApiUserRole role) {
        authentication.createUser(new CreateApiUserRequest(email, PASSWORD, role));
    }

    private String login(String email, String password) throws Exception {
        String response = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", email, "password", password)
                        )))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }
}

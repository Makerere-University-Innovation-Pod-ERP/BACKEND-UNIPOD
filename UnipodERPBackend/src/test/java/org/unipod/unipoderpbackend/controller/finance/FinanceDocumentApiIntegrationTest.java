package org.unipod.unipoderpbackend.controller.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
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
class FinanceDocumentApiIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AuthenticationService authentication;
    @Autowired private SectorService sectors;
    @Autowired private SectorMemberRepository members;

    private FinanceTestSupport support;
    private String finance;
    private String amina;
    private String other;
    private String invoiceId;

    @BeforeEach
    void setUp() throws Exception {
        support = new FinanceTestSupport(mockMvc, objectMapper, authentication, sectors, members);
        UUID multimedia = support.department("DOC Multimedia");
        finance = support.user("doc-finance@example.test", ApiUserRole.FINANCE_OFFICER);
        amina = support.user("doc-amina@example.test", ApiUserRole.SUBMITTER);
        other = support.user("doc-other@example.test", ApiUserRole.SUBMITTER);
        String item = support.read(mockMvc.perform(post("/price-list-items").header("Authorization", finance)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(support.json(Map.of("departmentId", multimedia.toString(), "service", "Studio recording",
                                "unit", "PER_HOUR", "priceUgx", 300000))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "id");
        invoiceId = support.read(mockMvc.perform(post("/price-list-items/" + item + "/invoice").header("Authorization", finance)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(support.json(Map.of("customerEmail", "doc-amina@example.test",
                                "customerName", "Amina Ssemanda", "quantity", 3, "sourceType", "JOB"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "id");
    }

    @Test
    void theCustomerAndFinanceDownloadTheInvoicePdf() throws Exception {
        String text = text(mockMvc.perform(get("/me/invoices/" + invoiceId + "/pdf").header("Authorization", amina))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andReturn().getResponse().getContentAsByteArray());
        assertThat(text).contains("Invoice INV-", "Amina Ssemanda", "DOC Multimedia", "Studio recording x 3",
                "UGX 900,000", "[Bank details to be confirmed]", "as the payment reference");

        mockMvc.perform(get("/invoices/" + invoiceId + "/pdf").header("Authorization", finance))
                .andExpect(status().isOk());
        mockMvc.perform(get("/me/invoices/" + invoiceId + "/pdf").header("Authorization", other))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/invoices/" + invoiceId + "/pdf").header("Authorization", amina))
                .andExpect(status().isForbidden());
    }

    @Test
    void theReceiptPdfExistsOnlyAfterTheReceiptIsIssued() throws Exception {
        mockMvc.perform(get("/me/invoices/" + invoiceId + "/receipt/pdf").header("Authorization", amina))
                .andExpect(status().isNotFound());

        String uploaded = mockMvc.perform(multipart("/invoices/" + invoiceId + "/evidence")
                        .file(new MockMultipartFile("file", "proof.pdf", "application/pdf", "%PDF-1.4 proof".getBytes()))
                        .header("Authorization", amina)
                        .param("reference", "FT77").param("amount", "900000").param("paidOn", LocalDate.now().toString()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String proofId = objectMapper.readTree(uploaded).get("paymentProof").get("id").asString();
        mockMvc.perform(post("/invoices/" + invoiceId + "/verify").param("proofId", proofId).header("Authorization", finance))
                .andExpect(status().isOk());
        mockMvc.perform(post("/invoices/" + invoiceId + "/receipt").header("Authorization", finance))
                .andExpect(status().isOk());

        String text = text(mockMvc.perform(get("/me/invoices/" + invoiceId + "/receipt/pdf").header("Authorization", amina))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andReturn().getResponse().getContentAsByteArray());
        assertThat(text).contains("Receipt ", "Received from", "Amina Ssemanda", "UGX 900,000", "doc-finance@example.test");
    }

    private static String text(byte[] bytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            return new PDFTextStripper().getText(document);
        }
    }
}

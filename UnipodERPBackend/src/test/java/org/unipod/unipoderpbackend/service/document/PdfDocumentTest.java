package org.unipod.unipoderpbackend.service.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

class PdfDocumentTest {
    private final DocumentBranding branding = new DocumentBranding(
            "UniPod", "Plot 1 | Kampala", "info@example.test", "Bank X | Account 123", "Footer text");

    @Test
    void writesHeaderRowsAndFooter() throws IOException {
        byte[] bytes = new PdfDocument(branding, "Receipt RCT-2026-000001")
                .row("Amount", "UGX 300,000")
                .finish();

        String text = text(bytes);
        assertThat(new String(bytes, 0, 5)).isEqualTo("%PDF-");
        assertThat(text).contains("UniPod", "Plot 1", "Kampala", "Receipt RCT-2026-000001", "UGX 300,000", "Footer text");
    }

    @Test
    void charactersTheFontCannotPrintDoNotBreakTheDocument() throws IOException {
        byte[] bytes = new PdfDocument(branding, "Names")
                .row("Name", "Nsubuga ✓ 中文 é")
                .finish();
        assertThat(text(bytes)).contains("Nsubuga ? ?? é");
    }

    @Test
    void longTablesContinueOnNewPages() throws IOException {
        List<List<String>> rows = new ArrayList<>();
        for (int index = 0; index < 120; index++) {
            rows.add(List.of("Department " + index, String.valueOf(index)));
        }
        byte[] bytes = new PdfDocument(branding, "Workload").table(List.of("department", "count"), rows).finish();
        try (PDDocument document = Loader.loadPDF(bytes)) {
            assertThat(document.getNumberOfPages()).isGreaterThan(1);
        }
        assertThat(text(bytes)).contains("Department 119");
    }

    static String text(byte[] bytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            return new PDFTextStripper().getText(document);
        }
    }
}

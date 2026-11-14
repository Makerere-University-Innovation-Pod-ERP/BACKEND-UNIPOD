package org.unipod.unipoderpbackend.service.document;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/**
 * Builds simple A4 documents: a branded header, a title, label/value rows, tables and a
 * footer. Long tables continue on new pages. Characters the built-in font cannot print
 * are replaced with "?" instead of failing.
 */
public final class PdfDocument {
    private static final float MARGIN = 50;
    private static final float WIDTH = PDRectangle.A4.getWidth();
    private static final float HEIGHT = PDRectangle.A4.getHeight();
    private static final PDType1Font REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDType1Font BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

    private final PDDocument document = new PDDocument();
    private final DocumentBranding branding;
    private final String title;
    private PDPageContentStream content;
    private float y;

    public PdfDocument(DocumentBranding branding, String title) {
        this.branding = branding;
        this.title = title;
        PDDocumentInformation info = document.getDocumentInformation();
        info.setTitle(title);
        info.setCreator(branding.organisationName());
        newPage();
        writeLine(BOLD, 16, branding.organisationName());
        for (String line : branding.addressLines()) {
            writeLine(REGULAR, 9, line);
        }
        writeLine(REGULAR, 9, branding.contact());
        gap(14);
        writeLine(BOLD, 14, title);
        gap(6);
    }

    public PdfDocument row(String label, String value) {
        ensureSpace(16);
        text(BOLD, 10, MARGIN, y, label);
        text(REGULAR, 10, MARGIN + 170, y, value);
        y -= 16;
        return this;
    }

    public PdfDocument emphasis(String label, String value) {
        ensureSpace(22);
        y -= 4;
        text(BOLD, 12, MARGIN, y, label);
        text(BOLD, 12, MARGIN + 170, y, value);
        y -= 18;
        return this;
    }

    public PdfDocument heading(String text) {
        gap(8);
        ensureSpace(18);
        writeLine(BOLD, 11, text);
        return this;
    }

    public PdfDocument paragraph(String text) {
        writeLine(REGULAR, 10, text);
        return this;
    }

    public PdfDocument table(List<String> header, List<List<String>> rows) {
        float column = (WIDTH - 2 * MARGIN) / Math.max(1, header.size());
        tableRow(BOLD, header, column);
        for (List<String> row : rows) {
            tableRow(REGULAR, row, column);
        }
        return this;
    }

    public byte[] finish() {
        try {
            footer();
            content.close();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            document.close();
            return out.toByteArray();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private void tableRow(PDType1Font font, List<String> cells, float column) {
        ensureSpace(16);
        for (int index = 0; index < cells.size(); index++) {
            text(font, 9, MARGIN + index * column, y, fit(font, 9, cells.get(index), column - 6));
        }
        y -= 15;
    }

    private void writeLine(PDType1Font font, float size, String text) {
        ensureSpace(size + 6);
        text(font, size, MARGIN, y, text);
        y -= size + 5;
    }

    private void gap(float points) {
        y -= points;
    }

    private void ensureSpace(float needed) {
        if (y - needed < MARGIN + 30) {
            footer();
            try {
                content.close();
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
            newPage();
        }
    }

    private void newPage() {
        PDPage page = new PDPage(PDRectangle.A4);
        document.addPage(page);
        try {
            content = new PDPageContentStream(document, page);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        y = HEIGHT - MARGIN;
    }

    private void footer() {
        text(REGULAR, 8, MARGIN, MARGIN, branding.footer());
        text(REGULAR, 8, WIDTH - MARGIN - 60, MARGIN, "Page " + document.getNumberOfPages());
    }

    private void text(PDType1Font font, float size, float x, float atY, String value) {
        try {
            content.beginText();
            content.setFont(font, size);
            content.newLineAtOffset(x, atY);
            content.showText(printable(font, value));
            content.endText();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static String fit(PDType1Font font, float size, String value, float width) {
        String text = printable(font, value);
        if (width(font, size, text) <= width) {
            return text;
        }
        while (text.length() > 1 && width(font, size, text + "...") > width) {
            text = text.substring(0, text.length() - 1);
        }
        return text + "...";
    }

    private static float width(PDType1Font font, float size, String text) {
        try {
            return font.getStringWidth(text) / 1000 * size;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    static String printable(PDType1Font font, String value) {
        if (value == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(value.length());
        value.codePoints().forEach(codePoint -> {
            String character = new String(Character.toChars(Character.isWhitespace(codePoint) ? ' ' : codePoint));
            try {
                font.encode(character);
                out.append(character);
            } catch (IOException | IllegalArgumentException exception) {
                out.append('?');
            }
        });
        return out.toString();
    }
}

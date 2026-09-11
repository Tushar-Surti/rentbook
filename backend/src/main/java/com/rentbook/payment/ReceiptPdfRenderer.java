package com.rentbook.payment;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.FontSelector;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * A rent receipt as a landlord would write one, in the app's Carbon Copy hand. Labels are in graphite,
 * entries in carbon, and the violet stamp is earned only by a verified payment. It is set in Anek, the
 * app's face, embedded from static instances (fonts/, SIL OFL). The rupee sign comes from Anek Devanagari,
 * and Helvetica is the fallback for any character outside both, such as a name in another script.
 */
@Component
class ReceiptPdfRenderer {

    private static final Color GRAPHITE = new Color(0x1F, 0x23, 0x29);
    private static final Color SOFT = new Color(0x4A, 0x51, 0x5C);
    private static final Color CARBON = new Color(0x2A, 0x31, 0x90);
    private static final Color STAMP = new Color(0x6B, 0x3A, 0x9E);
    private static final Color RULE = new Color(0xC9, 0xCE, 0xD6);

    /** The app's three hands: running text, headings and totals, and the wide face for the amount and stamp. */
    private enum Hand { TEXT, STRONG, WIDE }

    record Line(String description, String amount) {
    }

    record ReceiptDocument(String number, String issuedOn, String tenant, String landlord, String landlordPan,
                           String home, String amount, String amountInWords, List<Line> lines,
                           String paymentReference, String confirmedAt) {
    }

    private final BaseFont text;
    private final BaseFont strong;
    private final BaseFont wide;
    private final BaseFont textExt;
    private final BaseFont strongExt;
    private final BaseFont wideExt;
    private final BaseFont rupee;
    private final BaseFont rupeeWide;

    ReceiptPdfRenderer() {
        text = embedded("AnekLatin-Regular.ttf");
        strong = embedded("AnekLatin-SemiBold.ttf");
        wide = embedded("AnekLatin-WideBold.ttf");
        textExt = embedded("AnekLatinExt-Regular.ttf");
        strongExt = embedded("AnekLatinExt-SemiBold.ttf");
        wideExt = embedded("AnekLatinExt-WideBold.ttf");
        rupee = embedded("AnekRupee-Regular.ttf");
        rupeeWide = embedded("AnekRupee-WideBold.ttf");
    }

    byte[] render(ReceiptDocument receipt) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document pdf = new Document(PageSize.A5, 42, 42, 48, 42);
        PdfWriter writer = PdfWriter.getInstance(pdf, out);
        pdf.addTitle("Rent receipt " + receipt.number());
        pdf.addCreator("Rentbook");
        pdf.open();

        pdf.add(paragraph(0, phrase("Rent receipt", Hand.STRONG, 19, GRAPHITE)));
        pdf.add(paragraph(14,
                phrase("No. ", Hand.TEXT, 9, SOFT), phrase(receipt.number(), Hand.TEXT, 11, CARBON),
                phrase("      Date ", Hand.TEXT, 9, SOFT), phrase(receipt.issuedOn(), Hand.TEXT, 11, CARBON)));

        Paragraph received = paragraph(10,
                phrase("Received from ", Hand.TEXT, 10.5f, GRAPHITE),
                phrase(receipt.tenant(), Hand.TEXT, 11, CARBON),
                phrase(" the sum of ", Hand.TEXT, 10.5f, GRAPHITE),
                phrase(receipt.amount(), Hand.WIDE, 13, CARBON),
                phrase(" (" + receipt.amountInWords() + ") towards:", Hand.TEXT, 10.5f, GRAPHITE));
        received.setLeading(18);
        pdf.add(received);

        PdfPTable table = new PdfPTable(new float[] {3.2f, 1.3f});
        table.setWidthPercentage(100);
        for (Line line : receipt.lines()) {
            table.addCell(cell(phrase(line.description(), Hand.TEXT, 10.5f, GRAPHITE), Element.ALIGN_LEFT));
            table.addCell(cell(phrase(line.amount(), Hand.TEXT, 11, CARBON), Element.ALIGN_RIGHT));
        }
        PdfPCell totalLabel = cell(phrase("Total", Hand.STRONG, 10.5f, GRAPHITE), Element.ALIGN_LEFT);
        PdfPCell totalAmount = cell(phrase(receipt.amount(), Hand.WIDE, 13, CARBON), Element.ALIGN_RIGHT);
        for (PdfPCell total : List.of(totalLabel, totalAmount)) {
            total.setBorder(Rectangle.TOP);
            total.setBorderWidthTop(1.5f);
            total.setBorderColorTop(GRAPHITE);
            table.addCell(total);
        }
        table.setSpacingAfter(14);
        pdf.add(table);

        pdf.add(field("For", receipt.home()));
        pdf.add(field("Landlord", receipt.landlord()));
        if (receipt.landlordPan() != null) {
            pdf.add(field("Landlord's PAN", receipt.landlordPan()));
        }
        Paragraph proof = paragraph(0, phrase("Received online through Razorpay (payment " + receipt.paymentReference()
                + "), confirmed by Razorpay's signed notification on " + receipt.confirmedAt() + ".", Hand.TEXT, 9, SOFT));
        proof.setSpacingBefore(16);
        pdf.add(proof);
        pdf.add(paragraph(0, phrase("Issued through Rentbook on behalf of the landlord.", Hand.TEXT, 9, SOFT)));

        // The stamp: violet ink in a double ring, set down crooked over the corner of the sheet.
        PdfContentByte canvas = writer.getDirectContent();
        float x = pdf.right() - 66;
        float y = pdf.top() - 20;
        double tilt = -0.14;
        canvas.saveState();
        canvas.setColorStroke(STAMP);
        canvas.concatCTM((float) Math.cos(tilt), (float) Math.sin(tilt), (float) -Math.sin(tilt), (float) Math.cos(tilt),
                x, y);
        canvas.setLineWidth(1.6f);
        canvas.roundRectangle(-44, -17, 88, 34, 3);
        canvas.stroke();
        canvas.setLineWidth(0.6f);
        canvas.roundRectangle(-40, -13, 80, 26, 2);
        canvas.stroke();
        ColumnText.showTextAligned(canvas, Element.ALIGN_CENTER, phrase("Paid", Hand.WIDE, 17, STAMP), 0, -6, 0);
        canvas.restoreState();

        pdf.close();
        return out.toByteArray();
    }

    /**
     * Text in one of the app's hands, falling back glyph by glyph: Anek Latin, then the same rupee sign
     * the app shows, then Anek's accented Latin, then Helvetica.
     */
    private Phrase phrase(String value, Hand hand, float size, Color color) {
        FontSelector selector = new FontSelector();
        selector.addFont(new Font(switch (hand) {
            case TEXT -> text;
            case STRONG -> strong;
            case WIDE -> wide;
        }, size, Font.NORMAL, color));
        selector.addFont(new Font(hand == Hand.WIDE ? rupeeWide : rupee, size, Font.NORMAL, color));
        selector.addFont(new Font(switch (hand) {
            case TEXT -> textExt;
            case STRONG -> strongExt;
            case WIDE -> wideExt;
        }, size, Font.NORMAL, color));
        selector.addFont(FontFactory.getFont(FontFactory.HELVETICA, size * 0.92f, color));
        return selector.process(value);
    }

    private static Paragraph paragraph(float spacingAfter, Phrase... parts) {
        Paragraph paragraph = new Paragraph();
        for (Phrase part : parts) {
            paragraph.add(part);
        }
        paragraph.setSpacingAfter(spacingAfter);
        return paragraph;
    }

    private Paragraph field(String name, String value) {
        Paragraph paragraph = paragraph(3, phrase(name, Hand.TEXT, 9, SOFT), new Phrase(new Chunk("  ")),
                phrase(value, Hand.TEXT, 11, CARBON));
        paragraph.setLeading(16);
        return paragraph;
    }

    private static PdfPCell cell(Phrase phrase, int alignment) {
        PdfPCell cell = new PdfPCell(phrase);
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderColorBottom(RULE);
        cell.setPaddingLeft(0);
        cell.setPaddingRight(0);
        cell.setPaddingTop(6);
        cell.setPaddingBottom(8);
        cell.setHorizontalAlignment(alignment);
        return cell;
    }

    private static BaseFont embedded(String file) {
        try (InputStream in = new ClassPathResource("fonts/" + file).getInputStream()) {
            return BaseFont.createFont(file, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, in.readAllBytes(), null);
        } catch (IOException e) {
            throw new UncheckedIOException("Receipt font " + file + " is missing from the build", e);
        }
    }
}

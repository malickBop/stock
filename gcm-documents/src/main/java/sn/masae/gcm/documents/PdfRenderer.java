package sn.masae.gcm.documents;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.util.List;

/** Rendu PDF (OpenPDF) fidèle à la mise en page des onglets du classeur : en-tête République / Modèle N°, titre, zones, tableau, cadres. */
public class PdfRenderer implements DocumentRenderer {

    static final Color VERT = new Color(0x1B, 0x5E, 0x20);
    static final Color VERT_CLAIR = new Color(0xE8, 0xF5, 0xE9);
    private static final Font F_NORMAL = FontFactory.getFont(FontFactory.HELVETICA, BaseFont.CP1252, true, 9);
    private static final Font F_GRAS = FontFactory.getFont(FontFactory.HELVETICA_BOLD, BaseFont.CP1252, true, 9);
    private static final Font F_TITRE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, BaseFont.CP1252, true, 14, Font.NORMAL, VERT);
    private static final Font F_ENTETE_TABLE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, BaseFont.CP1252, true, 8, Font.NORMAL, Color.WHITE);
    private static final Font F_PETIT = FontFactory.getFont(FontFactory.HELVETICA, BaseFont.CP1252, true, 8);
    private static final Font F_FILIGRANE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, BaseFont.CP1252, true, 60, Font.NORMAL, new Color(200, 230, 201));

    @Override
    public String contentType() {
        return "application/pdf";
    }

    @Override
    public String extension() {
        return "pdf";
    }

    @Override
    public byte[] rendre(DocumentModele d) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(d.paysage() ? PageSize.A4.rotate() : PageSize.A4, 36, 36, 36, 36);
        PdfWriter w = PdfWriter.getInstance(doc, out);
        if (d.filigrane() != null) {
            w.setPageEvent(new PdfPageEventHelper() {
                @Override
                public void onEndPage(PdfWriter writer, Document document) {
                    Rectangle p = document.getPageSize();
                    ColumnText.showTextAligned(writer.getDirectContentUnder(), Element.ALIGN_CENTER, new Phrase(d.filigrane(), F_FILIGRANE),
                            p.getWidth() / 2, p.getHeight() / 2, 35);
                }
            });
        }
        doc.addTitle("Modèle N° " + d.numero() + " — " + d.titre());
        doc.addCreator("GCM MASAE");
        doc.open();
        entete(doc, d);
        Paragraph t = new Paragraph(d.titre(), F_TITRE);
        t.setAlignment(Element.ALIGN_CENTER);
        t.setSpacingBefore(10);
        t.setSpacingAfter(4);
        doc.add(t);
        if (d.reference() != null) {
            Paragraph r = new Paragraph("N° " + d.reference(), F_GRAS);
            r.setAlignment(Element.ALIGN_CENTER);
            r.setSpacingAfter(8);
            doc.add(r);
        }
        champs(doc, d.champs());
        tableau(doc, d);
        for (String m : d.mentions()) {
            Paragraph p = new Paragraph(m, F_NORMAL);
            p.setSpacingBefore(6);
            p.setAlignment(Element.ALIGN_JUSTIFIED);
            doc.add(p);
        }
        cadres(doc, d.cadres());
        doc.close();
        return out.toByteArray();
    }

    private void entete(Document doc, DocumentModele d) {
        PdfPTable t = new PdfPTable(new float[]{3, 2});
        t.setWidthPercentage(100);
        t.addCell(sansBord(new Phrase("RÉPUBLIQUE DU SÉNÉGAL\nUn Peuple — Un But — Une Foi\n" + d.ministere()
                + (d.structure() == null ? "" : "\n" + d.structure()), F_GRAS), Element.ALIGN_LEFT));
        t.addCell(sansBord(new Phrase("Modèle N° " + d.numero() + "\nInstruction Générale n°0001/MFB/DGB\n" + nz(d.articles()), F_GRAS),
                Element.ALIGN_RIGHT));
        doc.add(t);
        LineSeparatorCompat.ajouter(doc);
    }

    private void champs(Document doc, List<DocumentModele.Champ> champs) {
        if (champs.isEmpty()) return;
        PdfPTable t = new PdfPTable(new float[]{2, 3, 2, 3});
        t.setWidthPercentage(100);
        t.setSpacingAfter(8);
        for (DocumentModele.Champ c : champs) {
            PdfPCell l = sansBord(new Phrase(c.libelle() + " :", F_GRAS), Element.ALIGN_LEFT);
            PdfPCell v = sansBord(new Phrase(nz(c.valeur()).isEmpty() ? "........................................" : c.valeur(), F_NORMAL),
                    Element.ALIGN_LEFT);
            if (c.libelle().length() > 40 || nz(c.valeur()).length() > 40) {
                l.setColspan(1);
                v.setColspan(3);
            }
            t.addCell(l);
            t.addCell(v);
        }
        t.completeRow();
        doc.add(t);
    }

    private void tableau(Document doc, DocumentModele d) {
        if (d.colonnes() == null || d.colonnes().isEmpty()) return;
        PdfPTable t = new PdfPTable(d.colonnes().size());
        t.setWidthPercentage(100);
        t.setHeaderRows(1);
        t.setSpacingAfter(8);
        for (String c : d.colonnes()) {
            PdfPCell h = new PdfPCell(new Phrase(c, F_ENTETE_TABLE));
            h.setBackgroundColor(VERT);
            h.setHorizontalAlignment(Element.ALIGN_CENTER);
            h.setVerticalAlignment(Element.ALIGN_MIDDLE);
            h.setPadding(4);
            t.addCell(h);
        }
        List<List<String>> lignes = d.lignes().isEmpty() ? List.of(List.of()) : d.lignes();
        for (List<String> l : lignes) {
            for (int i = 0; i < d.colonnes().size(); i++) {
                String v = i < l.size() ? nz(l.get(i)) : "";
                PdfPCell c = new PdfPCell(new Phrase(v, F_PETIT));
                c.setPadding(3);
                c.setMinimumHeight(14);
                c.setHorizontalAlignment(numerique(v) ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
                t.addCell(c);
            }
        }
        if (d.totaux() != null) {
            for (int i = 0; i < d.colonnes().size(); i++) {
                String v = i < d.totaux().size() ? nz(d.totaux().get(i)) : "";
                PdfPCell c = new PdfPCell(new Phrase(v, F_GRAS));
                c.setBackgroundColor(VERT_CLAIR);
                c.setPadding(3);
                c.setHorizontalAlignment(numerique(v) ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
                t.addCell(c);
            }
        }
        doc.add(t);
    }

    private void cadres(Document doc, List<DocumentModele.Cadre> cadres) {
        if (cadres.isEmpty()) return;
        int n = Math.min(cadres.size(), 4);
        PdfPTable t = new PdfPTable(n);
        t.setWidthPercentage(100);
        t.setSpacingBefore(14);
        t.setKeepTogether(true);
        for (DocumentModele.Cadre c : cadres) {
            PdfPCell cell = new PdfPCell();
            cell.setBorderColor(VERT);
            cell.setPadding(5);
            cell.setMinimumHeight(80);
            cell.addElement(new Paragraph(c.qualite(), F_GRAS));
            cell.addElement(new Paragraph(nz(c.nom()), F_NORMAL));
            cell.addElement(new Paragraph(nz(c.mention()).isEmpty() ? "\n\nDate et signature :" : c.mention(), F_PETIT));
            t.addCell(cell);
        }
        t.completeRow();
        doc.add(t);
    }

    private static PdfPCell sansBord(Phrase p, int align) {
        PdfPCell c = new PdfPCell(p);
        c.setBorder(Rectangle.NO_BORDER);
        c.setHorizontalAlignment(align);
        c.setPadding(2);
        return c;
    }

    static boolean numerique(String v) {
        return !v.isEmpty() && v.matches("[-−]?[\\d  .,]+");
    }

    static String nz(String s) {
        return s == null ? "" : s;
    }

    /** Filet vert sous l'en-tête. */
    private static final class LineSeparatorCompat {
        static void ajouter(Document doc) {
            PdfPTable l = new PdfPTable(1);
            l.setWidthPercentage(100);
            PdfPCell c = new PdfPCell();
            c.setBorder(Rectangle.BOTTOM);
            c.setBorderColorBottom(VERT);
            c.setBorderWidthBottom(1.5f);
            c.setFixedHeight(4);
            l.addCell(c);
            doc.add(l);
        }
    }
}

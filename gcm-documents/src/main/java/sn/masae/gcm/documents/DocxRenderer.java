package sn.masae.gcm.documents;

import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.util.List;

/** Rendu DOCX (Apache POI) éditable, même structure que le rendu PDF. */
public class DocxRenderer implements DocumentRenderer {

    private static final String VERT = "1B5E20";
    private static final String VERT_CLAIR = "E8F5E9";

    @Override
    public String contentType() {
        return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    }

    @Override
    public String extension() {
        return "docx";
    }

    @Override
    public byte[] rendre(DocumentModele d) {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CTSectPr sect = doc.getDocument().getBody().addNewSectPr();
            CTPageSz sz = sect.addNewPgSz();
            if (d.paysage()) {
                sz.setOrient(STPageOrientation.LANDSCAPE);
                sz.setW(BigInteger.valueOf(16838));
                sz.setH(BigInteger.valueOf(11906));
            } else {
                sz.setW(BigInteger.valueOf(11906));
                sz.setH(BigInteger.valueOf(16838));
            }
            CTPageMar mar = sect.addNewPgMar();
            for (var setter : List.<java.util.function.Consumer<BigInteger>>of(mar::setTop, mar::setBottom, mar::setLeft, mar::setRight)) {
                setter.accept(BigInteger.valueOf(720));
            }
            entete(doc, d);
            XWPFParagraph t = doc.createParagraph();
            t.setAlignment(ParagraphAlignment.CENTER);
            texte(t, d.titre(), true, 14, VERT);
            if (d.filigrane() != null) texte(doc.createParagraph(), "[" + d.filigrane() + "]", true, 9, "9E9E9E");
            if (d.reference() != null) {
                XWPFParagraph r = doc.createParagraph();
                r.setAlignment(ParagraphAlignment.CENTER);
                texte(r, "N° " + d.reference(), true, 10, null);
            }
            for (DocumentModele.Champ c : d.champs()) {
                XWPFParagraph p = doc.createParagraph();
                p.setSpacingAfter(40);
                texte(p, c.libelle() + " : ", true, 10, null);
                texte(p, PdfRenderer.nz(c.valeur()).isEmpty() ? "........................................" : c.valeur(), false, 10, null);
            }
            tableau(doc, d);
            for (String m : d.mentions()) {
                XWPFParagraph p = doc.createParagraph();
                p.setAlignment(ParagraphAlignment.BOTH);
                texte(p, m, false, 10, null);
            }
            cadres(doc, d.cadres());
            doc.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void entete(XWPFDocument doc, DocumentModele d) {
        XWPFTable t = doc.createTable(1, 2);
        sansBordure(t);
        t.setWidth("100%");
        XWPFParagraph g = t.getRow(0).getCell(0).getParagraphs().get(0);
        texte(g, "RÉPUBLIQUE DU SÉNÉGAL", true, 10, null);
        ligne(t.getRow(0).getCell(0), "Un Peuple — Un But — Une Foi", false);
        ligne(t.getRow(0).getCell(0), d.ministere(), true);
        if (d.structure() != null) ligne(t.getRow(0).getCell(0), d.structure(), false);
        XWPFParagraph dr = t.getRow(0).getCell(1).getParagraphs().get(0);
        dr.setAlignment(ParagraphAlignment.RIGHT);
        texte(dr, "Modèle N° " + d.numero(), true, 10, VERT);
        ligne(t.getRow(0).getCell(1), "Instruction Générale n°0001/MFB/DGB", false).setAlignment(ParagraphAlignment.RIGHT);
        ligne(t.getRow(0).getCell(1), PdfRenderer.nz(d.articles()), false).setAlignment(ParagraphAlignment.RIGHT);
    }

    private void tableau(XWPFDocument doc, DocumentModele d) {
        if (d.colonnes() == null || d.colonnes().isEmpty()) return;
        List<List<String>> lignes = d.lignes().isEmpty() ? List.of(List.of()) : d.lignes();
        int n = d.colonnes().size();
        XWPFTable t = doc.createTable(1 + lignes.size() + (d.totaux() == null ? 0 : 1), n);
        t.setWidth("100%");
        for (int i = 0; i < n; i++) {
            XWPFTableCell c = t.getRow(0).getCell(i);
            c.setColor(VERT);
            texte(c.getParagraphs().get(0), d.colonnes().get(i), true, 8, "FFFFFF");
        }
        t.getRow(0).setRepeatHeader(true);
        for (int r = 0; r < lignes.size(); r++) {
            for (int i = 0; i < n; i++) {
                String v = i < lignes.get(r).size() ? PdfRenderer.nz(lignes.get(r).get(i)) : "";
                XWPFParagraph p = t.getRow(r + 1).getCell(i).getParagraphs().get(0);
                if (PdfRenderer.numerique(v)) p.setAlignment(ParagraphAlignment.RIGHT);
                texte(p, v, false, 8, null);
            }
        }
        if (d.totaux() != null) {
            XWPFTableRow row = t.getRow(lignes.size() + 1);
            for (int i = 0; i < n; i++) {
                String v = i < d.totaux().size() ? PdfRenderer.nz(d.totaux().get(i)) : "";
                row.getCell(i).setColor(VERT_CLAIR);
                texte(row.getCell(i).getParagraphs().get(0), v, true, 8, null);
            }
        }
        doc.createParagraph();
    }

    private void cadres(XWPFDocument doc, List<DocumentModele.Cadre> cadres) {
        if (cadres.isEmpty()) return;
        doc.createParagraph();
        XWPFTable t = doc.createTable(1, Math.min(cadres.size(), 4));
        t.setWidth("100%");
        for (int i = 0; i < cadres.size(); i++) {
            if (i >= 4 && i % 4 == 0) t.createRow();
            XWPFTableCell c = t.getRow(i / 4).getCell(i % 4);
            if (c == null) c = t.getRow(i / 4).addNewTableCell();
            texte(c.getParagraphs().get(0), cadres.get(i).qualite(), true, 9, VERT);
            ligne(c, PdfRenderer.nz(cadres.get(i).nom()), false);
            ligne(c, PdfRenderer.nz(cadres.get(i).mention()).isEmpty() ? "Date et signature :" : cadres.get(i).mention(), false);
            ligne(c, " ", false);
            ligne(c, " ", false);
        }
    }

    private static XWPFParagraph ligne(XWPFTableCell c, String s, boolean gras) {
        XWPFParagraph p = c.addParagraph();
        texte(p, s, gras, 9, null);
        return p;
    }

    private static void texte(XWPFParagraph p, String s, boolean gras, int taille, String couleur) {
        XWPFRun r = p.createRun();
        r.setFontFamily("Arial");
        r.setBold(gras);
        r.setFontSize(taille);
        if (couleur != null) r.setColor(couleur);
        String[] lignes = s.split("\n", -1);
        for (int i = 0; i < lignes.length; i++) {
            if (i > 0) r.addBreak();
            r.setText(lignes[i], i == 0 ? 0 : -1);
        }
    }

    private static void sansBordure(XWPFTable t) {
        t.setTopBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "FFFFFF");
        t.setBottomBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "FFFFFF");
        t.setLeftBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "FFFFFF");
        t.setRightBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "FFFFFF");
        t.setInsideHBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "FFFFFF");
        t.setInsideVBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "FFFFFF");
    }
}

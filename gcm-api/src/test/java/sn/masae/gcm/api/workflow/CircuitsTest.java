package sn.masae.gcm.api.workflow;

import org.junit.jupiter.api.Test;
import sn.masae.gcm.documents.DocumentModele;
import sn.masae.gcm.documents.DocxRenderer;
import sn.masae.gcm.documents.PdfRenderer;
import sn.masae.gcm.documents.SpecModele;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class CircuitsTest {

    @Test
    void lesVingtSixModelesSontDecrits() {
        assertThat(SpecModele.tous()).hasSize(26);
        IntStream.rangeClosed(1, 26).forEach(n -> {
            assertThat(ModelesReglementaires.titre(n)).isNotBlank();
            assertThat(Circuits.pour(n)).isNotNull();
        });
    }

    @Test
    void pvDeReceptionExigeVisaCbmOuCrf() {
        Circuit c = Circuits.pour(1);
        assertThat(c.avecCircuit()).isTrue();
        assertThat(c.visasExternes()).isNotEmpty();
    }

    @Test
    void chaqueModeleViergeSeGenereEnPdfEtDocx() {
        PdfRenderer pdf = new PdfRenderer();
        DocxRenderer docx = new DocxRenderer();
        for (int n = 1; n <= 26; n++) {
            SpecModele sp = SpecModele.de(n);
            var m = new DocumentModele(n, sp.titre(), sp.articles(), "MASAE", null, null,
                    sp.champs().stream().map(c -> new DocumentModele.Champ(c, null)).toList(), sp.colonnes(), List.of(), null, sp.mentions(),
                    List.of(new DocumentModele.Cadre("Le Comptable des matières", null, "Date et signature :")), "BROUILLON");
            byte[] p = pdf.rendre(m);
            assertThat(new String(p, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
            byte[] d = docx.rendre(m);
            assertThat(d[0]).isEqualTo((byte) 'P');
            assertThat(d[1]).isEqualTo((byte) 'K');
        }
    }
}

package sn.masae.gcm.api.documents;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExportServiceTest {

    @Test
    void csvEchappeEtNeutraliseLesFormules() {
        String csv = new String(ExportService.csv(List.of("A", "B"), List.of(List.of("=SOMME(A1)", "x;y"), List.of(new BigDecimal("-12.50"), "ok"))),
                StandardCharsets.UTF_8);
        assertThat(csv).startsWith("\uFEFFA;B\r\n").contains("'=SOMME(A1);\"x;y\"").contains("-12.50;ok");
    }

    @Test
    void xlsxContientEntetesEtValeurs() throws Exception {
        byte[] b = ExportService.xlsx("balance", List.of("Compte", "Valeur"), List.of(List.of("1-21", new BigDecimal("2250000"))));
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(b))) {
            assertThat(wb.getSheetAt(0).getRow(0).getCell(0).getStringCellValue()).isEqualTo("Compte");
            assertThat(wb.getSheetAt(0).getRow(1).getCell(1).getNumericCellValue()).isEqualTo(2_250_000d);
        }
    }
}

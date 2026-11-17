package sn.masae.gcm.api.documents;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import sn.masae.gcm.api.documents.GenerationService.Fichier;
import sn.masae.gcm.common.exception.BusinessException;
import sn.masae.gcm.common.exception.ErrorCode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/** Exports tabulaires CSV (séparateur « ; », UTF-8 avec BOM pour Excel) et XLSX. */
@Service
public class ExportService {

    public Fichier exporter(String nom, List<String> entetes, List<List<Object>> lignes, String format) {
        return switch (format == null ? "csv" : format.toLowerCase(Locale.ROOT)) {
            case "csv" -> new Fichier(nom + ".csv", "text/csv;charset=UTF-8", csv(entetes, lignes));
            case "xlsx" -> new Fichier(nom + ".xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", xlsx(nom, entetes, lignes));
            default -> throw new BusinessException(ErrorCode.VALIDATION, "Format d'export non supporté : " + format + " (csv ou xlsx)");
        };
    }

    static byte[] csv(List<String> entetes, List<List<Object>> lignes) {
        StringBuilder sb = new StringBuilder("\uFEFF");
        sb.append(String.join(";", entetes.stream().map(ExportService::echapper).toList())).append("\r\n");
        for (List<Object> l : lignes) {
            sb.append(String.join(";", l.stream().map(v -> echapper(v == null ? "" : v instanceof BigDecimal b ? b.toPlainString() : v.toString())).toList()))
                    .append("\r\n");
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    static String echapper(String v) {
        // Neutralise l'injection de formules à l'ouverture dans un tableur.
        String s = !v.isEmpty() && "=+-@".indexOf(v.charAt(0)) >= 0 && !v.matches("-?\\d+(\\.\\d+)?") ? "'" + v : v;
        return s.contains(";") || s.contains("\"") || s.contains("\n") ? "\"" + s.replace("\"", "\"\"") + "\"" : s;
    }

    static byte[] xlsx(String nom, List<String> entetes, List<List<Object>> lignes) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sh = wb.createSheet(nom.length() > 31 ? nom.substring(0, 31) : nom);
            CellStyle h = wb.createCellStyle();
            Font f = wb.createFont();
            f.setBold(true);
            f.setColor(IndexedColors.WHITE.getIndex());
            h.setFont(f);
            h.setFillForegroundColor(IndexedColors.GREEN.getIndex());
            h.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            CellStyle dateStyle = wb.createCellStyle();
            dateStyle.setDataFormat(wb.getCreationHelper().createDataFormat().getFormat("dd/mm/yyyy"));
            Row r0 = sh.createRow(0);
            for (int i = 0; i < entetes.size(); i++) {
                Cell c = r0.createCell(i);
                c.setCellValue(entetes.get(i));
                c.setCellStyle(h);
            }
            int r = 1;
            for (List<Object> l : lignes) {
                Row row = sh.createRow(r++);
                for (int i = 0; i < l.size(); i++) {
                    Object v = l.get(i);
                    Cell c = row.createCell(i);
                    if (v instanceof Number n) c.setCellValue(n.doubleValue());
                    else if (v instanceof LocalDate d) {
                        c.setCellValue(d);
                        c.setCellStyle(dateStyle);
                    } else if (v != null) c.setCellValue(v.toString());
                }
            }
            sh.createFreezePane(0, 1);
            sh.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, Math.max(0, r - 1), 0, entetes.size() - 1));
            for (int i = 0; i < entetes.size(); i++) sh.setColumnWidth(i, 20 * 256);
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

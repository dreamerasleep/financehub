package com.financehub.infrastructure.parser;

import com.financehub.domain.imports.ImportFormat;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class XlsxTransactionFileParserTest {

    private final XlsxTransactionFileParser parser = new XlsxTransactionFileParser();

    @Test
    void supportsXlsx() {
        assertThat(parser.supports()).isEqualTo(ImportFormat.XLSX);
    }

    @Test
    void parsesDateAndNumericCellsAsStrings() throws Exception {
        byte[] xlsx = buildWorkbook();
        List<RawRow> rows = parser.parse(new ByteArrayInputStream(xlsx));
        assertThat(rows).hasSize(2);

        RawRow first = rows.get(0);
        assertThat(first.fields().get("date")).isEqualTo("2026-06-01");
        assertThat(first.fields().get("type")).isEqualTo("INCOME");
        assertThat(first.fields().get("account")).isEqualTo("主帳戶");
        assertThat(first.fields().get("amount")).isEqualTo("30000.00");
        assertThat(first.fields().get("category")).isEqualTo("薪資");
        assertThat(first.fields().get("to_account")).isEmpty();
        assertThat(first.fields().get("note")).isEqualTo("六月薪水");

        RawRow second = rows.get(1);
        assertThat(second.fields().get("type")).isEqualTo("TRANSFER");
        assertThat(second.fields().get("to_account")).isEqualTo("副帳戶");
        assertThat(second.fields().get("category")).isEmpty();
    }

    @Test
    void rejectsMissingRequiredColumns() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet();
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("date");
            header.createCell(1).setCellValue("amount");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);

            org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> parser.parse(new ByteArrayInputStream(out.toByteArray())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Missing required column");
        }
    }

    private byte[] buildWorkbook() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet();
            CreationHelper helper = wb.getCreationHelper();
            CellStyle dateStyle = wb.createCellStyle();
            dateStyle.setDataFormat(helper.createDataFormat().getFormat("yyyy-mm-dd"));

            String[] headers = {"date", "type", "account", "amount", "category", "to_account", "note"};
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }

            Row r1 = sheet.createRow(1);
            org.apache.poi.ss.usermodel.Cell d1 = r1.createCell(0);
            d1.setCellValue(java.sql.Date.valueOf(LocalDate.of(2026, 6, 1)));
            d1.setCellStyle(dateStyle);
            r1.createCell(1).setCellValue("INCOME");
            r1.createCell(2).setCellValue("主帳戶");
            r1.createCell(3).setCellValue(30000.0);
            r1.createCell(4).setCellValue("薪資");
            r1.createCell(5).setCellValue("");
            r1.createCell(6).setCellValue("六月薪水");

            Row r2 = sheet.createRow(2);
            org.apache.poi.ss.usermodel.Cell d2 = r2.createCell(0);
            d2.setCellValue(java.sql.Date.valueOf(LocalDate.of(2026, 6, 2)));
            d2.setCellStyle(dateStyle);
            r2.createCell(1).setCellValue("TRANSFER");
            r2.createCell(2).setCellValue("主帳戶");
            r2.createCell(3).setCellValue(5000.0);
            r2.createCell(4).setCellValue("");
            r2.createCell(5).setCellValue("副帳戶");
            r2.createCell(6).setCellValue("");

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }
}

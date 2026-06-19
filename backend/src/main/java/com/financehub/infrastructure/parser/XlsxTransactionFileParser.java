package com.financehub.infrastructure.parser;

import com.financehub.domain.imports.ImportFormat;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class XlsxTransactionFileParser implements TransactionFileParser {

    private static final Set<String> REQUIRED = Set.of("date", "type", "account", "amount");
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    @Override
    public ImportFormat supports() {
        return ImportFormat.XLSX;
    }

    @Override
    public List<RawRow> parse(InputStream in) throws IOException {
        try (Workbook wb = new XSSFWorkbook(in)) {
            Sheet sheet = wb.getSheetAt(0);
            if (sheet == null) {
                throw new IllegalArgumentException("Empty XLSX file");
            }
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new IllegalArgumentException("Empty XLSX file");
            }

            Map<String, Integer> headerIndex = new HashMap<>();
            for (int c = 0; c < headerRow.getLastCellNum(); c++) {
                Cell cell = headerRow.getCell(c);
                if (cell == null) continue;
                String name = cell.getStringCellValue().trim().toLowerCase();
                if (!name.isEmpty()) {
                    headerIndex.put(name, c);
                }
            }
            for (String required : REQUIRED) {
                if (!headerIndex.containsKey(required)) {
                    throw new IllegalArgumentException("Missing required column: " + required);
                }
            }

            List<RawRow> rows = new ArrayList<>();
            int rowIndex = 0;
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null || isBlankRow(row, headerIndex)) continue;
                rowIndex++;
                Map<String, String> fields = new LinkedHashMap<>();
                for (Map.Entry<String, Integer> entry : headerIndex.entrySet()) {
                    String value = cellToString(row.getCell(entry.getValue()));
                    fields.put(entry.getKey(), value);
                }
                rows.add(new RawRow(rowIndex, fields));
            }
            return rows;
        }
    }

    private boolean isBlankRow(Row row, Map<String, Integer> headerIndex) {
        for (Integer col : headerIndex.values()) {
            Cell cell = row.getCell(col);
            if (cell != null && cell.getCellType() != CellType.BLANK
                    && !cellToString(cell).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private String cellToString(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    LocalDate d = cell.getLocalDateTimeCellValue().toLocalDate();
                    yield d.format(ISO_DATE);
                }
                BigDecimal bd = BigDecimal.valueOf(cell.getNumericCellValue());
                yield bd.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield String.valueOf(cell.getNumericCellValue());
                } catch (IllegalStateException ex) {
                    yield cell.getStringCellValue();
                }
            }
            default -> "";
        };
    }
}

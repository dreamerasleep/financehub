package com.financehub.infrastructure.parser;

import com.financehub.domain.imports.ImportFormat;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CsvTransactionFileParserTest {

    private final CsvTransactionFileParser parser = new CsvTransactionFileParser();

    @Test
    void supportsCsv() {
        assertThat(parser.supports()).isEqualTo(ImportFormat.CSV);
    }

    @Test
    void parsesStandardCsvIncludingBomAndMixedLineEndings() throws Exception {
        InputStream in = getClass().getResourceAsStream("/imports/sample-good.csv");
        List<RawRow> rows = parser.parse(in);
        assertThat(rows).hasSize(6);
        RawRow first = rows.get(0);
        assertThat(first.rowIndex()).isEqualTo(1);
        assertThat(first.fields()).containsEntry("date", "2026-06-01")
                .containsEntry("type", "INCOME")
                .containsEntry("account", "主帳戶")
                .containsEntry("amount", "30000.00")
                .containsEntry("category", "薪資")
                .containsEntry("note", "六月薪水");
        assertThat(first.fields().get("to_account")).isEmpty();
    }

    @Test
    void acceptsCrlfAndLf() throws Exception {
        String csv = "date,type,account,amount,category,to_account,note\r\n"
                + "2026-06-01,INCOME,A,100,薪資,,x\r\n"
                + "2026-06-02,EXPENSE,A,50,飲食,,y\n";
        List<RawRow> rows = parser.parse(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));
        assertThat(rows).hasSize(2);
        assertThat(rows.get(1).fields().get("type")).isEqualTo("EXPENSE");
    }

    @Test
    void rejectsEmptyFile() {
        assertThatThrownBy(() -> parser.parse(new ByteArrayInputStream(new byte[0])))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Empty");
    }

    @Test
    void rejectsHeaderOnlyFileAsZeroRows() throws Exception {
        InputStream in = getClass().getResourceAsStream("/imports/empty-only-header.csv");
        List<RawRow> rows = parser.parse(in);
        assertThat(rows).isEmpty();
    }

    @Test
    void rejectsMissingRequiredColumns() {
        String csv = "date,amount\n2026-06-01,100\n";
        assertThatThrownBy(() -> parser.parse(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Missing required column");
    }
}

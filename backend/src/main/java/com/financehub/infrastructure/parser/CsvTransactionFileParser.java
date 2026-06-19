package com.financehub.infrastructure.parser;

import com.financehub.domain.imports.ImportFormat;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class CsvTransactionFileParser implements TransactionFileParser {

    private static final Set<String> REQUIRED = Set.of("date", "type", "account", "amount");

    @Override
    public ImportFormat supports() {
        return ImportFormat.CSV;
    }

    @Override
    public List<RawRow> parse(InputStream in) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        reader.mark(1);
        int first = reader.read();
        if (first == -1) {
            throw new IllegalArgumentException("Empty CSV file");
        }
        if (first != 0xFEFF) {
            reader.reset();
        }

        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .build();

        try (CSVParser csvParser = new CSVParser(reader, format)) {
            Map<String, Integer> headerMap = csvParser.getHeaderMap();
            if (headerMap == null || headerMap.isEmpty()) {
                throw new IllegalArgumentException("Empty CSV file");
            }
            Map<String, String> normalizedHeader = new HashMap<>();
            for (String h : headerMap.keySet()) {
                normalizedHeader.put(h.toLowerCase().trim(), h);
            }
            for (String required : REQUIRED) {
                if (!normalizedHeader.containsKey(required)) {
                    throw new IllegalArgumentException("Missing required column: " + required);
                }
            }

            List<RawRow> rows = new ArrayList<>();
            int rowIndex = 0;
            for (CSVRecord record : csvParser) {
                rowIndex++;
                Map<String, String> fields = new LinkedHashMap<>();
                for (Map.Entry<String, String> entry : normalizedHeader.entrySet()) {
                    String value = record.get(entry.getValue());
                    fields.put(entry.getKey(), value == null ? "" : value);
                }
                rows.add(new RawRow(rowIndex, fields));
            }
            return rows;
        }
    }
}

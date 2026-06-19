package com.financehub.infrastructure.parser;

import java.util.Map;

public record RawRow(int rowIndex, Map<String, String> fields) {
}

package com.financehub.infrastructure.parser;

import com.financehub.domain.imports.ImportFormat;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public interface TransactionFileParser {

    ImportFormat supports();

    List<RawRow> parse(InputStream in) throws IOException;
}

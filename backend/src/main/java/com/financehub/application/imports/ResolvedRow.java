package com.financehub.application.imports;

import com.financehub.domain.imports.ImportJobRowStatus;
import com.financehub.domain.transaction.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ResolvedRow(
        int rowIndex,
        ImportJobRowStatus status,
        String errorMessage,
        TransactionType type,
        BigDecimal amount,
        LocalDate date,
        Long accountId,
        Long toAccountId,
        Long categoryId,
        String note,
        String dedupHash
) {
}

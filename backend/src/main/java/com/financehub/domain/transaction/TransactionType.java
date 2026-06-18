package com.financehub.domain.transaction;

import java.math.BigDecimal;

public enum TransactionType {
    INCOME,
    EXPENSE,
    TRANSFER;

    /**
     * Signed delta applied to the source (from) account.
     * EXPENSE / TRANSFER reduce the source; INCOME increases it.
     */
    public BigDecimal signedAmount(BigDecimal amount) {
        return this == INCOME ? amount : amount.negate();
    }

    public boolean isTransfer() {
        return this == TRANSFER;
    }
}

package com.financehub.domain.transaction;

import java.math.BigDecimal;

public enum TransactionType {
    INCOME,
    EXPENSE;

    public BigDecimal signedAmount(BigDecimal amount) {
        return this == EXPENSE ? amount.negate() : amount;
    }
}

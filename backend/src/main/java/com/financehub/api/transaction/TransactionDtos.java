package com.financehub.api.transaction;

import com.financehub.domain.transaction.Transaction;
import com.financehub.domain.transaction.TransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public final class TransactionDtos {

    private TransactionDtos() {
    }

    public record CreateTransactionRequest(
            @NotNull Long accountId,
            @NotNull Long categoryId,
            @NotNull TransactionType type,
            @NotNull @DecimalMin(value = "0.01", message = "must be positive") BigDecimal amount,
            @NotNull LocalDate txnDate,
            @Size(max = 255) String note
    ) {
    }

    public record UpdateTransactionRequest(
            @NotNull Long accountId,
            @NotNull Long categoryId,
            @NotNull TransactionType type,
            @NotNull @DecimalMin(value = "0.01", message = "must be positive") BigDecimal amount,
            @NotNull LocalDate txnDate,
            @Size(max = 255) String note
    ) {
    }

    public record TransactionResponse(
            Long id,
            Long accountId,
            Long categoryId,
            TransactionType type,
            BigDecimal amount,
            LocalDate txnDate,
            String note,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
        public static TransactionResponse from(Transaction t) {
            return new TransactionResponse(t.getId(), t.getAccountId(), t.getCategoryId(),
                    t.getType(), t.getAmount(), t.getTxnDate(), t.getNote(),
                    t.getCreatedAt(), t.getUpdatedAt());
        }
    }
}

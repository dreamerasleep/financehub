package com.financehub.api.account;

import com.financehub.domain.account.Account;
import com.financehub.domain.account.AccountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public final class AccountDtos {

    private AccountDtos() {
    }

    public record CreateAccountRequest(
            @NotBlank @Size(max = 100) String name,
            @NotNull AccountType type,
            @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
            BigDecimal initialBalance
    ) {
    }

    public record UpdateAccountRequest(
            @NotBlank @Size(max = 100) String name,
            @NotNull AccountType type,
            @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
            BigDecimal currentBalance
    ) {
    }

    public record AccountResponse(
            Long id,
            String name,
            AccountType type,
            String currency,
            BigDecimal currentBalance,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
        public static AccountResponse from(Account a) {
            return new AccountResponse(a.getId(), a.getName(), a.getType(), a.getCurrency(),
                    a.getCurrentBalance(), a.getCreatedAt(), a.getUpdatedAt());
        }
    }
}

package com.financehub.application.imports;

import com.financehub.domain.imports.ImportJobRowStatus;
import com.financehub.domain.transaction.TransactionType;
import com.financehub.infrastructure.parser.RawRow;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Set;

@Component
public class RowResolver {

    public ResolvedRow resolve(Long userId,
                               RawRow row,
                               Map<String, Long> accountsByNameLower,
                               Map<Long, String> currencyByAccountId,
                               Map<String, Long> categoriesByKeyLower,
                               Set<String> existingDbHashes,
                               Set<String> batchHashesSoFar) {

        Map<String, String> f = row.fields();

        LocalDate date;
        try {
            String raw = f.get("date");
            if (raw == null || raw.isBlank()) {
                return err(row, "Date is required");
            }
            date = LocalDate.parse(raw.trim());
        } catch (DateTimeParseException ex) {
            return err(row, "Invalid date format: " + f.get("date"));
        }

        TransactionType type;
        try {
            String raw = f.getOrDefault("type", "").trim().toUpperCase();
            if (raw.isEmpty()) return err(row, "Type is required");
            type = TransactionType.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            return err(row, "Unknown type: " + f.get("type"));
        }

        BigDecimal amount;
        try {
            String raw = f.getOrDefault("amount", "").replace(",", "").trim();
            if (raw.isEmpty()) return err(row, "Amount is required");
            amount = new BigDecimal(raw);
            if (amount.signum() <= 0) {
                return err(row, "Amount must be positive number");
            }
            amount = amount.setScale(2, java.math.RoundingMode.HALF_UP);
        } catch (NumberFormatException ex) {
            return err(row, "Amount must be positive number");
        }

        String accountName = f.getOrDefault("account", "").trim();
        if (accountName.isEmpty()) return err(row, "Account is required");
        Long accountId = accountsByNameLower.get(accountName.toLowerCase());
        if (accountId == null) {
            return err(row, "Account not found: " + accountName);
        }

        String note = trimToMax(f.get("note"), 255);
        String toAccountRaw = f.getOrDefault("to_account", "").trim();
        String categoryRaw = f.getOrDefault("category", "").trim();

        Long toAccountId = null;
        Long categoryId = null;

        if (type.isTransfer()) {
            if (!categoryRaw.isEmpty()) {
                return err(row, "to_account only allowed for TRANSFER");
            }
            if (toAccountRaw.isEmpty()) {
                return err(row, "Transfer requires to_account");
            }
            toAccountId = accountsByNameLower.get(toAccountRaw.toLowerCase());
            if (toAccountId == null) {
                return err(row, "Account not found: " + toAccountRaw);
            }
            if (toAccountId.equals(accountId)) {
                return err(row, "Transfer source and destination must differ");
            }
            String fromCurrency = currencyByAccountId.get(accountId);
            String toCurrency = currencyByAccountId.get(toAccountId);
            if (fromCurrency != null && toCurrency != null && !fromCurrency.equals(toCurrency)) {
                return err(row, "Cross-currency transfer not supported");
            }
        } else {
            if (!toAccountRaw.isEmpty()) {
                return err(row, "to_account only allowed for TRANSFER");
            }
            if (categoryRaw.isEmpty()) {
                return err(row, "Income/expense requires a category");
            }
            String key = categoryRaw.toLowerCase() + "|" + type.name();
            categoryId = categoriesByKeyLower.get(key);
            if (categoryId == null) {
                return err(row, "Category not found or kind mismatch: " + categoryRaw);
            }
        }

        String hash = DedupHash.of(userId, accountId, type, amount, date, note);
        if (existingDbHashes.contains(hash) || batchHashesSoFar.contains(hash)) {
            return new ResolvedRow(row.rowIndex(), ImportJobRowStatus.DUPLICATE,
                    "Duplicate of existing transaction",
                    type, amount, date, accountId, toAccountId, categoryId, note, hash);
        }

        return new ResolvedRow(row.rowIndex(), ImportJobRowStatus.OK, null,
                type, amount, date, accountId, toAccountId, categoryId, note, hash);
    }

    private ResolvedRow err(RawRow row, String msg) {
        return new ResolvedRow(row.rowIndex(), ImportJobRowStatus.ERROR, msg,
                null, null, null, null, null, null, null, null);
    }

    private String trimToMax(String s, int max) {
        if (s == null) return null;
        String t = s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }
}

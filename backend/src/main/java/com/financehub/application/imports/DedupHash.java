package com.financehub.application.imports;

import com.financehub.domain.transaction.TransactionType;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;

public final class DedupHash {

    private DedupHash() {}

    public static String of(Long userId, Long accountId, TransactionType type,
                            BigDecimal amount, LocalDate date, String note) {
        String canonical = String.join("|",
                String.valueOf(userId),
                String.valueOf(accountId),
                type.name(),
                amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(),
                date.toString(),
                note == null ? "" : note.trim());
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}

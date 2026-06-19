package com.financehub.application.imports;

import com.financehub.domain.imports.ImportJobRowStatus;
import com.financehub.domain.transaction.TransactionType;
import com.financehub.infrastructure.parser.RawRow;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RowResolverTest {

    private static final Long USER = 1L;

    private final RowResolver resolver = new RowResolver();

    private final Map<String, Long> accountsByName = Map.of(
            "主帳戶", 10L,
            "副帳戶", 11L,
            "usd 帳", 12L);
    private final Map<Long, String> currencyById = Map.of(
            10L, "TWD",
            11L, "TWD",
            12L, "USD");
    private final Map<String, Long> categoriesByKey = Map.of(
            "薪資|INCOME", 100L,
            "飲食|EXPENSE", 101L);

    private RawRow row(Map<String, String> fields) {
        return new RawRow(1, fields);
    }

    private Map<String, String> base() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("date", "2026-06-01");
        m.put("type", "INCOME");
        m.put("account", "主帳戶");
        m.put("amount", "100.00");
        m.put("category", "薪資");
        m.put("to_account", "");
        m.put("note", "test");
        return m;
    }

    @Test
    void resolvesValidIncome() {
        ResolvedRow r = resolver.resolve(USER, row(base()),
                accountsByName, currencyById, categoriesByKey, Set.of(), new HashSet<>());
        assertThat(r.status()).isEqualTo(ImportJobRowStatus.OK);
        assertThat(r.type()).isEqualTo(TransactionType.INCOME);
        assertThat(r.amount()).isEqualByComparingTo("100.00");
        assertThat(r.accountId()).isEqualTo(10L);
        assertThat(r.categoryId()).isEqualTo(100L);
        assertThat(r.dedupHash()).hasSize(64);
    }

    @Test
    void detectsBadDate() {
        Map<String, String> f = base();
        f.put("date", "06/01/2026");
        ResolvedRow r = resolver.resolve(USER, row(f),
                accountsByName, currencyById, categoriesByKey, Set.of(), new HashSet<>());
        assertThat(r.status()).isEqualTo(ImportJobRowStatus.ERROR);
        assertThat(r.errorMessage()).contains("Invalid date");
    }

    @Test
    void detectsAccountNotFound() {
        Map<String, String> f = base();
        f.put("account", "不存在");
        ResolvedRow r = resolver.resolve(USER, row(f),
                accountsByName, currencyById, categoriesByKey, Set.of(), new HashSet<>());
        assertThat(r.status()).isEqualTo(ImportJobRowStatus.ERROR);
        assertThat(r.errorMessage()).contains("Account not found");
    }

    @Test
    void detectsCategoryKindMismatch() {
        Map<String, String> f = base();
        f.put("type", "EXPENSE");
        f.put("category", "薪資");
        ResolvedRow r = resolver.resolve(USER, row(f),
                accountsByName, currencyById, categoriesByKey, Set.of(), new HashSet<>());
        assertThat(r.status()).isEqualTo(ImportJobRowStatus.ERROR);
        assertThat(r.errorMessage()).contains("Category not found or kind mismatch");
    }

    @Test
    void detectsTransferSameAccount() {
        Map<String, String> f = base();
        f.put("type", "TRANSFER");
        f.put("category", "");
        f.put("to_account", "主帳戶");
        ResolvedRow r = resolver.resolve(USER, row(f),
                accountsByName, currencyById, categoriesByKey, Set.of(), new HashSet<>());
        assertThat(r.status()).isEqualTo(ImportJobRowStatus.ERROR);
        assertThat(r.errorMessage()).contains("source and destination must differ");
    }

    @Test
    void detectsCrossCurrencyTransfer() {
        Map<String, String> f = base();
        f.put("type", "TRANSFER");
        f.put("category", "");
        f.put("to_account", "USD 帳");
        ResolvedRow r = resolver.resolve(USER, row(f),
                accountsByName, currencyById, categoriesByKey, Set.of(), new HashSet<>());
        assertThat(r.status()).isEqualTo(ImportJobRowStatus.ERROR);
        assertThat(r.errorMessage()).contains("Cross-currency");
    }

    @Test
    void marksDuplicateAgainstExistingHash() {
        ResolvedRow first = resolver.resolve(USER, row(base()),
                accountsByName, currencyById, categoriesByKey, Set.of(), new HashSet<>());
        ResolvedRow dup = resolver.resolve(USER, row(base()),
                accountsByName, currencyById, categoriesByKey, Set.of(first.dedupHash()), new HashSet<>());
        assertThat(dup.status()).isEqualTo(ImportJobRowStatus.DUPLICATE);
    }

    @Test
    void marksDuplicateWithinBatch() {
        Set<String> batch = new HashSet<>();
        ResolvedRow first = resolver.resolve(USER, row(base()),
                accountsByName, currencyById, categoriesByKey, Set.of(), batch);
        batch.add(first.dedupHash());
        ResolvedRow dup = resolver.resolve(USER, row(base()),
                accountsByName, currencyById, categoriesByKey, Set.of(), batch);
        assertThat(dup.status()).isEqualTo(ImportJobRowStatus.DUPLICATE);
    }
}

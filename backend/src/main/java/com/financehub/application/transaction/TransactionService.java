package com.financehub.application.transaction;

import com.financehub.domain.account.Account;
import com.financehub.domain.account.AccountRepository;
import com.financehub.domain.category.Category;
import com.financehub.domain.category.CategoryKind;
import com.financehub.domain.category.CategoryRepository;
import com.financehub.domain.transaction.Transaction;
import com.financehub.domain.transaction.TransactionRepository;
import com.financehub.domain.transaction.TransactionType;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;

    public TransactionService(TransactionRepository transactionRepository,
                              AccountRepository accountRepository,
                              CategoryRepository categoryRepository) {
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public List<Transaction> listForUser(Long userId, LocalDate from, LocalDate to) {
        if (from != null && to != null) {
            return transactionRepository.findByUserIdAndTxnDateBetweenOrderByTxnDateDescIdDesc(userId, from, to);
        }
        return transactionRepository.findByUserIdOrderByTxnDateDescIdDesc(userId);
    }

    @Transactional(readOnly = true)
    public Transaction getForUser(Long userId, Long id) {
        return transactionRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new EntityNotFoundException("Transaction not found"));
    }

    @Transactional
    public Transaction create(Long userId, Long accountId, Long toAccountId, Long categoryId,
                              TransactionType type, BigDecimal amount, LocalDate txnDate, String note) {
        validateShape(type, accountId, toAccountId, categoryId);

        Account fromAccount = requireAccount(userId, accountId);
        Account toAccount = null;
        if (type.isTransfer()) {
            toAccount = requireAccount(userId, toAccountId);
            ensureSameCurrency(fromAccount, toAccount);
        } else {
            Category category = requireCategory(userId, categoryId);
            ensureKindMatchesType(category, type);
        }

        Transaction txn = new Transaction(userId, accountId,
                type.isTransfer() ? toAccountId : null,
                type.isTransfer() ? null : categoryId,
                type, amount, txnDate, note);
        Transaction saved = transactionRepository.save(txn);
        applyDelta(fromAccount, toAccount, type, amount);
        return saved;
    }

    @Transactional
    public Transaction update(Long userId, Long id, Long accountId, Long toAccountId, Long categoryId,
                              TransactionType type, BigDecimal amount, LocalDate txnDate, String note) {
        Transaction txn = getForUser(userId, id);
        rollbackBalances(userId, txn);

        validateShape(type, accountId, toAccountId, categoryId);
        Account fromAccount = requireAccount(userId, accountId);
        Account toAccount = null;
        if (type.isTransfer()) {
            toAccount = requireAccount(userId, toAccountId);
            ensureSameCurrency(fromAccount, toAccount);
        } else {
            Category category = requireCategory(userId, categoryId);
            ensureKindMatchesType(category, type);
        }

        txn.setAccountId(accountId);
        txn.setToAccountId(type.isTransfer() ? toAccountId : null);
        txn.setCategoryId(type.isTransfer() ? null : categoryId);
        txn.setType(type);
        txn.setAmount(amount);
        txn.setTxnDate(txnDate);
        txn.setNote(note);

        applyDelta(fromAccount, toAccount, type, amount);
        return txn;
    }

    @Transactional
    public void delete(Long userId, Long id) {
        Transaction txn = getForUser(userId, id);
        rollbackBalances(userId, txn);
        transactionRepository.delete(txn);
    }

    private void rollbackBalances(Long userId, Transaction txn) {
        Account from = requireAccount(userId, txn.getAccountId());
        if (txn.getType().isTransfer()) {
            Account to = requireAccount(userId, txn.getToAccountId());
            applyBalance(from, txn.getAmount());
            applyBalance(to, txn.getAmount().negate());
        } else {
            applyBalance(from, txn.getType().signedAmount(txn.getAmount()).negate());
        }
    }

    private void applyDelta(Account from, Account to, TransactionType type, BigDecimal amount) {
        if (type.isTransfer()) {
            applyBalance(from, amount.negate());
            applyBalance(to, amount);
        } else {
            applyBalance(from, type.signedAmount(amount));
        }
    }

    private void validateShape(TransactionType type, Long accountId, Long toAccountId, Long categoryId) {
        if (type.isTransfer()) {
            if (toAccountId == null) {
                throw new IllegalArgumentException("Transfer requires toAccountId");
            }
            if (toAccountId.equals(accountId)) {
                throw new IllegalArgumentException("Transfer source and destination must differ");
            }
            if (categoryId != null) {
                throw new IllegalArgumentException("Transfer must not have a category");
            }
        } else {
            if (toAccountId != null) {
                throw new IllegalArgumentException("Only transfers may set toAccountId");
            }
            if (categoryId == null) {
                throw new IllegalArgumentException("Income/expense requires a category");
            }
        }
    }

    private void ensureSameCurrency(Account from, Account to) {
        if (!from.getCurrency().equals(to.getCurrency())) {
            throw new IllegalArgumentException("Cross-currency transfer not supported");
        }
    }

    private Account requireAccount(Long userId, Long accountId) {
        return accountRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Account not found"));
    }

    private Category requireCategory(Long userId, Long categoryId) {
        return categoryRepository.findVisibleToUser(categoryId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Category not found"));
    }

    private void ensureKindMatchesType(Category category, TransactionType type) {
        CategoryKind expected = type == TransactionType.INCOME ? CategoryKind.INCOME : CategoryKind.EXPENSE;
        if (category.getKind() != expected) {
            throw new IllegalArgumentException("Category kind does not match transaction type");
        }
    }

    private void applyBalance(Account account, BigDecimal delta) {
        account.setCurrentBalance(account.getCurrentBalance().add(delta));
    }
}

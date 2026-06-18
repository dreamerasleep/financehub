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
    public Transaction create(Long userId, Long accountId, Long categoryId, TransactionType type,
                              BigDecimal amount, LocalDate txnDate, String note) {
        Account account = requireAccount(userId, accountId);
        Category category = requireCategory(userId, categoryId);
        ensureKindMatchesType(category, type);

        Transaction txn = new Transaction(userId, accountId, categoryId, type, amount, txnDate, note);
        Transaction saved = transactionRepository.save(txn);
        applyBalance(account, type.signedAmount(amount));
        return saved;
    }

    @Transactional
    public Transaction update(Long userId, Long id, Long accountId, Long categoryId,
                              TransactionType type, BigDecimal amount, LocalDate txnDate, String note) {
        Transaction txn = getForUser(userId, id);
        Account oldAccount = requireAccount(userId, txn.getAccountId());
        applyBalance(oldAccount, txn.getType().signedAmount(txn.getAmount()).negate());

        Account newAccount = txn.getAccountId().equals(accountId)
                ? oldAccount
                : requireAccount(userId, accountId);
        Category category = requireCategory(userId, categoryId);
        ensureKindMatchesType(category, type);

        txn.setAccountId(accountId);
        txn.setCategoryId(categoryId);
        txn.setType(type);
        txn.setAmount(amount);
        txn.setTxnDate(txnDate);
        txn.setNote(note);

        applyBalance(newAccount, type.signedAmount(amount));
        return txn;
    }

    @Transactional
    public void delete(Long userId, Long id) {
        Transaction txn = getForUser(userId, id);
        Account account = requireAccount(userId, txn.getAccountId());
        applyBalance(account, txn.getType().signedAmount(txn.getAmount()).negate());
        transactionRepository.delete(txn);
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

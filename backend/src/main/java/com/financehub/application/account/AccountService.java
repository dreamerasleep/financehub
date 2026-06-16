package com.financehub.application.account;

import com.financehub.domain.account.Account;
import com.financehub.domain.account.AccountRepository;
import com.financehub.domain.account.AccountType;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class AccountService {

    private final AccountRepository accountRepository;

    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Transactional(readOnly = true)
    public List<Account> listForUser(Long userId) {
        return accountRepository.findByUserIdOrderByIdAsc(userId);
    }

    @Transactional(readOnly = true)
    public Account getForUser(Long userId, Long accountId) {
        return accountRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Account not found"));
    }

    @Transactional
    public Account create(Long userId, String name, AccountType type, String currency, BigDecimal initialBalance) {
        Account account = new Account(userId, name, type, currency,
                initialBalance == null ? BigDecimal.ZERO : initialBalance);
        return accountRepository.save(account);
    }

    @Transactional
    public Account update(Long userId, Long accountId, String name, AccountType type, String currency, BigDecimal currentBalance) {
        Account account = getForUser(userId, accountId);
        account.setName(name);
        account.setType(type);
        account.setCurrency(currency);
        if (currentBalance != null) {
            account.setCurrentBalance(currentBalance);
        }
        return account;
    }

    @Transactional
    public void delete(Long userId, Long accountId) {
        Account account = getForUser(userId, accountId);
        accountRepository.delete(account);
    }
}

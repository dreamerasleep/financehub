package com.financehub.api.account;

import com.financehub.application.account.AccountService;
import com.financehub.domain.account.Account;
import com.financehub.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    public List<AccountDtos.AccountResponse> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return accountService.listForUser(user.id()).stream()
                .map(AccountDtos.AccountResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public AccountDtos.AccountResponse get(@AuthenticationPrincipal AuthenticatedUser user,
                                           @PathVariable Long id) {
        return AccountDtos.AccountResponse.from(accountService.getForUser(user.id(), id));
    }

    @PostMapping
    public ResponseEntity<AccountDtos.AccountResponse> create(@AuthenticationPrincipal AuthenticatedUser user,
                                                              @Valid @RequestBody AccountDtos.CreateAccountRequest request) {
        Account account = accountService.create(user.id(), request.name(), request.type(),
                request.currency(), request.initialBalance());
        return ResponseEntity.status(201).body(AccountDtos.AccountResponse.from(account));
    }

    @PutMapping("/{id}")
    public AccountDtos.AccountResponse update(@AuthenticationPrincipal AuthenticatedUser user,
                                              @PathVariable Long id,
                                              @Valid @RequestBody AccountDtos.UpdateAccountRequest request) {
        Account account = accountService.update(user.id(), id, request.name(), request.type(),
                request.currency(), request.currentBalance());
        return AccountDtos.AccountResponse.from(account);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser user,
                                       @PathVariable Long id) {
        accountService.delete(user.id(), id);
        return ResponseEntity.noContent().build();
    }
}

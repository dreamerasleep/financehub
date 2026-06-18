package com.financehub.api.transaction;

import com.financehub.application.transaction.TransactionService;
import com.financehub.domain.transaction.Transaction;
import com.financehub.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @GetMapping
    public List<TransactionDtos.TransactionResponse> list(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return transactionService.listForUser(user.id(), from, to).stream()
                .map(TransactionDtos.TransactionResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public TransactionDtos.TransactionResponse get(@AuthenticationPrincipal AuthenticatedUser user,
                                                   @PathVariable Long id) {
        return TransactionDtos.TransactionResponse.from(transactionService.getForUser(user.id(), id));
    }

    @PostMapping
    public ResponseEntity<TransactionDtos.TransactionResponse> create(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody TransactionDtos.CreateTransactionRequest request) {
        Transaction txn = transactionService.create(user.id(), request.accountId(), request.categoryId(),
                request.type(), request.amount(), request.txnDate(), request.note());
        return ResponseEntity.status(201).body(TransactionDtos.TransactionResponse.from(txn));
    }

    @PutMapping("/{id}")
    public TransactionDtos.TransactionResponse update(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id,
            @Valid @RequestBody TransactionDtos.UpdateTransactionRequest request) {
        Transaction txn = transactionService.update(user.id(), id, request.accountId(), request.categoryId(),
                request.type(), request.amount(), request.txnDate(), request.note());
        return TransactionDtos.TransactionResponse.from(txn);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser user,
                                       @PathVariable Long id) {
        transactionService.delete(user.id(), id);
        return ResponseEntity.noContent().build();
    }
}

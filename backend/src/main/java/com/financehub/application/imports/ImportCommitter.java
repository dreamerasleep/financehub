package com.financehub.application.imports;

import com.financehub.api.imports.ImportDtos.ImportCommitResult;
import com.financehub.application.transaction.TransactionService;
import com.financehub.domain.account.Account;
import com.financehub.domain.account.AccountRepository;
import com.financehub.domain.category.Category;
import com.financehub.domain.category.CategoryRepository;
import com.financehub.domain.imports.ImportJob;
import com.financehub.domain.imports.ImportJobRepository;
import com.financehub.domain.imports.ImportJobRow;
import com.financehub.domain.imports.ImportJobRowRepository;
import com.financehub.domain.imports.ImportJobRowStatus;
import com.financehub.domain.imports.ImportJobStatus;
import com.financehub.domain.transaction.Transaction;
import com.financehub.domain.transaction.TransactionType;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class ImportCommitter {

    private final ImportJobRepository jobRepository;
    private final ImportJobRowRepository rowRepository;
    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;
    private final TransactionService transactionService;

    public ImportCommitter(ImportJobRepository jobRepository,
                           ImportJobRowRepository rowRepository,
                           AccountRepository accountRepository,
                           CategoryRepository categoryRepository,
                           TransactionService transactionService) {
        this.jobRepository = jobRepository;
        this.rowRepository = rowRepository;
        this.accountRepository = accountRepository;
        this.categoryRepository = categoryRepository;
        this.transactionService = transactionService;
    }

    @Transactional
    public ImportCommitResult commit(Long userId, Long jobId, List<Long> rowIds) {
        ImportJob job = jobRepository.findByIdAndUserId(jobId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Import job not found"));
        if (job.getStatus() != ImportJobStatus.PENDING) {
            throw new IllegalStateException(
                    "Job is not PENDING (status=" + job.getStatus() + ")");
        }

        List<ImportJobRow> locked = rowRepository.lockOkRowsByJobId(jobId);
        Set<Long> selection = rowIds == null || rowIds.isEmpty()
                ? null
                : new HashSet<>(rowIds);
        List<ImportJobRow> selected = locked.stream()
                .filter(r -> selection == null || selection.contains(r.getId()))
                .toList();

        boolean anyInvalid = false;
        for (ImportJobRow r : selected) {
            String reason = revalidate(userId, r);
            if (reason != null) {
                r.setStatus(ImportJobRowStatus.ERROR);
                r.setErrorMessage(reason);
                anyInvalid = true;
            }
        }
        if (anyInvalid) {
            recountAndSave(job);
            throw new IllegalStateException("Some rows could no longer be committed");
        }

        List<Long> txnIds = new ArrayList<>(selected.size());
        for (ImportJobRow r : selected) {
            Transaction created = transactionService.create(
                    userId,
                    r.getParsedAccountId(),
                    r.getParsedToAccountId(),
                    r.getParsedCategoryId(),
                    r.getParsedType(),
                    r.getParsedAmount(),
                    r.getParsedDate(),
                    r.getParsedNote());
            txnIds.add(created.getId());
        }

        job.setStatus(ImportJobStatus.COMMITTED);
        job.setCommittedAt(OffsetDateTime.now());
        jobRepository.save(job);
        return new ImportCommitResult(jobId, txnIds.size(), Collections.unmodifiableList(txnIds));
    }

    private String revalidate(Long userId, ImportJobRow row) {
        if (row.getParsedAccountId() == null) return "Account missing";
        Account from = accountRepository.findByIdAndUserId(row.getParsedAccountId(), userId).orElse(null);
        if (from == null) return "Account not found";
        if (row.getParsedType() == TransactionType.TRANSFER) {
            if (row.getParsedToAccountId() == null) return "Transfer requires to_account";
            Account to = accountRepository.findByIdAndUserId(row.getParsedToAccountId(), userId).orElse(null);
            if (to == null) return "to_account not found";
            if (!from.getCurrency().equals(to.getCurrency())) return "Cross-currency transfer not supported";
        } else {
            if (row.getParsedCategoryId() == null) return "Category missing";
            Category cat = categoryRepository.findVisibleToUser(row.getParsedCategoryId(), userId).orElse(null);
            if (cat == null) return "Category not found";
        }
        return null;
    }

    private void recountAndSave(ImportJob job) {
        List<ImportJobRow> all = rowRepository.findByJobIdOrderByRowIndexAsc(job.getId());
        int ok = 0, err = 0, dup = 0;
        for (ImportJobRow r : all) {
            switch (r.getStatus()) {
                case OK -> ok++;
                case ERROR -> err++;
                case DUPLICATE -> dup++;
            }
        }
        job.setOkCount(ok);
        job.setErrorCount(err);
        job.setDupCount(dup);
        jobRepository.save(job);
    }
}

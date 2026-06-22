package com.financehub.application.imports;

import com.financehub.domain.account.Account;
import com.financehub.domain.account.AccountRepository;
import com.financehub.domain.category.Category;
import com.financehub.domain.category.CategoryRepository;
import com.financehub.domain.imports.ImportFormat;
import com.financehub.domain.imports.ImportJob;
import com.financehub.domain.imports.ImportJobRepository;
import com.financehub.api.imports.ImportDtos;
import com.financehub.domain.imports.ImportJobRow;
import com.financehub.domain.imports.ImportJobRowRepository;
import com.financehub.domain.imports.ImportJobRowStatus;
import com.financehub.domain.imports.ImportJobStatus;
import com.financehub.domain.transaction.Transaction;
import com.financehub.domain.transaction.TransactionRepository;
import com.financehub.infrastructure.parser.RawRow;
import com.financehub.infrastructure.parser.TransactionFileParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ImportJobService {

    private final Map<ImportFormat, TransactionFileParser> parsers;
    private final RowResolver rowResolver;
    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;
    private final ImportJobRepository jobRepository;
    private final ImportJobRowRepository rowRepository;
    private final ObjectMapper objectMapper;
    private final ImportProperties properties;

    public ImportJobService(List<TransactionFileParser> parsersList,
                            RowResolver rowResolver,
                            AccountRepository accountRepository,
                            CategoryRepository categoryRepository,
                            TransactionRepository transactionRepository,
                            ImportJobRepository jobRepository,
                            ImportJobRowRepository rowRepository,
                            ObjectMapper objectMapper,
                            ImportProperties properties) {
        this.parsers = parsersList.stream()
                .collect(Collectors.toUnmodifiableMap(TransactionFileParser::supports, p -> p));
        this.rowResolver = rowResolver;
        this.accountRepository = accountRepository;
        this.categoryRepository = categoryRepository;
        this.transactionRepository = transactionRepository;
        this.jobRepository = jobRepository;
        this.rowRepository = rowRepository;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Transactional
    public ImportJob upload(Long userId, MultipartFile file) throws IOException {
        ImportFormat format = detectFormat(file.getOriginalFilename());
        TransactionFileParser parser = parsers.get(format);
        if (parser == null) {
            throw new UnsupportedFormatException("Unsupported format: " + format);
        }

        List<RawRow> rawRows;
        try (InputStream in = file.getInputStream()) {
            rawRows = parser.parse(in);
        }
        if (rawRows.size() > properties.getMaxRows()) {
            throw new IllegalArgumentException(
                    "File exceeds maximum row count of " + properties.getMaxRows());
        }

        Map<String, Long> accountsByNameLower = new HashMap<>();
        Map<Long, String> currencyByAccount = new HashMap<>();
        for (Account a : accountRepository.findByUserIdOrderByIdAsc(userId)) {
            accountsByNameLower.put(a.getName().toLowerCase(), a.getId());
            currencyByAccount.put(a.getId(), a.getCurrency());
        }
        Map<String, Long> categoriesByKey = new HashMap<>();
        for (Category c : categoryRepository.findVisibleTo(userId)) {
            String key = c.getName().toLowerCase() + "|" + c.getKind().name();
            categoriesByKey.put(key, c.getId());
        }

        Set<String> existingHashes = computeExistingHashes(userId, rawRows);
        Set<String> batchHashes = new HashSet<>();

        ImportJob job = new ImportJob(userId, file.getOriginalFilename(), format);
        job = jobRepository.save(job);

        int okCount = 0, errorCount = 0, dupCount = 0;
        for (RawRow raw : rawRows) {
            ResolvedRow resolved = rowResolver.resolve(userId, raw,
                    accountsByNameLower, currencyByAccount, categoriesByKey,
                    existingHashes, batchHashes);

            ImportJobRow row = new ImportJobRow(
                    job.getId(),
                    resolved.rowIndex(),
                    toJson(raw.fields()),
                    resolved.status());
            row.setParsedType(resolved.type());
            row.setParsedAmount(resolved.amount());
            row.setParsedDate(resolved.date());
            row.setParsedAccountId(resolved.accountId());
            row.setParsedToAccountId(resolved.toAccountId());
            row.setParsedCategoryId(resolved.categoryId());
            row.setParsedNote(resolved.note());
            row.setDedupHash(resolved.dedupHash());
            row.setErrorMessage(resolved.errorMessage());
            rowRepository.save(row);

            switch (resolved.status()) {
                case OK -> {
                    okCount++;
                    if (resolved.dedupHash() != null) batchHashes.add(resolved.dedupHash());
                }
                case ERROR -> errorCount++;
                case DUPLICATE -> dupCount++;
            }
        }

        job.setRowCount(rawRows.size());
        job.setOkCount(okCount);
        job.setErrorCount(errorCount);
        job.setDupCount(dupCount);
        return jobRepository.save(job);
    }

    @Transactional(readOnly = true)
    public ImportJob get(Long userId, Long id) {
        return jobRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new EntityNotFoundException("Import job not found"));
    }

    @Transactional(readOnly = true)
    public List<ImportJobRow> getRows(Long userId, Long id) {
        ImportJob job = get(userId, id);
        return rowRepository.findByJobIdOrderByRowIndexAsc(job.getId());
    }

    @Transactional(readOnly = true)
    public List<ImportJob> listRecent(Long userId) {
        return jobRepository.findTop20ByUserIdOrderByIdDesc(userId);
    }

    @Transactional
    public void cancel(Long userId, Long id) {
        ImportJob job = get(userId, id);
        if (job.getStatus() != ImportJobStatus.PENDING) {
            throw new IllegalStateException("Job is not PENDING (status=" + job.getStatus() + ")");
        }
        job.setStatus(ImportJobStatus.CANCELLED);
    }

    @Transactional
    public ImportDtos.PatchRowResponse patchRow(
            Long userId,
            Long jobId,
            Long rowId,
            ImportDtos.PatchRowRequest body) {

        ImportJob job = jobRepository.findByIdAndUserId(jobId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Import job not found"));

        if (job.getStatus() != ImportJobStatus.PENDING) {
            throw new JobNotPendingException(
                    "Job is not PENDING (status=" + job.getStatus() + ")");
        }

        ImportJobRow row = rowRepository.findByIdAndJobId(rowId, jobId)
                .orElseThrow(() -> new EntityNotFoundException("Import row not found"));

        if (row.getStatus() == ImportJobRowStatus.OK) {
            throw new OkRowNotEditableException(
                    "OK rows cannot be edited; cancel selection or re-upload instead");
        }

        Map<String, Long> accountsByNameLower = new HashMap<>();
        Map<Long, String> currencyByAccount = new HashMap<>();
        for (Account a : accountRepository.findByUserIdOrderByIdAsc(userId)) {
            accountsByNameLower.put(a.getName().toLowerCase(), a.getId());
            currencyByAccount.put(a.getId(), a.getCurrency());
        }
        Map<String, Long> categoriesByKey = new HashMap<>();
        for (Category c : categoryRepository.findVisibleTo(userId)) {
            String key = c.getName().toLowerCase() + "|" + c.getKind().name();
            categoriesByKey.put(key, c.getId());
        }

        Map<String, String> raw = new HashMap<>();
        raw.put("date", body.date() == null ? "" : body.date());
        raw.put("type", body.type() == null ? "" : body.type());
        raw.put("account", body.account() == null ? "" : body.account());
        raw.put("amount", body.amount() == null ? "" : body.amount());
        raw.put("category", body.category() == null ? "" : body.category());
        raw.put("to_account", body.toAccount() == null ? "" : body.toAccount());
        raw.put("note", body.note() == null ? "" : body.note());

        Set<String> existingDbHashes = computeExistingHashesForSingleRow(userId, raw);
        Set<String> batchHashesSoFar =
                rowRepository.findOkDedupHashesByJobIdExcept(jobId, rowId);

        RawRow rawRow = new RawRow(row.getRowIndex(), raw);
        ResolvedRow resolved = rowResolver.resolve(userId, rawRow,
                accountsByNameLower, currencyByAccount, categoriesByKey,
                existingDbHashes, batchHashesSoFar);

        row.setStatus(resolved.status());
        row.setParsedType(resolved.type());
        row.setParsedAmount(resolved.amount());
        row.setParsedDate(resolved.date());
        row.setParsedAccountId(resolved.accountId());
        row.setParsedToAccountId(resolved.toAccountId());
        row.setParsedCategoryId(resolved.categoryId());
        row.setParsedNote(resolved.note());
        row.setDedupHash(resolved.dedupHash());
        row.setErrorMessage(resolved.errorMessage());
        row.setRawJson(toJson(raw));
        rowRepository.save(row);

        long okCount = rowRepository.countByJobIdAndStatus(jobId, ImportJobRowStatus.OK);
        long errorCount = rowRepository.countByJobIdAndStatus(jobId, ImportJobRowStatus.ERROR);
        long dupCount = rowRepository.countByJobIdAndStatus(jobId, ImportJobRowStatus.DUPLICATE);
        job.setOkCount((int) okCount);
        job.setErrorCount((int) errorCount);
        job.setDupCount((int) dupCount);
        jobRepository.save(job);

        return new ImportDtos.PatchRowResponse(
                ImportDtos.ImportJobResponse.from(job),
                ImportDtos.ImportJobRowResponse.from(row));
    }

    private Set<String> computeExistingHashesForSingleRow(Long userId, Map<String, String> raw) {
        LocalDate date = tryParseDate(raw.get("date"));
        if (date == null) return Set.of();
        List<Transaction> candidates = transactionRepository
                .findByUserIdAndTxnDateBetween(userId, date, date);
        Set<String> hashes = new HashSet<>();
        for (Transaction t : candidates) {
            hashes.add(DedupHash.of(userId, t.getAccountId(), t.getType(),
                    t.getAmount(), t.getTxnDate(), t.getNote()));
        }
        return hashes;
    }

    private ImportFormat detectFormat(String filename) {
        if (filename == null) throw new IllegalArgumentException("Filename required");
        String lower = filename.toLowerCase();
        if (lower.endsWith(".csv")) return ImportFormat.CSV;
        if (lower.endsWith(".xlsx")) return ImportFormat.XLSX;
        throw new UnsupportedFormatException("Unsupported extension: " + filename);
    }

    private Set<String> computeExistingHashes(Long userId, List<RawRow> rows) {
        Set<LocalDate> dates = rows.stream()
                .map(r -> tryParseDate(r.fields().get("date")))
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        if (dates.isEmpty()) return Set.of();
        LocalDate min = dates.stream().min(java.util.Comparator.naturalOrder()).orElseThrow();
        LocalDate max = dates.stream().max(java.util.Comparator.naturalOrder()).orElseThrow();

        List<Transaction> candidates = transactionRepository
                .findByUserIdAndTxnDateBetween(userId, min, max);
        Set<String> hashes = new HashSet<>();
        for (Transaction t : candidates) {
            hashes.add(DedupHash.of(userId, t.getAccountId(), t.getType(),
                    t.getAmount(), t.getTxnDate(), t.getNote()));
        }
        return hashes;
    }

    private LocalDate tryParseDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return LocalDate.parse(raw.trim());
        } catch (Exception ex) {
            return null;
        }
    }

    private String toJson(Map<String, String> fields) {
        try {
            return objectMapper.writeValueAsString(fields);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize raw row", ex);
        }
    }
}

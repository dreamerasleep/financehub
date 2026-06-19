package com.financehub.api.imports;

import com.financehub.domain.imports.ImportFormat;
import com.financehub.domain.imports.ImportJob;
import com.financehub.domain.imports.ImportJobRow;
import com.financehub.domain.imports.ImportJobRowStatus;
import com.financehub.domain.imports.ImportJobStatus;
import com.financehub.domain.transaction.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public final class ImportDtos {

    private ImportDtos() {}

    public record ImportJobResponse(
            Long id,
            String filename,
            ImportFormat format,
            ImportJobStatus status,
            int rowCount, int okCount, int errorCount, int dupCount,
            OffsetDateTime createdAt,
            OffsetDateTime committedAt,
            OffsetDateTime expiresAt
    ) {
        public static ImportJobResponse from(ImportJob j) {
            return new ImportJobResponse(j.getId(), j.getFilename(), j.getFormat(), j.getStatus(),
                    j.getRowCount(), j.getOkCount(), j.getErrorCount(), j.getDupCount(),
                    j.getCreatedAt(), j.getCommittedAt(), j.getExpiresAt());
        }
    }

    public record ImportJobRowResponse(
            Long id,
            int rowIndex,
            ImportJobRowStatus status,
            String errorMessage,
            String rawJson,
            TransactionType parsedType,
            BigDecimal parsedAmount,
            LocalDate parsedDate,
            Long parsedAccountId,
            Long parsedToAccountId,
            Long parsedCategoryId,
            String parsedNote
    ) {
        public static ImportJobRowResponse from(ImportJobRow r) {
            return new ImportJobRowResponse(r.getId(), r.getRowIndex(), r.getStatus(),
                    r.getErrorMessage(), r.getRawJson(), r.getParsedType(), r.getParsedAmount(),
                    r.getParsedDate(), r.getParsedAccountId(), r.getParsedToAccountId(),
                    r.getParsedCategoryId(), r.getParsedNote());
        }
    }

    public record ImportJobDetailResponse(
            ImportJobResponse job,
            List<ImportJobRowResponse> rows
    ) {}

    public record CommitRequest(List<Long> rowIds) {}

    public record ImportCommitResult(
            Long jobId,
            int committedCount,
            List<Long> transactionIds
    ) {}
}

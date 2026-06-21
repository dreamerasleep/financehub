package com.financehub.domain.imports;

import com.financehub.domain.transaction.TransactionType;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "import_job_rows")
public class ImportJobRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_id", nullable = false)
    private Long jobId;

    @Column(name = "row_index", nullable = false)
    private int rowIndex;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_json", nullable = false, columnDefinition = "jsonb")
    private String rawJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "parsed_type", length = 10)
    private TransactionType parsedType;

    @Column(name = "parsed_amount")
    private BigDecimal parsedAmount;

    @Column(name = "parsed_date")
    private LocalDate parsedDate;

    @Column(name = "parsed_account_id")
    private Long parsedAccountId;

    @Column(name = "parsed_to_account_id")
    private Long parsedToAccountId;

    @Column(name = "parsed_category_id")
    private Long parsedCategoryId;

    @Column(name = "parsed_note", length = 255)
    private String parsedNote;

    @Column(name = "dedup_hash", length = 64)
    private String dedupHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private ImportJobRowStatus status;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    protected ImportJobRow() {}

    public ImportJobRow(Long jobId, int rowIndex, String rawJson, ImportJobRowStatus status) {
        this.jobId = jobId;
        this.rowIndex = rowIndex;
        this.rawJson = rawJson;
        this.status = status;
    }

    public Long getId() { return id; }
    public Long getJobId() { return jobId; }
    public int getRowIndex() { return rowIndex; }
    public String getRawJson() { return rawJson; }
    public void setRawJson(String v) { this.rawJson = v; }
    public TransactionType getParsedType() { return parsedType; }
    public void setParsedType(TransactionType v) { this.parsedType = v; }
    public BigDecimal getParsedAmount() { return parsedAmount; }
    public void setParsedAmount(BigDecimal v) { this.parsedAmount = v; }
    public LocalDate getParsedDate() { return parsedDate; }
    public void setParsedDate(LocalDate v) { this.parsedDate = v; }
    public Long getParsedAccountId() { return parsedAccountId; }
    public void setParsedAccountId(Long v) { this.parsedAccountId = v; }
    public Long getParsedToAccountId() { return parsedToAccountId; }
    public void setParsedToAccountId(Long v) { this.parsedToAccountId = v; }
    public Long getParsedCategoryId() { return parsedCategoryId; }
    public void setParsedCategoryId(Long v) { this.parsedCategoryId = v; }
    public String getParsedNote() { return parsedNote; }
    public void setParsedNote(String v) { this.parsedNote = v; }
    public String getDedupHash() { return dedupHash; }
    public void setDedupHash(String v) { this.dedupHash = v; }
    public ImportJobRowStatus getStatus() { return status; }
    public void setStatus(ImportJobRowStatus v) { this.status = v; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String v) { this.errorMessage = v; }
}

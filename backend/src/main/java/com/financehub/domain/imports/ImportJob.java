package com.financehub.domain.imports;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "import_jobs")
public class ImportJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String filename;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ImportFormat format;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ImportJobStatus status;

    @Column(name = "row_count", nullable = false)
    private int rowCount;

    @Column(name = "ok_count", nullable = false)
    private int okCount;

    @Column(name = "error_count", nullable = false)
    private int errorCount;

    @Column(name = "dup_count", nullable = false)
    private int dupCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "committed_at")
    private OffsetDateTime committedAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        if (expiresAt == null) expiresAt = now.plusHours(24);
        if (status == null) status = ImportJobStatus.PENDING;
    }

    protected ImportJob() {}

    public ImportJob(Long userId, String filename, ImportFormat format) {
        this.userId = userId;
        this.filename = filename;
        this.format = format;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getFilename() { return filename; }
    public ImportFormat getFormat() { return format; }
    public ImportJobStatus getStatus() { return status; }
    public void setStatus(ImportJobStatus status) { this.status = status; }
    public int getRowCount() { return rowCount; }
    public void setRowCount(int v) { this.rowCount = v; }
    public int getOkCount() { return okCount; }
    public void setOkCount(int v) { this.okCount = v; }
    public int getErrorCount() { return errorCount; }
    public void setErrorCount(int v) { this.errorCount = v; }
    public int getDupCount() { return dupCount; }
    public void setDupCount(int v) { this.dupCount = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getCommittedAt() { return committedAt; }
    public void setCommittedAt(OffsetDateTime v) { this.committedAt = v; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(OffsetDateTime v) { this.expiresAt = v; }
}

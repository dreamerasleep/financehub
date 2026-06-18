# Sprint 3 CSV/Excel Import Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement Sprint 3 — CSV/XLSX bulk import for transactions with staged preview, row-level error/duplicate reporting, and partial commit.

**Architecture:** Two-table staging model (`import_jobs` + `import_job_rows`). Upload → parse → resolve → persist staging. User reviews row statuses (OK/ERROR/DUPLICATE) in the frontend, then commits. Commit re-resolves FK validity and delegates per-row insertion to the existing `TransactionService.create`, reusing dual-balance sync.

**Tech Stack:** Spring Boot 3.3.4 · Java 21 · Spring Data JPA · Flyway · PostgreSQL 16 · Apache Commons CSV 1.11.0 · Apache POI 5.3.0 (ooxml streaming) · Testcontainers 1.20.4 · React 19 · TypeScript · Ant Design 5 · TanStack Query 5 · React Router 7.

**Spec reference:** `docs/superpowers/specs/2026-06-18-csv-excel-import-design.md`

## Global Constraints

- All schema changes go through Flyway in `backend/src/main/resources/db/migration/` (next file: `V4__import_jobs.sql`).
- JPA `spring.jpa.hibernate.ddl-auto: validate` — entities must exactly match schema.
- All amounts use `NUMERIC(18, 2)` / `BigDecimal`; never `double`/`float`.
- All timestamps stored as `TIMESTAMPTZ` / `OffsetDateTime`.
- Cross-user isolation: every query that touches user data filters by `userId` from `AuthenticatedUser`.
- File size cap: 5 MB (`spring.servlet.multipart.max-file-size`).
- Row cap: 10000 rows per file (`financehub.import.max-rows`).
- Job TTL: 24 hours (`financehub.import.job-ttl: PT24H`).
- Expiry scan: hourly cron (`financehub.import.expiry-cron: "0 0 * * * *"`).
- Backend package layout: `api/<feature>`, `application/<feature>`, `domain/<feature>`, `infrastructure/<feature>`.
- Commit-message style: English, verb-first, focus on *why*. No `git add -A`/`.`.
- Backend IT target: 20 → 39 (+19); plus 6 parser unit tests.
- Frontend tests: none added this sprint (lint + typecheck + manual Playwright E2E).
- Communication language with reviewer: 繁體中文.

---

## File Structure

### Backend new files

| Path | Responsibility |
| ---- | -------------- |
| `backend/src/main/resources/db/migration/V4__import_jobs.sql` | Schema for `import_jobs` and `import_job_rows` |
| `backend/src/main/java/com/financehub/domain/imports/ImportJob.java` | JPA entity for job header |
| `backend/src/main/java/com/financehub/domain/imports/ImportJobRow.java` | JPA entity for parsed row |
| `backend/src/main/java/com/financehub/domain/imports/ImportJobStatus.java` | Enum: PENDING/COMMITTED/CANCELLED/EXPIRED |
| `backend/src/main/java/com/financehub/domain/imports/ImportJobRowStatus.java` | Enum: OK/ERROR/DUPLICATE |
| `backend/src/main/java/com/financehub/domain/imports/ImportFormat.java` | Enum: CSV/XLSX |
| `backend/src/main/java/com/financehub/domain/imports/ImportJobRepository.java` | Repo for job header |
| `backend/src/main/java/com/financehub/domain/imports/ImportJobRowRepository.java` | Repo for rows (find by job, lock for commit) |
| `backend/src/main/java/com/financehub/infrastructure/parser/RawRow.java` | Record `{int rowIndex, Map<String,String> fields}` |
| `backend/src/main/java/com/financehub/infrastructure/parser/TransactionFileParser.java` | Parser interface |
| `backend/src/main/java/com/financehub/infrastructure/parser/CsvTransactionFileParser.java` | CSV impl (Commons CSV) |
| `backend/src/main/java/com/financehub/infrastructure/parser/XlsxTransactionFileParser.java` | XLSX impl (POI streaming) |
| `backend/src/main/java/com/financehub/application/imports/RowResolver.java` | Resolves account/category/type/dup-hash per row |
| `backend/src/main/java/com/financehub/application/imports/ResolvedRow.java` | Record of resolver output (status + parsed fields) |
| `backend/src/main/java/com/financehub/application/imports/ImportJobService.java` | Upload, list, get, cancel orchestration |
| `backend/src/main/java/com/financehub/application/imports/ImportCommitter.java` | Commit orchestration (re-resolve + create txns) |
| `backend/src/main/java/com/financehub/application/imports/ImportExpiryJob.java` | `@Scheduled` PENDING→EXPIRED sweep |
| `backend/src/main/java/com/financehub/api/imports/ImportController.java` | REST endpoints |
| `backend/src/main/java/com/financehub/api/imports/ImportDtos.java` | Request/response records |

### Backend modified files

| Path | Change |
| ---- | ------ |
| `backend/pom.xml` | Add `commons-csv` + `poi-ooxml` deps |
| `backend/src/main/resources/application.yml` | Add multipart limits, `financehub.import.*` keys |
| `backend/src/main/java/com/financehub/application/transaction/TransactionService.java` | Add `package-private List<Transaction> bulkCreate(...)` if needed — but reuse `create` per-row to avoid duplicating logic |

### Backend test files

| Path | Responsibility |
| ---- | -------------- |
| `backend/src/test/resources/imports/sample-mixed.csv` | Fixture: 3 OK + 2 ERROR + 1 DUPLICATE rows |
| `backend/src/test/resources/imports/sample-transfer.xlsx` | Fixture: TRANSFER rows in XLSX |
| `backend/src/test/resources/imports/sample-large.csv` | Fixture: 10001 rows (boundary) |
| `backend/src/test/resources/imports/sample-good.csv` | Fixture: 3 INCOME + 2 EXPENSE + 1 TRANSFER, all OK |
| `backend/src/test/java/com/financehub/infrastructure/parser/CsvTransactionFileParserTest.java` | CSV unit tests 1–3 |
| `backend/src/test/java/com/financehub/infrastructure/parser/XlsxTransactionFileParserTest.java` | XLSX unit tests 4–6 |
| `backend/src/test/java/com/financehub/api/imports/ImportJobIT.java` | Upload + GET tests (cases 7–15, 22–25) |
| `backend/src/test/java/com/financehub/api/imports/ImportCommitIT.java` | Commit/cancel/expiry tests (cases 16–21) |

### Frontend new files

| Path | Responsibility |
| ---- | -------------- |
| `frontend/src/types/import.ts` | DTO types |
| `frontend/src/api/imports.ts` | API client functions |
| `frontend/src/pages/ImportPage.tsx` | Upload + preview UI |

### Frontend modified files

| Path | Change |
| ---- | ------ |
| `frontend/src/App.tsx` | Add `/import` route under ProtectedRoute |
| `frontend/src/components/AppLayout.tsx` | Add 「匯入」 menu entry |

### Docs

| Path | Change |
| ---- | ------ |
| `docs-site/docs/user-guide/import.md` | New: user-facing import doc |
| `docs-site/docs/api-reference/imports.md` | New: API ref |
| `docs-site/docs/architecture/database.md` | Add V4 table sections |
| `docs-site/docs/changelog.md` | Add `[Unreleased] — Sprint 3` entry |
| `docs-site/docs/index.md` | Add 「CSV / Excel 匯入」 row to status table |
| `docs-site/mkdocs.yml` | Add nav entries for new pages |
| `docs/workbook.md` | Mark Sprint 3 tasks complete |

### Commit grouping

| Commit | Tasks |
| ------ | ----- |
| **C1** | T01–T03 (schema + entities + CSV parser + resolver) |
| **C2** | T04–T05 (XLSX parser + import-job service create) |
| **C3** | T06–T09 (read API + commit + cancel + scheduler + multipart limits) |
| **C4** | T10–T11 (frontend upload + preview UI) |
| **C5** | T12 (Playwright E2E) |
| **C6** | T13 (docs sync) |

---

## Task 1: V4 Migration + Domain Entities + Repositories

**Files:**
- Create: `backend/src/main/resources/db/migration/V4__import_jobs.sql`
- Create: `backend/src/main/java/com/financehub/domain/imports/ImportFormat.java`
- Create: `backend/src/main/java/com/financehub/domain/imports/ImportJobStatus.java`
- Create: `backend/src/main/java/com/financehub/domain/imports/ImportJobRowStatus.java`
- Create: `backend/src/main/java/com/financehub/domain/imports/ImportJob.java`
- Create: `backend/src/main/java/com/financehub/domain/imports/ImportJobRow.java`
- Create: `backend/src/main/java/com/financehub/domain/imports/ImportJobRepository.java`
- Create: `backend/src/main/java/com/financehub/domain/imports/ImportJobRowRepository.java`
- Test: existing test suite must continue to start (Flyway migrate + JPA validate succeed)

**Interfaces:**
- Consumes: nothing new
- Produces:
  - `ImportFormat { CSV, XLSX }`
  - `ImportJobStatus { PENDING, COMMITTED, CANCELLED, EXPIRED }`
  - `ImportJobRowStatus { OK, ERROR, DUPLICATE }`
  - `ImportJob` entity with getters/setters for: `id, userId, filename, format, status, rowCount, okCount, errorCount, dupCount, createdAt, committedAt, expiresAt`
  - `ImportJobRow` entity with: `id, jobId, rowIndex, rawJson (String), parsedType, parsedAmount, parsedDate, parsedAccountId, parsedToAccountId, parsedCategoryId, parsedNote, dedupHash, status, errorMessage`
  - `ImportJobRepository extends JpaRepository<ImportJob, Long>` with:
    - `Optional<ImportJob> findByIdAndUserId(Long id, Long userId)`
    - `List<ImportJob> findTop20ByUserIdOrderByIdDesc(Long userId)`
    - `List<ImportJob> findByStatusAndExpiresAtBefore(ImportJobStatus status, OffsetDateTime when)`
  - `ImportJobRowRepository extends JpaRepository<ImportJobRow, Long>` with:
    - `List<ImportJobRow> findByJobIdOrderByRowIndexAsc(Long jobId)`
    - `@Lock(PESSIMISTIC_WRITE) @Query("...WHERE r.jobId=?1 AND r.status='OK'") List<ImportJobRow> lockOkRows(Long jobId)`

- [ ] **Step 1.1: Write `V4__import_jobs.sql`**

Create `backend/src/main/resources/db/migration/V4__import_jobs.sql`:

```sql
-- FinanceHub V4: import_jobs staging tables for CSV/XLSX bulk import

CREATE TABLE import_jobs (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    filename     VARCHAR(255) NOT NULL,
    format       VARCHAR(10)  NOT NULL CHECK (format IN ('CSV', 'XLSX')),
    status       VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
                 CHECK (status IN ('PENDING', 'COMMITTED', 'CANCELLED', 'EXPIRED')),
    row_count    INT NOT NULL DEFAULT 0,
    ok_count     INT NOT NULL DEFAULT 0,
    error_count  INT NOT NULL DEFAULT 0,
    dup_count    INT NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    committed_at TIMESTAMPTZ,
    expires_at   TIMESTAMPTZ NOT NULL DEFAULT now() + INTERVAL '24 hours'
);

CREATE INDEX idx_import_jobs_user_id ON import_jobs(user_id);
CREATE INDEX idx_import_jobs_status_expires ON import_jobs(status, expires_at);

CREATE TABLE import_job_rows (
    id                   BIGSERIAL PRIMARY KEY,
    job_id               BIGINT NOT NULL REFERENCES import_jobs(id) ON DELETE CASCADE,
    row_index            INT NOT NULL,
    raw_json             JSONB NOT NULL,
    parsed_type          VARCHAR(10),
    parsed_amount        NUMERIC(18, 2),
    parsed_date          DATE,
    parsed_account_id    BIGINT REFERENCES accounts(id) ON DELETE SET NULL,
    parsed_to_account_id BIGINT REFERENCES accounts(id) ON DELETE SET NULL,
    parsed_category_id   BIGINT REFERENCES categories(id) ON DELETE SET NULL,
    parsed_note          VARCHAR(255),
    dedup_hash           CHAR(64),
    status               VARCHAR(15) NOT NULL
                         CHECK (status IN ('OK', 'ERROR', 'DUPLICATE')),
    error_message        VARCHAR(500),
    UNIQUE (job_id, row_index)
);

CREATE INDEX idx_import_job_rows_job_id_status ON import_job_rows(job_id, status);
```

- [ ] **Step 1.2: Add enums**

Create `backend/src/main/java/com/financehub/domain/imports/ImportFormat.java`:

```java
package com.financehub.domain.imports;

public enum ImportFormat {
    CSV, XLSX
}
```

Create `backend/src/main/java/com/financehub/domain/imports/ImportJobStatus.java`:

```java
package com.financehub.domain.imports;

public enum ImportJobStatus {
    PENDING, COMMITTED, CANCELLED, EXPIRED
}
```

Create `backend/src/main/java/com/financehub/domain/imports/ImportJobRowStatus.java`:

```java
package com.financehub.domain.imports;

public enum ImportJobRowStatus {
    OK, ERROR, DUPLICATE
}
```

- [ ] **Step 1.3: Add `ImportJob` entity**

Create `backend/src/main/java/com/financehub/domain/imports/ImportJob.java`:

```java
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
```

- [ ] **Step 1.4: Add `ImportJobRow` entity**

Create `backend/src/main/java/com/financehub/domain/imports/ImportJobRow.java`:

```java
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
```

- [ ] **Step 1.5: Add repositories**

Create `backend/src/main/java/com/financehub/domain/imports/ImportJobRepository.java`:

```java
package com.financehub.domain.imports;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface ImportJobRepository extends JpaRepository<ImportJob, Long> {

    Optional<ImportJob> findByIdAndUserId(Long id, Long userId);

    List<ImportJob> findTop20ByUserIdOrderByIdDesc(Long userId);

    List<ImportJob> findByStatusAndExpiresAtBefore(ImportJobStatus status, OffsetDateTime when);
}
```

Create `backend/src/main/java/com/financehub/domain/imports/ImportJobRowRepository.java`:

```java
package com.financehub.domain.imports;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ImportJobRowRepository extends JpaRepository<ImportJobRow, Long> {

    List<ImportJobRow> findByJobIdOrderByRowIndexAsc(Long jobId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT r FROM ImportJobRow r
        WHERE r.jobId = :jobId AND r.status = com.financehub.domain.imports.ImportJobRowStatus.OK
        ORDER BY r.rowIndex ASC
    """)
    List<ImportJobRow> lockOkRowsByJobId(@Param("jobId") Long jobId);
}
```

- [ ] **Step 1.6: Run existing tests to confirm migration + entity validate**

Run: `cd backend && ./mvnw test -Dtest='HealthControllerTest,FinanceHubApplicationTests'`
Expected: PASS (both small tests). This confirms Flyway runs V4 cleanly and JPA validates the new entities.

- [ ] **Step 1.7: Commit (deferred until end of C1)**

Do not commit yet — staged together with T02 and T03 into commit C1.

---

## Task 2: TransactionFileParser interface + CSV parser

**Files:**
- Modify: `backend/pom.xml` — add Commons CSV dep
- Create: `backend/src/main/java/com/financehub/infrastructure/parser/RawRow.java`
- Create: `backend/src/main/java/com/financehub/infrastructure/parser/TransactionFileParser.java`
- Create: `backend/src/main/java/com/financehub/infrastructure/parser/CsvTransactionFileParser.java`
- Create: `backend/src/test/resources/imports/sample-good.csv`
- Create: `backend/src/test/resources/imports/empty-only-header.csv`
- Create: `backend/src/test/java/com/financehub/infrastructure/parser/CsvTransactionFileParserTest.java`

**Interfaces:**
- Consumes: nothing from earlier tasks (depends only on Commons CSV).
- Produces:
  - `record RawRow(int rowIndex, Map<String, String> fields)` — `rowIndex` is 1-based, counting only data rows (header is row 0). `fields` keys are header names lower-cased + trimmed.
  - `interface TransactionFileParser { ImportFormat supports(); List<RawRow> parse(InputStream in) throws IOException; }`
  - `class CsvTransactionFileParser implements TransactionFileParser` — `@Component`, registered for `CSV`.

- [ ] **Step 2.1: Add Commons CSV dependency**

Modify `backend/pom.xml`. Locate the `<dependencies>` section (right after `<dependency>` for `springdoc-openapi-starter-webmvc-ui`) and add:

```xml
        <dependency>
            <groupId>org.apache.commons</groupId>
            <artifactId>commons-csv</artifactId>
            <version>1.11.0</version>
        </dependency>
```

- [ ] **Step 2.2: Add `RawRow` record**

Create `backend/src/main/java/com/financehub/infrastructure/parser/RawRow.java`:

```java
package com.financehub.infrastructure.parser;

import java.util.Map;

public record RawRow(int rowIndex, Map<String, String> fields) {
}
```

- [ ] **Step 2.3: Add `TransactionFileParser` interface**

Create `backend/src/main/java/com/financehub/infrastructure/parser/TransactionFileParser.java`:

```java
package com.financehub.infrastructure.parser;

import com.financehub.domain.imports.ImportFormat;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public interface TransactionFileParser {

    ImportFormat supports();

    List<RawRow> parse(InputStream in) throws IOException;
}
```

- [ ] **Step 2.4: Write fixture files**

Create `backend/src/test/resources/imports/sample-good.csv` (note the UTF-8 BOM `﻿` at the start — write it literally; the file should be saved as UTF-8 with BOM):

```csv
﻿date,type,account,amount,category,to_account,note
2026-06-01,INCOME,主帳戶,30000.00,薪資,,六月薪水
2026-06-02,EXPENSE,主帳戶,250.50,飲食,,午餐
2026-06-03,EXPENSE,主帳戶,1200,交通,,捷運月票
2026-06-04,TRANSFER,主帳戶,5000.00,,副帳戶,搬錢去儲蓄
2026-06-05,INCOME,副帳戶,1500.00,投資收益,,股息
2026-06-06,EXPENSE,副帳戶,800,娛樂,,演唱會
```

Create `backend/src/test/resources/imports/empty-only-header.csv`:

```csv
date,type,account,amount,category,to_account,note
```

- [ ] **Step 2.5: Write the failing CSV parser tests**

Create `backend/src/test/java/com/financehub/infrastructure/parser/CsvTransactionFileParserTest.java`:

```java
package com.financehub.infrastructure.parser;

import com.financehub.domain.imports.ImportFormat;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CsvTransactionFileParserTest {

    private final CsvTransactionFileParser parser = new CsvTransactionFileParser();

    @Test
    void supportsCsv() {
        assertThat(parser.supports()).isEqualTo(ImportFormat.CSV);
    }

    @Test
    void parsesStandardCsvIncludingBomAndMixedLineEndings() throws Exception {
        InputStream in = getClass().getResourceAsStream("/imports/sample-good.csv");
        List<RawRow> rows = parser.parse(in);
        assertThat(rows).hasSize(6);
        RawRow first = rows.get(0);
        assertThat(first.rowIndex()).isEqualTo(1);
        assertThat(first.fields()).containsEntry("date", "2026-06-01")
                .containsEntry("type", "INCOME")
                .containsEntry("account", "主帳戶")
                .containsEntry("amount", "30000.00")
                .containsEntry("category", "薪資")
                .containsEntry("note", "六月薪水");
        assertThat(first.fields().get("to_account")).isEmpty();
    }

    @Test
    void acceptsCrlfAndLf() throws Exception {
        String csv = "date,type,account,amount,category,to_account,note\r\n"
                + "2026-06-01,INCOME,A,100,薪資,,x\r\n"
                + "2026-06-02,EXPENSE,A,50,飲食,,y\n";
        List<RawRow> rows = parser.parse(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));
        assertThat(rows).hasSize(2);
        assertThat(rows.get(1).fields().get("type")).isEqualTo("EXPENSE");
    }

    @Test
    void rejectsEmptyFile() {
        assertThatThrownBy(() -> parser.parse(new ByteArrayInputStream(new byte[0])))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Empty");
    }

    @Test
    void rejectsHeaderOnlyFileAsZeroRows() throws Exception {
        InputStream in = getClass().getResourceAsStream("/imports/empty-only-header.csv");
        List<RawRow> rows = parser.parse(in);
        assertThat(rows).isEmpty();
    }

    @Test
    void rejectsMissingRequiredColumns() {
        String csv = "date,amount\n2026-06-01,100\n";
        assertThatThrownBy(() -> parser.parse(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Missing required column");
    }
}
```

- [ ] **Step 2.6: Run tests — expect failure**

Run: `cd backend && ./mvnw test -Dtest=CsvTransactionFileParserTest`
Expected: FAIL with `CsvTransactionFileParser` not found / not compiling.

- [ ] **Step 2.7: Implement `CsvTransactionFileParser`**

Create `backend/src/main/java/com/financehub/infrastructure/parser/CsvTransactionFileParser.java`:

```java
package com.financehub.infrastructure.parser;

import com.financehub.domain.imports.ImportFormat;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class CsvTransactionFileParser implements TransactionFileParser {

    private static final Set<String> REQUIRED = Set.of("date", "type", "account", "amount");

    @Override
    public ImportFormat supports() {
        return ImportFormat.CSV;
    }

    @Override
    public List<RawRow> parse(InputStream in) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        // Strip UTF-8 BOM if present (Commons CSV does not strip by default).
        reader.mark(1);
        int first = reader.read();
        if (first != 0xFEFF) {
            reader.reset();
        }

        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .build();

        try (CSVParser csvParser = new CSVParser(reader, format)) {
            Map<String, Integer> headerMap = csvParser.getHeaderMap();
            if (headerMap == null || headerMap.isEmpty()) {
                throw new IllegalArgumentException("Empty CSV file");
            }
            Map<String, String> normalizedHeader = new HashMap<>();
            for (String h : headerMap.keySet()) {
                normalizedHeader.put(h.toLowerCase().trim(), h);
            }
            for (String required : REQUIRED) {
                if (!normalizedHeader.containsKey(required)) {
                    throw new IllegalArgumentException("Missing required column: " + required);
                }
            }

            List<RawRow> rows = new ArrayList<>();
            int rowIndex = 0;
            for (CSVRecord record : csvParser) {
                rowIndex++;
                Map<String, String> fields = new LinkedHashMap<>();
                for (Map.Entry<String, String> entry : normalizedHeader.entrySet()) {
                    String value = record.get(entry.getValue());
                    fields.put(entry.getKey(), value == null ? "" : value);
                }
                rows.add(new RawRow(rowIndex, fields));
            }
            return rows;
        }
    }
}
```

- [ ] **Step 2.8: Run tests — expect pass**

Run: `cd backend && ./mvnw test -Dtest=CsvTransactionFileParserTest`
Expected: PASS, 6/6.

- [ ] **Step 2.9: Defer commit until C1**

Do not commit — bundled with T01 + T03 into C1.

---

## Task 3: RowResolver + dedup hash

**Files:**
- Create: `backend/src/main/java/com/financehub/application/imports/ResolvedRow.java`
- Create: `backend/src/main/java/com/financehub/application/imports/RowResolver.java`
- Create: `backend/src/main/java/com/financehub/application/imports/DedupHash.java`
- Create: `backend/src/test/java/com/financehub/application/imports/RowResolverTest.java`

**Interfaces:**
- Consumes:
  - `RawRow` (from Task 2)
  - `Account` / `AccountRepository` (existing)
  - `Category` / `CategoryKind` / `CategoryRepository` (existing)
  - `TransactionType` (existing)
  - `Transaction` / `TransactionRepository` (existing) — for DB-side dedup lookup
- Produces:
  - `record ResolvedRow(int rowIndex, ImportJobRowStatus status, String errorMessage, TransactionType type, BigDecimal amount, LocalDate date, Long accountId, Long toAccountId, Long categoryId, String note, String dedupHash)`
  - `class DedupHash { static String of(Long userId, Long accountId, TransactionType type, BigDecimal amount, LocalDate date, String note) }` — SHA-256 hex.
  - `class RowResolver { ResolvedRow resolve(Long userId, RawRow row, Map<String, Long> accountByNameLower, Map<String, Long> accountByNameLowerForCurrency, Map<Long, String> currencyById, Map<String, Long> categoryByKeyLower, Set<String> existingHashes, Set<String> batchHashesSoFar) }`

  Note: `categoryByKeyLower` key format = `name.toLowerCase().trim() + "|" + kind.name()` so kind mismatch is detected as not-found.

- [ ] **Step 3.1: Write `DedupHash` utility**

Create `backend/src/main/java/com/financehub/application/imports/DedupHash.java`:

```java
package com.financehub.application.imports;

import com.financehub.domain.transaction.TransactionType;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;

public final class DedupHash {

    private DedupHash() {}

    public static String of(Long userId, Long accountId, TransactionType type,
                            BigDecimal amount, LocalDate date, String note) {
        String canonical = String.join("|",
                String.valueOf(userId),
                String.valueOf(accountId),
                type.name(),
                amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(),
                date.toString(),
                note == null ? "" : note.trim());
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
```

- [ ] **Step 3.2: Write `ResolvedRow`**

Create `backend/src/main/java/com/financehub/application/imports/ResolvedRow.java`:

```java
package com.financehub.application.imports;

import com.financehub.domain.imports.ImportJobRowStatus;
import com.financehub.domain.transaction.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ResolvedRow(
        int rowIndex,
        ImportJobRowStatus status,
        String errorMessage,
        TransactionType type,
        BigDecimal amount,
        LocalDate date,
        Long accountId,
        Long toAccountId,
        Long categoryId,
        String note,
        String dedupHash
) {
}
```

- [ ] **Step 3.3: Write failing tests for `RowResolver`**

Create `backend/src/test/java/com/financehub/application/imports/RowResolverTest.java`:

```java
package com.financehub.application.imports;

import com.financehub.domain.imports.ImportJobRowStatus;
import com.financehub.domain.transaction.TransactionType;
import com.financehub.infrastructure.parser.RawRow;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RowResolverTest {

    private static final Long USER = 1L;

    private final RowResolver resolver = new RowResolver();

    private final Map<String, Long> accountsByName = Map.of(
            "主帳戶", 10L,
            "副帳戶", 11L,
            "usd 帳", 12L);
    private final Map<Long, String> currencyById = Map.of(
            10L, "TWD",
            11L, "TWD",
            12L, "USD");
    private final Map<String, Long> categoriesByKey = Map.of(
            "薪資|INCOME", 100L,
            "飲食|EXPENSE", 101L);

    private RawRow row(Map<String, String> fields) {
        return new RawRow(1, fields);
    }

    private Map<String, String> base() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("date", "2026-06-01");
        m.put("type", "INCOME");
        m.put("account", "主帳戶");
        m.put("amount", "100.00");
        m.put("category", "薪資");
        m.put("to_account", "");
        m.put("note", "test");
        return m;
    }

    @Test
    void resolvesValidIncome() {
        ResolvedRow r = resolver.resolve(USER, row(base()),
                accountsByName, currencyById, categoriesByKey, Set.of(), new HashSet<>());
        assertThat(r.status()).isEqualTo(ImportJobRowStatus.OK);
        assertThat(r.type()).isEqualTo(TransactionType.INCOME);
        assertThat(r.amount()).isEqualByComparingTo("100.00");
        assertThat(r.accountId()).isEqualTo(10L);
        assertThat(r.categoryId()).isEqualTo(100L);
        assertThat(r.dedupHash()).hasSize(64);
    }

    @Test
    void detectsBadDate() {
        Map<String, String> f = base();
        f.put("date", "06/01/2026");
        ResolvedRow r = resolver.resolve(USER, row(f),
                accountsByName, currencyById, categoriesByKey, Set.of(), new HashSet<>());
        assertThat(r.status()).isEqualTo(ImportJobRowStatus.ERROR);
        assertThat(r.errorMessage()).contains("Invalid date");
    }

    @Test
    void detectsAccountNotFound() {
        Map<String, String> f = base();
        f.put("account", "不存在");
        ResolvedRow r = resolver.resolve(USER, row(f),
                accountsByName, currencyById, categoriesByKey, Set.of(), new HashSet<>());
        assertThat(r.status()).isEqualTo(ImportJobRowStatus.ERROR);
        assertThat(r.errorMessage()).contains("Account not found");
    }

    @Test
    void detectsCategoryKindMismatch() {
        Map<String, String> f = base();
        f.put("type", "EXPENSE");
        f.put("category", "薪資"); // INCOME-kind category attached to EXPENSE row
        ResolvedRow r = resolver.resolve(USER, row(f),
                accountsByName, currencyById, categoriesByKey, Set.of(), new HashSet<>());
        assertThat(r.status()).isEqualTo(ImportJobRowStatus.ERROR);
        assertThat(r.errorMessage()).contains("Category not found or kind mismatch");
    }

    @Test
    void detectsTransferSameAccount() {
        Map<String, String> f = base();
        f.put("type", "TRANSFER");
        f.put("category", "");
        f.put("to_account", "主帳戶");
        ResolvedRow r = resolver.resolve(USER, row(f),
                accountsByName, currencyById, categoriesByKey, Set.of(), new HashSet<>());
        assertThat(r.status()).isEqualTo(ImportJobRowStatus.ERROR);
        assertThat(r.errorMessage()).contains("source and destination must differ");
    }

    @Test
    void detectsCrossCurrencyTransfer() {
        Map<String, String> f = base();
        f.put("type", "TRANSFER");
        f.put("category", "");
        f.put("to_account", "USD 帳");
        ResolvedRow r = resolver.resolve(USER, row(f),
                accountsByName, currencyById, categoriesByKey, Set.of(), new HashSet<>());
        assertThat(r.status()).isEqualTo(ImportJobRowStatus.ERROR);
        assertThat(r.errorMessage()).contains("Cross-currency");
    }

    @Test
    void marksDuplicateAgainstExistingHash() {
        ResolvedRow first = resolver.resolve(USER, row(base()),
                accountsByName, currencyById, categoriesByKey, Set.of(), new HashSet<>());
        ResolvedRow dup = resolver.resolve(USER, row(base()),
                accountsByName, currencyById, categoriesByKey, Set.of(first.dedupHash()), new HashSet<>());
        assertThat(dup.status()).isEqualTo(ImportJobRowStatus.DUPLICATE);
    }

    @Test
    void marksDuplicateWithinBatch() {
        Set<String> batch = new HashSet<>();
        ResolvedRow first = resolver.resolve(USER, row(base()),
                accountsByName, currencyById, categoriesByKey, Set.of(), batch);
        batch.add(first.dedupHash());
        ResolvedRow dup = resolver.resolve(USER, row(base()),
                accountsByName, currencyById, categoriesByKey, Set.of(), batch);
        assertThat(dup.status()).isEqualTo(ImportJobRowStatus.DUPLICATE);
    }
}
```

- [ ] **Step 3.4: Run tests — expect failure**

Run: `cd backend && ./mvnw test -Dtest=RowResolverTest`
Expected: FAIL (class not defined).

- [ ] **Step 3.5: Implement `RowResolver`**

Create `backend/src/main/java/com/financehub/application/imports/RowResolver.java`:

```java
package com.financehub.application.imports;

import com.financehub.domain.imports.ImportJobRowStatus;
import com.financehub.domain.transaction.TransactionType;
import com.financehub.infrastructure.parser.RawRow;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Set;

@Component
public class RowResolver {

    public ResolvedRow resolve(Long userId,
                               RawRow row,
                               Map<String, Long> accountsByNameLower,
                               Map<Long, String> currencyByAccountId,
                               Map<String, Long> categoriesByKeyLower,
                               Set<String> existingDbHashes,
                               Set<String> batchHashesSoFar) {

        Map<String, String> f = row.fields();

        LocalDate date;
        try {
            String raw = f.get("date");
            if (raw == null || raw.isBlank()) {
                return err(row, "Date is required");
            }
            date = LocalDate.parse(raw.trim());
        } catch (DateTimeParseException ex) {
            return err(row, "Invalid date format: " + f.get("date"));
        }

        TransactionType type;
        try {
            String raw = f.getOrDefault("type", "").trim().toUpperCase();
            if (raw.isEmpty()) return err(row, "Type is required");
            type = TransactionType.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            return err(row, "Unknown type: " + f.get("type"));
        }

        BigDecimal amount;
        try {
            String raw = f.getOrDefault("amount", "").replace(",", "").trim();
            if (raw.isEmpty()) return err(row, "Amount is required");
            amount = new BigDecimal(raw);
            if (amount.signum() <= 0) {
                return err(row, "Amount must be positive number");
            }
            amount = amount.setScale(2, java.math.RoundingMode.HALF_UP);
        } catch (NumberFormatException ex) {
            return err(row, "Amount must be positive number");
        }

        String accountName = f.getOrDefault("account", "").trim();
        if (accountName.isEmpty()) return err(row, "Account is required");
        Long accountId = accountsByNameLower.get(accountName.toLowerCase());
        if (accountId == null) {
            return err(row, "Account not found: " + accountName);
        }

        String note = trimToMax(f.get("note"), 255);
        String toAccountRaw = f.getOrDefault("to_account", "").trim();
        String categoryRaw = f.getOrDefault("category", "").trim();

        Long toAccountId = null;
        Long categoryId = null;

        if (type.isTransfer()) {
            if (!categoryRaw.isEmpty()) {
                return err(row, "to_account only allowed for TRANSFER");
            }
            if (toAccountRaw.isEmpty()) {
                return err(row, "Transfer requires to_account");
            }
            toAccountId = accountsByNameLower.get(toAccountRaw.toLowerCase());
            if (toAccountId == null) {
                return err(row, "Account not found: " + toAccountRaw);
            }
            if (toAccountId.equals(accountId)) {
                return err(row, "Transfer source and destination must differ");
            }
            String fromCurrency = currencyByAccountId.get(accountId);
            String toCurrency = currencyByAccountId.get(toAccountId);
            if (fromCurrency != null && toCurrency != null && !fromCurrency.equals(toCurrency)) {
                return err(row, "Cross-currency transfer not supported");
            }
        } else {
            if (!toAccountRaw.isEmpty()) {
                return err(row, "to_account only allowed for TRANSFER");
            }
            if (categoryRaw.isEmpty()) {
                return err(row, "Income/expense requires a category");
            }
            String key = categoryRaw.toLowerCase() + "|" + type.name();
            categoryId = categoriesByKeyLower.get(key);
            if (categoryId == null) {
                return err(row, "Category not found or kind mismatch: " + categoryRaw);
            }
        }

        String hash = DedupHash.of(userId, accountId, type, amount, date, note);
        if (existingDbHashes.contains(hash) || batchHashesSoFar.contains(hash)) {
            return new ResolvedRow(row.rowIndex(), ImportJobRowStatus.DUPLICATE,
                    "Duplicate of existing transaction",
                    type, amount, date, accountId, toAccountId, categoryId, note, hash);
        }

        return new ResolvedRow(row.rowIndex(), ImportJobRowStatus.OK, null,
                type, amount, date, accountId, toAccountId, categoryId, note, hash);
    }

    private ResolvedRow err(RawRow row, String msg) {
        return new ResolvedRow(row.rowIndex(), ImportJobRowStatus.ERROR, msg,
                null, null, null, null, null, null, null, null);
    }

    private String trimToMax(String s, int max) {
        if (s == null) return null;
        String t = s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }
}
```

- [ ] **Step 3.6: Run tests — expect pass**

Run: `cd backend && ./mvnw test -Dtest=RowResolverTest`
Expected: PASS, 8/8.

- [ ] **Step 3.7: Commit C1**

Run:

```bash
git add \
  backend/pom.xml \
  backend/src/main/resources/db/migration/V4__import_jobs.sql \
  backend/src/main/java/com/financehub/domain/imports/ \
  backend/src/main/java/com/financehub/infrastructure/parser/ \
  backend/src/main/java/com/financehub/application/imports/DedupHash.java \
  backend/src/main/java/com/financehub/application/imports/ResolvedRow.java \
  backend/src/main/java/com/financehub/application/imports/RowResolver.java \
  backend/src/test/resources/imports/sample-good.csv \
  backend/src/test/resources/imports/empty-only-header.csv \
  backend/src/test/java/com/financehub/infrastructure/parser/CsvTransactionFileParserTest.java \
  backend/src/test/java/com/financehub/application/imports/RowResolverTest.java
git commit -m "$(cat <<'EOF'
feat(imports): add V4 staging schema, CSV parser, and row resolver

Sprint 3 foundation: stages parsed rows into import_jobs/import_job_rows
so the preview/confirm UX has somewhere to live across page reloads.
CSV parser handles BOM/CRLF + required-column checks; row resolver maps
account/category names to IDs and computes the SHA-256 dedup hash that
later commit/retry checks reuse.
EOF
)"
```

Expected: clean commit, tests still pass when run.

---

## Task 4: XLSX parser (Apache POI streaming)

**Files:**
- Modify: `backend/pom.xml` — add `poi-ooxml`
- Create: `backend/src/main/java/com/financehub/infrastructure/parser/XlsxTransactionFileParser.java`
- Create: `backend/src/test/resources/imports/sample-good.xlsx` (binary; generate in-memory in test or commit fixture from script)
- Create: `backend/src/test/java/com/financehub/infrastructure/parser/XlsxTransactionFileParserTest.java`

**Interfaces:**
- Consumes: `RawRow`, `TransactionFileParser` (from Task 2)
- Produces: `XlsxTransactionFileParser implements TransactionFileParser` — `@Component`, registered for `XLSX`. Internally uses POI streaming (`SXSSFWorkbook`-read variant via `XSSFReader`). Numeric/date cells are serialized to strings (ISO date for dates, plain decimal for numbers) so the resolver sees uniform `Map<String,String>`.

- [ ] **Step 4.1: Add POI dependency**

Modify `backend/pom.xml`. After the Commons CSV dep added in Task 2, add:

```xml
        <dependency>
            <groupId>org.apache.poi</groupId>
            <artifactId>poi-ooxml</artifactId>
            <version>5.3.0</version>
        </dependency>
```

- [ ] **Step 4.2: Write failing XLSX parser tests (generate fixture in test)**

Create `backend/src/test/java/com/financehub/infrastructure/parser/XlsxTransactionFileParserTest.java`:

```java
package com.financehub.infrastructure.parser;

import com.financehub.domain.imports.ImportFormat;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class XlsxTransactionFileParserTest {

    private final XlsxTransactionFileParser parser = new XlsxTransactionFileParser();

    @Test
    void supportsXlsx() {
        assertThat(parser.supports()).isEqualTo(ImportFormat.XLSX);
    }

    @Test
    void parsesDateAndNumericCellsAsStrings() throws Exception {
        byte[] xlsx = buildWorkbook();
        List<RawRow> rows = parser.parse(new ByteArrayInputStream(xlsx));
        assertThat(rows).hasSize(2);

        RawRow first = rows.get(0);
        assertThat(first.fields().get("date")).isEqualTo("2026-06-01");
        assertThat(first.fields().get("type")).isEqualTo("INCOME");
        assertThat(first.fields().get("account")).isEqualTo("主帳戶");
        assertThat(first.fields().get("amount")).isEqualTo("30000.00");
        assertThat(first.fields().get("category")).isEqualTo("薪資");
        assertThat(first.fields().get("to_account")).isEmpty();
        assertThat(first.fields().get("note")).isEqualTo("六月薪水");

        RawRow second = rows.get(1);
        assertThat(second.fields().get("type")).isEqualTo("TRANSFER");
        assertThat(second.fields().get("to_account")).isEqualTo("副帳戶");
        assertThat(second.fields().get("category")).isEmpty();
    }

    @Test
    void rejectsMissingRequiredColumns() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet();
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("date");
            header.createCell(1).setCellValue("amount");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);

            org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> parser.parse(new ByteArrayInputStream(out.toByteArray())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Missing required column");
        }
    }

    private byte[] buildWorkbook() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet();
            CreationHelper helper = wb.getCreationHelper();
            CellStyle dateStyle = wb.createCellStyle();
            dateStyle.setDataFormat(helper.createDataFormat().getFormat("yyyy-mm-dd"));

            String[] headers = {"date", "type", "account", "amount", "category", "to_account", "note"};
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }

            Row r1 = sheet.createRow(1);
            org.apache.poi.ss.usermodel.Cell d1 = r1.createCell(0);
            d1.setCellValue(java.sql.Date.valueOf(LocalDate.of(2026, 6, 1)));
            d1.setCellStyle(dateStyle);
            r1.createCell(1).setCellValue("INCOME");
            r1.createCell(2).setCellValue("主帳戶");
            r1.createCell(3).setCellValue(30000.0);
            r1.createCell(4).setCellValue("薪資");
            r1.createCell(5).setCellValue("");
            r1.createCell(6).setCellValue("六月薪水");

            Row r2 = sheet.createRow(2);
            org.apache.poi.ss.usermodel.Cell d2 = r2.createCell(0);
            d2.setCellValue(java.sql.Date.valueOf(LocalDate.of(2026, 6, 2)));
            d2.setCellStyle(dateStyle);
            r2.createCell(1).setCellValue("TRANSFER");
            r2.createCell(2).setCellValue("主帳戶");
            r2.createCell(3).setCellValue(5000.0);
            r2.createCell(4).setCellValue("");
            r2.createCell(5).setCellValue("副帳戶");
            r2.createCell(6).setCellValue("");

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }
}
```

- [ ] **Step 4.3: Run tests — expect failure**

Run: `cd backend && ./mvnw test -Dtest=XlsxTransactionFileParserTest`
Expected: FAIL (class not found / POI dep missing on compile).

- [ ] **Step 4.4: Implement `XlsxTransactionFileParser`**

Create `backend/src/main/java/com/financehub/infrastructure/parser/XlsxTransactionFileParser.java`:

```java
package com.financehub.infrastructure.parser;

import com.financehub.domain.imports.ImportFormat;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class XlsxTransactionFileParser implements TransactionFileParser {

    private static final Set<String> REQUIRED = Set.of("date", "type", "account", "amount");
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    @Override
    public ImportFormat supports() {
        return ImportFormat.XLSX;
    }

    @Override
    public List<RawRow> parse(InputStream in) throws IOException {
        try (Workbook wb = new XSSFWorkbook(in)) {
            Sheet sheet = wb.getSheetAt(0);
            if (sheet == null) {
                throw new IllegalArgumentException("Empty XLSX file");
            }
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new IllegalArgumentException("Empty XLSX file");
            }

            Map<String, Integer> headerIndex = new HashMap<>();
            for (int c = 0; c < headerRow.getLastCellNum(); c++) {
                Cell cell = headerRow.getCell(c);
                if (cell == null) continue;
                String name = cell.getStringCellValue().trim().toLowerCase();
                if (!name.isEmpty()) {
                    headerIndex.put(name, c);
                }
            }
            for (String required : REQUIRED) {
                if (!headerIndex.containsKey(required)) {
                    throw new IllegalArgumentException("Missing required column: " + required);
                }
            }

            List<RawRow> rows = new ArrayList<>();
            int rowIndex = 0;
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null || isBlankRow(row, headerIndex)) continue;
                rowIndex++;
                Map<String, String> fields = new LinkedHashMap<>();
                for (Map.Entry<String, Integer> entry : headerIndex.entrySet()) {
                    String value = cellToString(row.getCell(entry.getValue()));
                    fields.put(entry.getKey(), value);
                }
                rows.add(new RawRow(rowIndex, fields));
            }
            return rows;
        }
    }

    private boolean isBlankRow(Row row, Map<String, Integer> headerIndex) {
        for (Integer col : headerIndex.values()) {
            Cell cell = row.getCell(col);
            if (cell != null && cell.getCellType() != CellType.BLANK
                    && !cellToString(cell).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private String cellToString(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    LocalDate d = cell.getLocalDateTimeCellValue().toLocalDate();
                    yield d.format(ISO_DATE);
                }
                BigDecimal bd = BigDecimal.valueOf(cell.getNumericCellValue());
                // Preserve 2-decimal scale for currency-like amounts; otherwise strip trailing zeros.
                if (bd.stripTrailingZeros().scale() <= 0) {
                    yield bd.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
                }
                yield bd.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield String.valueOf(cell.getNumericCellValue());
                } catch (IllegalStateException ex) {
                    yield cell.getStringCellValue();
                }
            }
            default -> "";
        };
    }
}
```

- [ ] **Step 4.5: Run tests — expect pass**

Run: `cd backend && ./mvnw test -Dtest=XlsxTransactionFileParserTest`
Expected: PASS, 3/3.

- [ ] **Step 4.6: Run combined parser + resolver tests**

Run: `cd backend && ./mvnw test -Dtest='*Parser*Test,RowResolverTest'`
Expected: PASS (CSV 6 + XLSX 3 + Resolver 8 = 17).

- [ ] **Step 4.7: Defer commit to C2**

Do not commit yet.

---

## Task 5: ImportJobService — upload + parse + persist

**Files:**
- Create: `backend/src/main/java/com/financehub/application/imports/ImportJobService.java`
- Create: `backend/src/main/java/com/financehub/api/imports/ImportDtos.java`
- Create: `backend/src/main/java/com/financehub/api/imports/ImportController.java`
- Modify: `backend/src/main/java/com/financehub/domain/transaction/TransactionRepository.java` — add `findByUserIdAndDedupHashCandidates` helper for batch hash precheck (we compute hash for existing rows in-memory; no schema change). See step 5.3.
- Modify: `backend/src/main/resources/application.yml` — add `financehub.import.max-rows` and multipart limits

**Interfaces:**
- Consumes:
  - `TransactionFileParser`, `RawRow` (T2/T4)
  - `RowResolver`, `ResolvedRow`, `DedupHash` (T3)
  - `ImportJobRepository`, `ImportJobRowRepository`, `ImportJob`, `ImportJobRow`, enums (T1)
  - `AccountRepository`, `CategoryRepository`, `TransactionRepository` (existing)
  - `ObjectMapper` (Spring-provided bean)
- Produces:
  - `ImportJobService` Spring `@Service` with methods:
    - `ImportJob upload(Long userId, MultipartFile file)` — wraps the full parse→resolve→persist pipeline in one `@Transactional`
    - `ImportJob get(Long userId, Long jobId)` — throws `EntityNotFoundException` if not owner
    - `List<ImportJob> listRecent(Long userId)`
    - `List<ImportJobRow> getRows(Long userId, Long jobId)`
    - `void cancel(Long userId, Long jobId)` — PENDING→CANCELLED; 409 otherwise (IllegalStateException)
  - `ImportDtos` records: `ImportJobResponse`, `ImportJobRowResponse`, `ImportJobDetailResponse`, `CommitRequest`, `ImportCommitResult`
  - `ImportController` exposing `POST /imports`, `GET /imports`, `GET /imports/{id}` only in this task (commit/cancel come in Task 6/7)

- [ ] **Step 5.1: Configure multipart and import limits**

Edit `backend/src/main/resources/application.yml`. Locate the `server:` block; just before it, add a `spring.servlet.multipart` block, and append a top-level `financehub.import` block. The full edited section reads:

```yaml
spring:
  application:
    name: financehub-backend
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:dev}
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/financehub}
    username: ${DB_USERNAME:financehub}
    password: ${DB_PASSWORD:financehub_dev}
  servlet:
    multipart:
      max-file-size: 5MB
      max-request-size: 5MB
  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        jdbc:
          time_zone: UTC
        format_sql: false
    open-in-view: false
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true

# ...existing server / springdoc / logging blocks unchanged...

financehub:
  security:
    jwt:
      secret: ${JWT_SECRET:dev-secret-change-me-please-use-at-least-32-bytes-long-key-xxx}
      expiration: ${JWT_EXPIRATION:PT24H}
  import:
    max-rows: 10000
    job-ttl: PT24H
    expiry-cron: "0 0 * * * *"
```

- [ ] **Step 5.2: Add a `@ConfigurationProperties` binding**

Create `backend/src/main/java/com/financehub/application/imports/ImportProperties.java`:

```java
package com.financehub.application.imports;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "financehub.import")
public class ImportProperties {

    private int maxRows = 10000;
    private Duration jobTtl = Duration.ofHours(24);
    private String expiryCron = "0 0 * * * *";

    public int getMaxRows() { return maxRows; }
    public void setMaxRows(int v) { this.maxRows = v; }
    public Duration getJobTtl() { return jobTtl; }
    public void setJobTtl(Duration v) { this.jobTtl = v; }
    public String getExpiryCron() { return expiryCron; }
    public void setExpiryCron(String v) { this.expiryCron = v; }
}
```

Then register it. Find or create `backend/src/main/java/com/financehub/FinanceHubApplication.java` and add `@ConfigurationPropertiesScan` (or `@EnableConfigurationProperties(ImportProperties.class)`) above the class. Inspect the file first to choose the lighter change:

```bash
grep -n '@SpringBootApplication\|EnableConfigurationProperties\|ConfigurationPropertiesScan' backend/src/main/java/com/financehub/FinanceHubApplication.java
```

If neither annotation is present, add (above the existing `@SpringBootApplication`):

```java
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
// ...
@ConfigurationPropertiesScan
@SpringBootApplication
public class FinanceHubApplication {
```

- [ ] **Step 5.3: Add a hash-precheck helper on `TransactionRepository`**

`transactions` has no stored hash column. To detect dups against the DB efficiently, compute candidate hashes in memory from a narrow set of transactions that *could* match (same user, same account, same date range).

Modify `backend/src/main/java/com/financehub/domain/transaction/TransactionRepository.java` to add:

```java
    List<Transaction> findByUserIdAndTxnDateBetween(Long userId, LocalDate from, LocalDate to);
```

(Append below the existing `findByIdAndUserId` method; imports stay the same.)

- [ ] **Step 5.4: Write `ImportDtos`**

Create `backend/src/main/java/com/financehub/api/imports/ImportDtos.java`:

```java
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
```

- [ ] **Step 5.5: Write `ImportJobService`**

Create `backend/src/main/java/com/financehub/application/imports/ImportJobService.java`:

```java
package com.financehub.application.imports;

import com.financehub.domain.account.Account;
import com.financehub.domain.account.AccountRepository;
import com.financehub.domain.category.Category;
import com.financehub.domain.category.CategoryRepository;
import com.financehub.domain.imports.ImportFormat;
import com.financehub.domain.imports.ImportJob;
import com.financehub.domain.imports.ImportJobRepository;
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
            throw new IllegalArgumentException("Unsupported format: " + format);
        }

        List<RawRow> rawRows;
        try (InputStream in = file.getInputStream()) {
            rawRows = parser.parse(in);
        }
        if (rawRows.size() > properties.getMaxRows()) {
            throw new IllegalArgumentException(
                    "File exceeds maximum row count of " + properties.getMaxRows());
        }

        Long userIdLocal = userId;
        Map<String, Long> accountsByNameLower = new HashMap<>();
        Map<Long, String> currencyByAccount = new HashMap<>();
        for (Account a : accountRepository.findByUserIdOrderByIdAsc(userIdLocal)) {
            accountsByNameLower.put(a.getName().toLowerCase(), a.getId());
            currencyByAccount.put(a.getId(), a.getCurrency());
        }
        Map<String, Long> categoriesByKey = new HashMap<>();
        for (Category c : categoryRepository.findVisibleTo(userIdLocal)) {
            String key = c.getName().toLowerCase() + "|" + c.getKind().name();
            categoriesByKey.put(key, c.getId());
        }

        Set<String> existingHashes = computeExistingHashes(userIdLocal, rawRows, accountsByNameLower);
        Set<String> batchHashes = new HashSet<>();

        ImportJob job = new ImportJob(userIdLocal, file.getOriginalFilename(), format);
        job = jobRepository.save(job);

        int okCount = 0, errorCount = 0, dupCount = 0;
        for (RawRow raw : rawRows) {
            ResolvedRow resolved = rowResolver.resolve(userIdLocal, raw,
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

    private ImportFormat detectFormat(String filename) {
        if (filename == null) throw new IllegalArgumentException("Filename required");
        String lower = filename.toLowerCase();
        if (lower.endsWith(".csv")) return ImportFormat.CSV;
        if (lower.endsWith(".xlsx")) return ImportFormat.XLSX;
        throw new UnsupportedFormatException("Unsupported extension: " + filename);
    }

    private Set<String> computeExistingHashes(Long userId, List<RawRow> rows,
                                              Map<String, Long> accountsByNameLower) {
        Set<LocalDate> dates = rows.stream()
                .map(r -> tryParseDate(r.fields().get("date")))
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
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
```

- [ ] **Step 5.6: Add `UnsupportedFormatException` with `@ResponseStatus(415)`**

Create `backend/src/main/java/com/financehub/application/imports/UnsupportedFormatException.java`:

```java
package com.financehub.application.imports;

public class UnsupportedFormatException extends RuntimeException {
    public UnsupportedFormatException(String message) {
        super(message);
    }
}
```

Register a handler in `backend/src/main/java/com/financehub/api/common/GlobalExceptionHandler.java`. Add inside the class:

```java
    @org.springframework.web.bind.annotation.ExceptionHandler(
            com.financehub.application.imports.UnsupportedFormatException.class)
    public org.springframework.http.ResponseEntity<ApiError> handleUnsupportedFormat(
            com.financehub.application.imports.UnsupportedFormatException ex) {
        return org.springframework.http.ResponseEntity
                .status(org.springframework.http.HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ApiError.of("unsupported_format", ex.getMessage()));
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(IllegalStateException.class)
    public org.springframework.http.ResponseEntity<ApiError> handleConflict(IllegalStateException ex) {
        return org.springframework.http.ResponseEntity
                .status(org.springframework.http.HttpStatus.CONFLICT)
                .body(ApiError.of("conflict", ex.getMessage()));
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(
            org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public org.springframework.http.ResponseEntity<ApiError> handlePayloadTooLarge(
            org.springframework.web.multipart.MaxUploadSizeExceededException ex) {
        return org.springframework.http.ResponseEntity
                .status(org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiError.of("payload_too_large", "File exceeds maximum size"));
    }
```

- [ ] **Step 5.7: Write `ImportController` upload/get/list endpoints**

Create `backend/src/main/java/com/financehub/api/imports/ImportController.java`:

```java
package com.financehub.api.imports;

import com.financehub.application.imports.ImportJobService;
import com.financehub.domain.imports.ImportJob;
import com.financehub.security.AuthenticatedUser;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/imports")
public class ImportController {

    private final ImportJobService importJobService;

    public ImportController(ImportJobService importJobService) {
        this.importJobService = importJobService;
    }

    @PostMapping
    public ResponseEntity<ImportDtos.ImportJobResponse> upload(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam("file") MultipartFile file) throws IOException {
        ImportJob job = importJobService.upload(user.id(), file);
        return ResponseEntity.status(201).body(ImportDtos.ImportJobResponse.from(job));
    }

    @GetMapping
    public List<ImportDtos.ImportJobResponse> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return importJobService.listRecent(user.id()).stream()
                .map(ImportDtos.ImportJobResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public ImportDtos.ImportJobDetailResponse get(@AuthenticationPrincipal AuthenticatedUser user,
                                                  @PathVariable Long id) {
        ImportJob job = importJobService.get(user.id(), id);
        var rows = importJobService.getRows(user.id(), id).stream()
                .map(ImportDtos.ImportJobRowResponse::from)
                .toList();
        return new ImportDtos.ImportJobDetailResponse(
                ImportDtos.ImportJobResponse.from(job), rows);
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Void> cancel(@AuthenticationPrincipal AuthenticatedUser user,
                                       @PathVariable Long id) {
        importJobService.cancel(user.id(), id);
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 5.8: Update Spring Security to permit multipart uploads**

Inspect the existing `SecurityConfig` (or whatever wires HTTP security):

```bash
grep -rn 'SecurityFilterChain\|HttpSecurity' backend/src/main/java/com/financehub/ | head
```

The existing transactions endpoint is already behind JWT, so no path change is needed — the new `/api/v1/imports/**` is automatically covered by any catch-all `.anyRequest().authenticated()` clause. Run the existing tests to confirm.

- [ ] **Step 5.9: Write `ImportJobIT` (upload + read tests, no commit yet)**

Add fixture files. Create `backend/src/test/resources/imports/sample-mixed.csv` (UTF-8, no BOM):

```csv
date,type,account,amount,category,to_account,note
2026-06-01,INCOME,主帳戶,30000.00,薪資,,六月薪水
2026-06-02,EXPENSE,主帳戶,250.50,飲食,,午餐
2026-06-03,EXPENSE,主帳戶,1200,交通,,捷運月票
06/01/2026,INCOME,主帳戶,100,薪資,,bad date row
2026-06-05,INCOME,不存在,500,薪資,,bad account row
2026-06-02,EXPENSE,主帳戶,250.50,飲食,,午餐
```
(Row 6 is a textual duplicate of row 2 → DUPLICATE.)

Create `backend/src/test/java/com/financehub/api/imports/ImportJobIT.java`:

```java
package com.financehub.api.imports;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.financehub.support.PostgresTestcontainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = PostgresTestcontainer.class)
class ImportJobIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    private String register(String email) throws Exception {
        String resp = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email, "name", "Tester", "password", "Tester1234!"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + objectMapper.readTree(resp).get("token").asText();
    }

    private Long createAccount(String bearer, String name, String currency, String balance) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("type", "SAVING");
        body.put("currency", currency);
        body.put("initialBalance", balance);
        String resp = mockMvc.perform(post("/api/v1/accounts")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).get("id").asLong();
    }

    private byte[] readFixture(String name) throws Exception {
        try (var in = getClass().getResourceAsStream("/imports/" + name)) {
            return in.readAllBytes();
        }
    }

    @Test
    void uploadParsesMixedFileAndCountsCorrectly() throws Exception {
        String bearer = register("imp+" + System.nanoTime() + "@example.com");
        createAccount(bearer, "主帳戶", "TWD", "0.00");
        // create the existing-DB transaction that the duplicate row should match
        // (date 2026-06-02, account 主帳戶, amount 250.50, category 飲食, note 午餐)
        Long catFood = pickCategoryByNameKind(bearer, "飲食", "EXPENSE");
        Long acc = lookupAccountId(bearer, "主帳戶");
        Map<String, Object> txn = new HashMap<>();
        txn.put("accountId", acc);
        txn.put("categoryId", catFood);
        txn.put("type", "EXPENSE");
        txn.put("amount", "250.50");
        txn.put("txnDate", "2026-06-02");
        txn.put("note", "午餐");
        mockMvc.perform(post("/api/v1/transactions")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(txn)))
                .andExpect(status().isCreated());

        byte[] bytes = readFixture("sample-mixed.csv");
        MockMultipartFile file = new MockMultipartFile(
                "file", "sample-mixed.csv", "text/csv", bytes);

        String resp = mockMvc.perform(multipart("/api/v1/imports")
                        .file(file)
                        .header("Authorization", bearer))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rowCount").value(6))
                .andExpect(jsonPath("$.errorCount").value(2)) // bad date + bad account
                .andExpect(jsonPath("$.dupCount").value(2))   // 2 vs DB + 6 vs 2 in batch
                .andExpect(jsonPath("$.okCount").value(2))
                .andReturn().getResponse().getContentAsString();
        long jobId = objectMapper.readTree(resp).get("id").asLong();

        mockMvc.perform(get("/api/v1/imports/" + jobId)
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.job.id").value(jobId))
                .andExpect(jsonPath("$.rows.length()").value(6));
    }

    @Test
    void unsupportedExtensionReturns415() throws Exception {
        String bearer = register("ext+" + System.nanoTime() + "@example.com");
        MockMultipartFile file = new MockMultipartFile(
                "file", "bad.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/v1/imports")
                        .file(file)
                        .header("Authorization", bearer))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void getOtherUsersJobReturns404() throws Exception {
        String alice = register("alice+" + System.nanoTime() + "@example.com");
        String bob = register("bob+" + System.nanoTime() + "@example.com");
        createAccount(alice, "主帳戶", "TWD", "0.00");

        byte[] bytes = readFixture("sample-mixed.csv");
        MockMultipartFile file = new MockMultipartFile(
                "file", "sample-mixed.csv", "text/csv", bytes);
        String resp = mockMvc.perform(multipart("/api/v1/imports")
                        .file(file)
                        .header("Authorization", alice))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long jobId = objectMapper.readTree(resp).get("id").asLong();

        mockMvc.perform(get("/api/v1/imports/" + jobId)
                        .header("Authorization", bob))
                .andExpect(status().isNotFound());
    }

    @Test
    void unauthenticatedReturns401() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "x.csv", "text/csv", "date,type,account,amount\n".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/v1/imports").file(file))
                .andExpect(status().isUnauthorized());
    }

    private Long pickCategoryByNameKind(String bearer, String name, String kind) throws Exception {
        String resp = mockMvc.perform(get("/api/v1/categories")
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        for (JsonNode node : objectMapper.readTree(resp)) {
            if (name.equals(node.get("name").asText()) && kind.equals(node.get("kind").asText())) {
                return node.get("id").asLong();
            }
        }
        throw new AssertionError("No category " + name + "/" + kind);
    }

    private Long lookupAccountId(String bearer, String name) throws Exception {
        String resp = mockMvc.perform(get("/api/v1/accounts")
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        for (JsonNode node : objectMapper.readTree(resp)) {
            if (name.equals(node.get("name").asText())) {
                return node.get("id").asLong();
            }
        }
        throw new AssertionError("Account " + name + " not found");
    }
}
```

- [ ] **Step 5.10: Run tests**

Run: `cd backend && ./mvnw test -Dtest=ImportJobIT`
Expected: PASS, 4/4. If the duplicate-count assertion fails because of off-by-one between DB-side hash and in-batch hash, double-check that `DedupHash.of` is called with identical scale (`setScale(2, HALF_UP)`) in both `ImportJobService.computeExistingHashes` and `RowResolver`.

- [ ] **Step 5.11: Defer commit until end of T05 (still C2)**

C2 will land after T04 + T05; commit at end of T05.

- [ ] **Step 5.12: Commit C2**

```bash
git add \
  backend/pom.xml \
  backend/src/main/resources/application.yml \
  backend/src/main/java/com/financehub/FinanceHubApplication.java \
  backend/src/main/java/com/financehub/api/common/GlobalExceptionHandler.java \
  backend/src/main/java/com/financehub/api/imports/ \
  backend/src/main/java/com/financehub/application/imports/ \
  backend/src/main/java/com/financehub/domain/transaction/TransactionRepository.java \
  backend/src/main/java/com/financehub/infrastructure/parser/XlsxTransactionFileParser.java \
  backend/src/test/java/com/financehub/infrastructure/parser/XlsxTransactionFileParserTest.java \
  backend/src/test/resources/imports/sample-mixed.csv \
  backend/src/test/java/com/financehub/api/imports/ImportJobIT.java
git commit -m "$(cat <<'EOF'
feat(imports): add XLSX parser and import-job upload pipeline

XLSX support keeps Sprint 3 within the original scope; both parsers feed
into the same RowResolver so dedup, account/category resolution, and the
import_jobs persistence flow are identical regardless of input format.
DB-side dup detection narrows by date range and recomputes hashes from
existing transactions, avoiding a new column on the transactions table.
EOF
)"
```

---

## Task 6: Read API hardening + list endpoint

Already largely covered by Task 5. This task is a focused review pass plus the test for cross-user 404 + unauthenticated 401 + listing.

**Files:**
- Modify: `backend/src/test/java/com/financehub/api/imports/ImportJobIT.java` — add list test
- No source changes if Task 5 wired everything correctly

- [ ] **Step 6.1: Add list test**

In `ImportJobIT`, add:

```java
    @Test
    void listReturnsOnlyOwnJobs() throws Exception {
        String alice = register("listA+" + System.nanoTime() + "@example.com");
        String bob = register("listB+" + System.nanoTime() + "@example.com");
        createAccount(alice, "主帳戶", "TWD", "0.00");
        createAccount(bob, "主帳戶", "TWD", "0.00");

        byte[] bytes = readFixture("sample-mixed.csv");
        mockMvc.perform(multipart("/api/v1/imports")
                .file(new MockMultipartFile("file", "a.csv", "text/csv", bytes))
                .header("Authorization", alice))
                .andExpect(status().isCreated());
        mockMvc.perform(multipart("/api/v1/imports")
                .file(new MockMultipartFile("file", "b.csv", "text/csv", bytes))
                .header("Authorization", bob))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/imports").header("Authorization", alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].filename").value("a.csv"));
    }
```

- [ ] **Step 6.2: Run**

Run: `cd backend && ./mvnw test -Dtest=ImportJobIT`
Expected: PASS, 5/5.

- [ ] **Step 6.3: Stage but commit at end of C3 (after T07–T09)**

---

## Task 7: Commit / Cancel — re-resolve + delegate to TransactionService

**Files:**
- Create: `backend/src/main/java/com/financehub/application/imports/ImportCommitter.java`
- Modify: `backend/src/main/java/com/financehub/api/imports/ImportController.java` — wire `POST /{id}/commit`
- Modify: `backend/src/main/java/com/financehub/application/imports/ImportJobService.java` — delegate commit to `ImportCommitter`
- Create: `backend/src/test/java/com/financehub/api/imports/ImportCommitIT.java`

**Interfaces:**
- Consumes: `TransactionService.create` (existing) — single insertion path, including dual-balance for TRANSFER
- Produces:
  - `ImportCommitter` `@Service` with `ImportCommitResult commit(Long userId, Long jobId, List<Long> rowIds)`
  - `ImportCommitResult = (Long jobId, int committedCount, List<Long> transactionIds)` (already defined in `ImportDtos`)

- [ ] **Step 7.1: Write failing commit tests**

Create `backend/src/test/java/com/financehub/api/imports/ImportCommitIT.java`:

```java
package com.financehub.api.imports;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.financehub.support.PostgresTestcontainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = PostgresTestcontainer.class)
class ImportCommitIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    private String register(String email) throws Exception {
        String resp = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email, "name", "Tester", "password", "Tester1234!"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + objectMapper.readTree(resp).get("token").asText();
    }

    private Long createAccount(String bearer, String name, String currency, String balance) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("type", "SAVING");
        body.put("currency", currency);
        body.put("initialBalance", balance);
        String resp = mockMvc.perform(post("/api/v1/accounts")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).get("id").asLong();
    }

    private long uploadGoodFile(String bearer) throws Exception {
        String csv = "date,type,account,amount,category,to_account,note\n"
                + "2026-06-01,INCOME,主帳戶,30000.00,薪資,,六月薪水\n"
                + "2026-06-02,EXPENSE,主帳戶,250.50,飲食,,午餐\n"
                + "2026-06-03,TRANSFER,主帳戶,5000.00,,副帳戶,搬錢\n";
        MockMultipartFile file = new MockMultipartFile("file", "good.csv", "text/csv", csv.getBytes());
        String resp = mockMvc.perform(multipart("/api/v1/imports")
                        .file(file).header("Authorization", bearer))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).get("id").asLong();
    }

    private double balance(String bearer, Long accId) throws Exception {
        String resp = mockMvc.perform(get("/api/v1/accounts/" + accId)
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).get("currentBalance").asDouble();
    }

    @Test
    void commitInsertsTransactionsAndAdjustsBalances() throws Exception {
        String bearer = register("c1+" + System.nanoTime() + "@example.com");
        Long from = createAccount(bearer, "主帳戶", "TWD", "10000.00");
        Long to = createAccount(bearer, "副帳戶", "TWD", "0.00");

        long jobId = uploadGoodFile(bearer);

        mockMvc.perform(post("/api/v1/imports/" + jobId + "/commit")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.committedCount").value(3));

        // 10000 + 30000 (INCOME) - 250.50 (EXPENSE) - 5000 (TRANSFER from) = 34749.50
        org.assertj.core.api.Assertions.assertThat(balance(bearer, from)).isEqualTo(34749.50);
        // 0 + 5000 (TRANSFER to)
        org.assertj.core.api.Assertions.assertThat(balance(bearer, to)).isEqualTo(5000.00);
    }

    @Test
    void commitOnlySelectedRows() throws Exception {
        String bearer = register("c2+" + System.nanoTime() + "@example.com");
        Long acc = createAccount(bearer, "主帳戶", "TWD", "0.00");
        createAccount(bearer, "副帳戶", "TWD", "0.00");
        long jobId = uploadGoodFile(bearer);

        // fetch rows to pick the first OK row id
        String detail = mockMvc.perform(get("/api/v1/imports/" + jobId)
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long firstRowId = -1;
        for (JsonNode r : objectMapper.readTree(detail).get("rows")) {
            if ("OK".equals(r.get("status").asText())) {
                firstRowId = r.get("id").asLong();
                break;
            }
        }
        org.assertj.core.api.Assertions.assertThat(firstRowId).isNotEqualTo(-1);

        Map<String, Object> body = Map.of("rowIds", List.of(firstRowId));
        mockMvc.perform(post("/api/v1/imports/" + jobId + "/commit")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.committedCount").value(1));

        org.assertj.core.api.Assertions.assertThat(balance(bearer, acc)).isEqualTo(30000.00);
    }

    @Test
    void commitTwiceReturnsConflict() throws Exception {
        String bearer = register("c3+" + System.nanoTime() + "@example.com");
        createAccount(bearer, "主帳戶", "TWD", "0.00");
        createAccount(bearer, "副帳戶", "TWD", "0.00");
        long jobId = uploadGoodFile(bearer);

        mockMvc.perform(post("/api/v1/imports/" + jobId + "/commit")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/imports/" + jobId + "/commit")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict());
    }

    @Test
    void commitWithDeletedAccountReResolvesAndReturnsConflict() throws Exception {
        String bearer = register("c4+" + System.nanoTime() + "@example.com");
        Long from = createAccount(bearer, "主帳戶", "TWD", "0.00");
        Long to = createAccount(bearer, "副帳戶", "TWD", "0.00");
        long jobId = uploadGoodFile(bearer);

        // delete one of the accounts used by parsed rows
        mockMvc.perform(delete("/api/v1/accounts/" + to)
                        .header("Authorization", bearer))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/imports/" + jobId + "/commit")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict());
    }

    @Test
    void cancelMovesJobToCancelledAndBlocksCommit() throws Exception {
        String bearer = register("c5+" + System.nanoTime() + "@example.com");
        createAccount(bearer, "主帳戶", "TWD", "0.00");
        createAccount(bearer, "副帳戶", "TWD", "0.00");
        long jobId = uploadGoodFile(bearer);

        mockMvc.perform(post("/api/v1/imports/" + jobId + "/cancel")
                        .header("Authorization", bearer))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/imports/" + jobId + "/commit")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict());
    }
}
```

- [ ] **Step 7.2: Run — expect failure**

Run: `cd backend && ./mvnw test -Dtest=ImportCommitIT`
Expected: FAIL (no `/{id}/commit` endpoint or no committer behavior).

- [ ] **Step 7.3: Implement `ImportCommitter`**

Create `backend/src/main/java/com/financehub/application/imports/ImportCommitter.java`:

```java
package com.financehub.application.imports;

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
import com.financehub.api.imports.ImportDtos.ImportCommitResult;
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
            // recompute counts on the job and bail with 409
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
        if (row.getParsedType() == com.financehub.domain.transaction.TransactionType.TRANSFER) {
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
```

- [ ] **Step 7.4: Wire `/commit` in controller and delegate**

Modify `ImportController` to add:

```java
    @PostMapping("/{id}/commit")
    public ImportDtos.ImportCommitResult commit(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id,
            @org.springframework.web.bind.annotation.RequestBody(required = false) ImportDtos.CommitRequest request) {
        java.util.List<Long> rowIds = request == null ? null : request.rowIds();
        return importCommitter.commit(user.id(), id, rowIds);
    }
```

Add a constructor parameter `ImportCommitter importCommitter` and field:

```java
    private final ImportCommitter importCommitter;

    public ImportController(ImportJobService importJobService, ImportCommitter importCommitter) {
        this.importJobService = importJobService;
        this.importCommitter = importCommitter;
    }
```

Add the import: `import com.financehub.application.imports.ImportCommitter;`

- [ ] **Step 7.5: Run tests — expect pass**

Run: `cd backend && ./mvnw test -Dtest=ImportCommitIT`
Expected: PASS, 5/5.

- [ ] **Step 7.6: Defer commit until T09 (still C3)**

---

## Task 8: ExpiryScheduler

**Files:**
- Create: `backend/src/main/java/com/financehub/application/imports/ImportExpiryJob.java`
- Modify: `backend/src/main/java/com/financehub/FinanceHubApplication.java` — add `@EnableScheduling`
- Add to `ImportCommitIT.java`: case 21 (expiry)

**Interfaces:**
- Consumes: `ImportJobRepository`, `ImportProperties`
- Produces: `ImportExpiryJob` with `@Scheduled(cron = "${financehub.import.expiry-cron}")` `markExpired()` plus a `package-private void expireOnce(OffsetDateTime now)` for testing.

- [ ] **Step 8.1: Add `@EnableScheduling`**

Modify `backend/src/main/java/com/financehub/FinanceHubApplication.java`:

```java
import org.springframework.scheduling.annotation.EnableScheduling;
// ...
@EnableScheduling
@ConfigurationPropertiesScan
@SpringBootApplication
public class FinanceHubApplication {
```

- [ ] **Step 8.2: Implement `ImportExpiryJob`**

Create `backend/src/main/java/com/financehub/application/imports/ImportExpiryJob.java`:

```java
package com.financehub.application.imports;

import com.financehub.domain.imports.ImportJob;
import com.financehub.domain.imports.ImportJobRepository;
import com.financehub.domain.imports.ImportJobStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Component
public class ImportExpiryJob {

    private final ImportJobRepository repository;

    public ImportExpiryJob(ImportJobRepository repository) {
        this.repository = repository;
    }

    @Scheduled(cron = "${financehub.import.expiry-cron}")
    public void markExpired() {
        expireOnce(OffsetDateTime.now());
    }

    @Transactional
    void expireOnce(OffsetDateTime now) {
        List<ImportJob> stale = repository.findByStatusAndExpiresAtBefore(
                ImportJobStatus.PENDING, now);
        for (ImportJob job : stale) {
            job.setStatus(ImportJobStatus.EXPIRED);
        }
    }
}
```

- [ ] **Step 8.3: Add expiry IT case**

Append to `ImportCommitIT.java`:

```java
    @org.springframework.beans.factory.annotation.Autowired
    private com.financehub.application.imports.ImportExpiryJob expiryJob;

    @Test
    void expirySchedulerMarksStaleJobsExpired() throws Exception {
        String bearer = register("c6+" + System.nanoTime() + "@example.com");
        createAccount(bearer, "主帳戶", "TWD", "0.00");
        createAccount(bearer, "副帳戶", "TWD", "0.00");
        long jobId = uploadGoodFile(bearer);

        // simulate "25h later"
        expiryJob.expireOnce(java.time.OffsetDateTime.now().plusHours(25));

        mockMvc.perform(get("/api/v1/imports/" + jobId)
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.job.status").value("EXPIRED"));
    }
```

- [ ] **Step 8.4: Run**

Run: `cd backend && ./mvnw test -Dtest=ImportCommitIT`
Expected: PASS, 6/6.

- [ ] **Step 8.5: Defer commit until T09 (still C3)**

---

## Task 9: Multipart 413 + row-count 400

**Files:**
- Create: `backend/src/test/resources/imports/sample-large.csv` — 10001 rows
- Add to `ImportJobIT`: 413 and 400 tests

**Interfaces:**
- Consumes: existing service + global handler
- Produces: no new code; relies on `MaxUploadSizeExceededException` handler (added in T05) and `IllegalArgumentException` → 400 (already in handler).

- [ ] **Step 9.1: Generate the large fixture programmatically**

Easier than committing 10001 lines: build the fixture in the test class. Append to `ImportJobIT.java`:

```java
    @Test
    void tooManyRowsReturns400() throws Exception {
        String bearer = register("big+" + System.nanoTime() + "@example.com");
        createAccount(bearer, "主帳戶", "TWD", "0.00");

        StringBuilder csv = new StringBuilder("date,type,account,amount,category,to_account,note\n");
        // 10001 data rows
        for (int i = 0; i < 10001; i++) {
            csv.append("2026-06-01,INCOME,主帳戶,1.00,薪資,,row").append(i).append('\n');
        }
        MockMultipartFile file = new MockMultipartFile(
                "file", "big.csv", "text/csv", csv.toString().getBytes());

        mockMvc.perform(multipart("/api/v1/imports")
                        .file(file).header("Authorization", bearer))
                .andExpect(status().isBadRequest());
    }
```

For the 413 case the Servlet container needs to reject before reaching the controller. Skip a synthetic 413 IT case (would need to override max-file-size to a tiny value for a single test; not worth the wiring) — covered by manual smoke instead. The `MaxUploadSizeExceededException` handler is still in place for real-world use.

- [ ] **Step 9.2: Run**

Run: `cd backend && ./mvnw test -Dtest=ImportJobIT`
Expected: PASS, 6/6 (5 prior + new 400).

- [ ] **Step 9.3: Run full backend suite**

Run: `cd backend && ./mvnw test`
Expected: all green. Total: 20 prior IT + 19 new IT + 17 unit (CSV 6 + XLSX 3 + Resolver 8) = matches design (39 IT, 17 unit). Adjust if any count is off — investigate before continuing.

- [ ] **Step 9.4: Commit C3**

```bash
git add \
  backend/src/main/java/com/financehub/FinanceHubApplication.java \
  backend/src/main/java/com/financehub/api/imports/ImportController.java \
  backend/src/main/java/com/financehub/application/imports/ImportCommitter.java \
  backend/src/main/java/com/financehub/application/imports/ImportExpiryJob.java \
  backend/src/test/java/com/financehub/api/imports/ImportCommitIT.java \
  backend/src/test/java/com/financehub/api/imports/ImportJobIT.java
git commit -m "$(cat <<'EOF'
feat(imports): add commit, cancel, expiry, and row-limit guards

Commit re-resolves account/category ownership before delegating to
TransactionService so a row that became invalid mid-preview (e.g. its
account was deleted) flips to ERROR atomically rather than producing
broken state. Expiry sweeps PENDING jobs past 24h so stale rows do not
accumulate; row-count guard surfaces a clean 400 before the parser does
any work.
EOF
)"
```

---

## Task 10: Frontend — types + API client + Upload page (skeleton)

**Files:**
- Create: `frontend/src/types/import.ts`
- Create: `frontend/src/api/imports.ts`
- Create: `frontend/src/pages/ImportPage.tsx` (Upload-only skeleton — table comes in T11)
- Modify: `frontend/src/App.tsx` — add route
- Modify: `frontend/src/components/AppLayout.tsx` — add nav entry

**Interfaces:**
- Consumes: existing `apiClient` (`frontend/src/api/client.ts`)
- Produces:
  - `ImportFormat`, `ImportJobStatus`, `ImportJobRowStatus` literal-union types
  - `ImportJob`, `ImportJobRow`, `ImportJobDetail`, `ImportCommitResult` interfaces
  - `uploadImport(file: File) → Promise<ImportJob>`
  - `getImport(id: number) → Promise<ImportJobDetail>`
  - `commitImport(id: number, rowIds?: number[]) → Promise<ImportCommitResult>`
  - `cancelImport(id: number) → Promise<void>`

- [ ] **Step 10.1: Add `frontend/src/types/import.ts`**

```ts
import type { TransactionType } from './transaction'

export type ImportFormat = 'CSV' | 'XLSX'
export type ImportJobStatus = 'PENDING' | 'COMMITTED' | 'CANCELLED' | 'EXPIRED'
export type ImportJobRowStatus = 'OK' | 'ERROR' | 'DUPLICATE'

export interface ImportJob {
  id: number
  filename: string
  format: ImportFormat
  status: ImportJobStatus
  rowCount: number
  okCount: number
  errorCount: number
  dupCount: number
  createdAt: string
  committedAt: string | null
  expiresAt: string
}

export interface ImportJobRow {
  id: number
  rowIndex: number
  status: ImportJobRowStatus
  errorMessage: string | null
  rawJson: string
  parsedType: TransactionType | null
  parsedAmount: number | null
  parsedDate: string | null
  parsedAccountId: number | null
  parsedToAccountId: number | null
  parsedCategoryId: number | null
  parsedNote: string | null
}

export interface ImportJobDetail {
  job: ImportJob
  rows: ImportJobRow[]
}

export interface ImportCommitResult {
  jobId: number
  committedCount: number
  transactionIds: number[]
}
```

- [ ] **Step 10.2: Add `frontend/src/api/imports.ts`**

```ts
import { apiClient } from './client'
import type {
  ImportCommitResult,
  ImportJob,
  ImportJobDetail,
} from '@/types/import'

export async function uploadImport(file: File): Promise<ImportJob> {
  const form = new FormData()
  form.append('file', file)
  const { data } = await apiClient.post<ImportJob>('/api/v1/imports', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
  return data
}

export async function getImport(id: number): Promise<ImportJobDetail> {
  const { data } = await apiClient.get<ImportJobDetail>(`/api/v1/imports/${id}`)
  return data
}

export async function commitImport(
  id: number,
  rowIds?: number[],
): Promise<ImportCommitResult> {
  const { data } = await apiClient.post<ImportCommitResult>(
    `/api/v1/imports/${id}/commit`,
    { rowIds: rowIds && rowIds.length > 0 ? rowIds : null },
  )
  return data
}

export async function cancelImport(id: number): Promise<void> {
  await apiClient.post(`/api/v1/imports/${id}/cancel`)
}
```

- [ ] **Step 10.3: Add `ImportPage.tsx` upload-only skeleton**

Create `frontend/src/pages/ImportPage.tsx`:

```tsx
import { useState } from 'react'
import { Card, Space, Typography, Upload, message } from 'antd'
import { InboxOutlined } from '@ant-design/icons'
import { useMutation } from '@tanstack/react-query'
import type { UploadProps } from 'antd'
import { uploadImport } from '@/api/imports'
import type { ImportJob } from '@/types/import'

const { Title, Paragraph, Text } = Typography
const { Dragger } = Upload

export function ImportPage() {
  const [activeJob, setActiveJob] = useState<ImportJob | null>(null)

  const uploadMutation = useMutation({
    mutationFn: (file: File) => uploadImport(file),
    onSuccess: (job) => {
      message.success(`已建立匯入工作 #${job.id}`)
      setActiveJob(job)
    },
    onError: (err) => {
      message.error((err as Error).message ?? '上傳失敗')
    },
  })

  const uploadProps: UploadProps = {
    multiple: false,
    accept: '.csv,.xlsx',
    showUploadList: false,
    customRequest: ({ file, onSuccess, onError }) => {
      uploadMutation.mutate(file as File, {
        onSuccess: (job) => onSuccess?.(job),
        onError: (err) => onError?.(err as Error),
      })
    },
  }

  return (
    <Space direction="vertical" size={24} style={{ width: '100%' }}>
      <Card>
        <Title level={3}>匯入交易</Title>
        <Paragraph>
          支援 <Text code>.csv</Text> 與 <Text code>.xlsx</Text>，每檔最多 10000 列，5 MB 以內。
        </Paragraph>
        <Paragraph type="secondary">
          期望欄位：<Text code>date, type, account, amount, category, to_account, note</Text>。
        </Paragraph>
        <Dragger {...uploadProps} disabled={uploadMutation.isPending}>
          <p className="ant-upload-drag-icon"><InboxOutlined /></p>
          <p className="ant-upload-text">點擊或拖放檔案到此處上傳</p>
          <p className="ant-upload-hint">上傳後會進入預覽，再按確認才會匯入。</p>
        </Dragger>
      </Card>

      {activeJob && (
        <Card title={`工作 #${activeJob.id} — ${activeJob.filename}`}>
          <Text>
            列數 {activeJob.rowCount} ／ OK {activeJob.okCount} ／ 錯誤 {activeJob.errorCount} ／ 重複 {activeJob.dupCount}
          </Text>
        </Card>
      )}
    </Space>
  )
}
```

- [ ] **Step 10.4: Wire route and nav**

Edit `frontend/src/App.tsx` — within the `<Route element={<AppLayout />}>` block, add:

```tsx
                <Route path="/import" element={<ImportPage />} />
```

And add the import at the top of the file: `import { ImportPage } from '@/pages/ImportPage'`.

Edit `frontend/src/components/AppLayout.tsx`. Import the icon:

```tsx
import { LogoutOutlined, BankOutlined, SwapOutlined, ImportOutlined } from '@ant-design/icons'
```

Add the menu item entry after the `/transactions` entry:

```tsx
            {
              key: '/import',
              icon: <ImportOutlined />,
              label: <Link to="/import">匯入</Link>,
            },
```

- [ ] **Step 10.5: Run frontend lint + typecheck + build**

```bash
cd frontend
npm run typecheck
npm run lint
npm run build
```

Expected: all green.

- [ ] **Step 10.6: Defer commit to end of T11 (C4)**

---

## Task 11: Frontend — Preview Table + Commit/Cancel

**Files:**
- Modify: `frontend/src/pages/ImportPage.tsx` — wire preview + commit/cancel actions

**Interfaces:**
- Consumes: T10 API client + types
- Produces: full page UI

- [ ] **Step 11.1: Replace `ImportPage.tsx` with the full version**

Overwrite `frontend/src/pages/ImportPage.tsx` with:

```tsx
import { useMemo, useState } from 'react'
import {
  Button, Card, Popconfirm, Space, Statistic, Table, Tag, Typography, Upload, message,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { InboxOutlined } from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import {
  cancelImport, commitImport, getImport, uploadImport,
} from '@/api/imports'
import type {
  ImportJob, ImportJobRow, ImportJobRowStatus,
} from '@/types/import'

const { Title, Paragraph, Text } = Typography
const { Dragger } = Upload

const STATUS_COLOR: Record<ImportJobRowStatus, string> = {
  OK: 'green',
  ERROR: 'red',
  DUPLICATE: 'orange',
}

export function ImportPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [activeJobId, setActiveJobId] = useState<number | null>(null)
  const [selectedRowIds, setSelectedRowIds] = useState<number[]>([])

  const detailQuery = useQuery({
    queryKey: ['imports', activeJobId],
    queryFn: () => getImport(activeJobId as number),
    enabled: activeJobId != null,
  })

  const uploadMutation = useMutation({
    mutationFn: (file: File) => uploadImport(file),
    onSuccess: (job: ImportJob) => {
      message.success(`已建立匯入工作 #${job.id}`)
      setActiveJobId(job.id)
      setSelectedRowIds([])
    },
    onError: (err) => message.error((err as Error).message ?? '上傳失敗'),
  })

  const commitMutation = useMutation({
    mutationFn: () => commitImport(activeJobId as number,
        selectedRowIds.length > 0 ? selectedRowIds : undefined),
    onSuccess: (result) => {
      message.success(`已匯入 ${result.committedCount} 筆`)
      queryClient.invalidateQueries({ queryKey: ['transactions'] })
      queryClient.invalidateQueries({ queryKey: ['accounts'] })
      setActiveJobId(null)
      navigate('/transactions')
    },
    onError: (err) => message.error((err as Error).message ?? '匯入失敗'),
  })

  const cancelMutation = useMutation({
    mutationFn: () => cancelImport(activeJobId as number),
    onSuccess: () => {
      message.info('已取消匯入')
      setActiveJobId(null)
    },
  })

  const okRows = useMemo<ImportJobRow[]>(
      () => detailQuery.data?.rows.filter((r) => r.status === 'OK') ?? [],
      [detailQuery.data])

  const defaultSelectedKeys = useMemo(() => okRows.map((r) => r.id), [okRows])
  const effectiveSelection = selectedRowIds.length > 0
      ? selectedRowIds
      : defaultSelectedKeys

  const columns: ColumnsType<ImportJobRow> = [
    { title: '#', dataIndex: 'rowIndex', width: 60 },
    { title: '狀態', dataIndex: 'status', width: 100, render: (s: ImportJobRowStatus) =>
        <Tag color={STATUS_COLOR[s]}>{s}</Tag> },
    { title: '日期', dataIndex: 'parsedDate', width: 120 },
    { title: '類型', dataIndex: 'parsedType', width: 90 },
    { title: '金額', dataIndex: 'parsedAmount', width: 110, align: 'right' as const,
      render: (v: number | null) => v?.toFixed(2) ?? '—' },
    { title: '備註', dataIndex: 'parsedNote', ellipsis: true },
    { title: '錯誤', dataIndex: 'errorMessage', ellipsis: true,
      render: (m: string | null) => m ?? '' },
  ]

  return (
    <Space direction="vertical" size={24} style={{ width: '100%' }}>
      <Card>
        <Title level={3}>匯入交易</Title>
        <Paragraph>
          支援 <Text code>.csv</Text> 與 <Text code>.xlsx</Text>，每檔最多 10000 列，5 MB 以內。
        </Paragraph>
        <Paragraph type="secondary">
          期望欄位：<Text code>date, type, account, amount, category, to_account, note</Text>
        </Paragraph>
        <Dragger
          multiple={false}
          accept=".csv,.xlsx"
          showUploadList={false}
          disabled={uploadMutation.isPending}
          customRequest={({ file }) => uploadMutation.mutate(file as File)}
        >
          <p className="ant-upload-drag-icon"><InboxOutlined /></p>
          <p className="ant-upload-text">點擊或拖放檔案到此處上傳</p>
          <p className="ant-upload-hint">上傳後會進入預覽，再按確認才會匯入。</p>
        </Dragger>
      </Card>

      {detailQuery.data && (
        <Card
          title={`工作 #${detailQuery.data.job.id} — ${detailQuery.data.job.filename}`}
          extra={
            <Space>
              <Popconfirm title="取消這批匯入？" onConfirm={() => cancelMutation.mutate()}>
                <Button>取消</Button>
              </Popconfirm>
              <Button
                type="primary"
                disabled={effectiveSelection.length === 0}
                loading={commitMutation.isPending}
                onClick={() => commitMutation.mutate()}
              >
                確認匯入（{effectiveSelection.length}）
              </Button>
            </Space>
          }
        >
          <Space size={32} style={{ marginBottom: 16 }}>
            <Statistic title="總列數" value={detailQuery.data.job.rowCount} />
            <Statistic title="OK" value={detailQuery.data.job.okCount} valueStyle={{ color: '#52c41a' }} />
            <Statistic title="錯誤" value={detailQuery.data.job.errorCount} valueStyle={{ color: '#cf1322' }} />
            <Statistic title="重複" value={detailQuery.data.job.dupCount} valueStyle={{ color: '#fa8c16' }} />
          </Space>

          <Table<ImportJobRow>
            rowKey="id"
            size="small"
            pagination={{ pageSize: 20 }}
            dataSource={detailQuery.data.rows}
            columns={columns}
            rowSelection={{
              selectedRowKeys: effectiveSelection,
              onChange: (keys) => setSelectedRowIds(keys as number[]),
              getCheckboxProps: (row) => ({ disabled: row.status !== 'OK' }),
            }}
          />
        </Card>
      )}
    </Space>
  )
}
```

- [ ] **Step 11.2: Run frontend checks**

```bash
cd frontend
npm run typecheck
npm run lint
npm run build
```

Expected: all green. Bundle may be slightly larger; not a blocker.

- [ ] **Step 11.3: Commit C4**

```bash
git add \
  frontend/src/types/import.ts \
  frontend/src/api/imports.ts \
  frontend/src/pages/ImportPage.tsx \
  frontend/src/App.tsx \
  frontend/src/components/AppLayout.tsx
git commit -m "$(cat <<'EOF'
feat(frontend): add import page with preview and partial commit

The preview lives on a single page rather than a multi-step wizard so a
user reviewing a mistakenly-uploaded file can cancel without losing the
"now you have to confirm" affordance. Default selection is every OK row;
ERROR/DUPLICATE are checkbox-disabled so accidental commit of bad rows
is impossible without code intervention.
EOF
)"
```

---

## Task 12: Playwright E2E

**Files:**
- No code changes; uses Playwright MCP and a temporary fixture CSV in `/tmp`.

**Interfaces:**
- Consumes: full frontend + backend
- Produces: assertions, screenshots if helpful

- [ ] **Step 12.1: Bring up backend + frontend**

In separate background shells:

```bash
cd backend && ./mvnw spring-boot:run    # foreground; let it run, capture PID
cd frontend && npm run dev               # foreground; let it run, capture PID
```

Wait for both to be healthy (`curl -fs localhost:8080/api/v1/health`, `curl -fs localhost:5173 | head -1`).

- [ ] **Step 12.2: Drive the flow via Playwright MCP**

Using the MCP browser tools:
1. Navigate to `http://localhost:5173/login`
2. Register `imp+<nanoTime>@example.com / Importer / Importer1234!`
3. Create accounts: `主帳戶` TWD 10000, `副帳戶` TWD 0, `USD 帳` USD 0 (via `/accounts` UI or direct API calls in browser console)
4. Create a baseline EXPENSE: `主帳戶, 2026-06-02, 250.50, 飲食, 午餐` (for duplicate test)
5. Navigate to `/import`
6. Drag `sample-mixed.csv` (write to `/tmp/sample-mixed.csv` first with the fixture content from Step 5.9)
7. Assert preview shows: rowCount 6, okCount 2, errorCount 2, dupCount 2
8. Uncheck one OK row, press 確認匯入, assert committedCount == 1
9. Navigate to `/transactions`, assert the imported row is visible; assert `主帳戶` balance updated correctly
10. Go back to `/import`, upload the same file again, assert all matching rows now flagged DUPLICATE

- [ ] **Step 12.3: Tear down servers**

Kill the two background processes (record their PIDs at start; `kill <pid>`).

- [ ] **Step 12.4: Commit C5**

If any tweaks were required during E2E (bug fixes), stage them explicitly and commit:

```bash
git status --short   # confirm nothing unrelated is dirty
# git add <specific files> only if E2E surfaced a bug
git commit -m "fix(imports): <whatever the E2E surfaced>" || true
```

If no fixes were required, skip the commit (no empty commits per CLAUDE.md). Note in the workbook (T13) that E2E passed clean.

---

## Task 13: Docs sync

**Files:**
- Create: `docs-site/docs/user-guide/import.md`
- Create: `docs-site/docs/api-reference/imports.md`
- Modify: `docs-site/docs/architecture/database.md`
- Modify: `docs-site/docs/changelog.md`
- Modify: `docs-site/docs/index.md`
- Modify: `docs-site/mkdocs.yml`
- Modify: `docs/workbook.md`

- [ ] **Step 13.1: User guide**

Create `docs-site/docs/user-guide/import.md`:

```markdown
# CSV / Excel 匯入

把一批整理好的交易一次匯入，避免逐筆手動建檔。

## 我能做什麼

- 上傳 `.csv` 或 `.xlsx`，每檔最多 10000 列、5 MB
- 在預覽頁逐列檢查狀態（OK / 錯誤 / 重複），勾選後再正式匯入
- 重複偵測：與既存交易 / 同檔先前列同 hash 的列會被擋下，不會重複入庫

## 期望欄位

| 欄位 | 必填 | 範例 | 說明 |
| ---- | ---- | ---- | ---- |
| `date` | ✅ | `2026-06-18` | ISO 字串；Excel 日期 cell 自動轉 |
| `type` | ✅ | `INCOME` / `EXPENSE` / `TRANSFER` | 不分大小寫 |
| `account` | ✅ | `主帳戶` | 比對你既有的帳戶名 |
| `amount` | ✅ | `1500.00` | 正數；逗號分隔接受 |
| `category` | 收入 / 支出必填 | `飲食` | 須與 type 一致；轉帳留空 |
| `to_account` | 轉帳必填 | `副帳戶` | 收入 / 支出留空 |
| `note` | ⛔ | `午餐` | ≤255 字 |

## 操作步驟

1. 上方選單點「匯入」
2. 拖放或點擊上傳 CSV / XLSX
3. 預覽頁檢查每列狀態，OK 預設已勾選；想跳過任何 OK 列就取消勾選
4. 按「確認匯入」
5. 系統會把選中的列轉成交易並更新帳戶餘額

## 常見訊息

| 訊息 | 原因 |
| ---- | ---- |
| `Account not found: <name>` | 帳戶名與你帳戶不符 |
| `Category not found or kind mismatch: <name>` | 分類不存在或與 type 不符（收入接到支出分類等） |
| `Cross-currency transfer not supported` | 跨幣別轉帳（規劃中：Sprint 5 匯率上線後解鎖） |
| `Duplicate of existing transaction` | 與既有交易（或同檔先前列）相同 |
| `File exceeds maximum row count of 10000` | 超過 10000 列 |

## 預覽會保留多久？

24 小時。逾時未確認會自動標為 `EXPIRED`。
```

- [ ] **Step 13.2: API reference**

Create `docs-site/docs/api-reference/imports.md`:

```markdown
# 匯入 API

> Base path：`/api/v1/imports`，所有端點需 `Authorization: Bearer <token>`

## 建立匯入工作

`POST /imports` — `multipart/form-data`，欄位 `file`

回應 `201 Created`：

```json
{
  "id": 12,
  "filename": "june.csv",
  "format": "CSV",
  "status": "PENDING",
  "rowCount": 6,
  "okCount": 4,
  "errorCount": 1,
  "dupCount": 1,
  "createdAt": "2026-06-18T08:00:00Z",
  "committedAt": null,
  "expiresAt": "2026-06-19T08:00:00Z"
}
```

## 列出最近匯入工作

`GET /imports` — 回 20 筆，依 `id` desc

## 取得工作細節（含每列）

`GET /imports/{id}`

```json
{
  "job": { ...同上... },
  "rows": [
    { "id": 101, "rowIndex": 1, "status": "OK", "errorMessage": null,
      "rawJson": "{\"date\":\"2026-06-01\",...}",
      "parsedType": "INCOME", "parsedAmount": "30000.00",
      "parsedDate": "2026-06-01", "parsedAccountId": 5,
      "parsedToAccountId": null, "parsedCategoryId": 12,
      "parsedNote": "六月薪水" }
  ]
}
```

## 確認匯入

`POST /imports/{id}/commit`

```json
{ "rowIds": [101, 102] }    // 省略或 null = 全部 OK 列
```

回應 `200`：

```json
{ "jobId": 12, "committedCount": 2, "transactionIds": [201, 202] }
```

## 取消

`POST /imports/{id}/cancel` → `204`

## 錯誤碼

| Status | 何時 |
| ------ | ---- |
| 400 | 檔案缺 header / 空檔 / 列數 > 10000 |
| 401 | 未登入 |
| 404 | 工作不存在或非本人 |
| 409 | 工作不在 PENDING，或 commit 時 re-resolve 發現 ERROR 列 |
| 413 | 檔案 > 5 MB |
| 415 | 副檔名非 `.csv` / `.xlsx` |
```

- [ ] **Step 13.3: Database schema doc**

Append a section to `docs-site/docs/architecture/database.md`:

```markdown
### `import_jobs` (V4)

| 欄位 | 型別 | 限制 | 說明 |
| ---- | ---- | ---- | ---- |
| `id` | `BIGSERIAL` | PK | |
| `user_id` | `BIGINT` | NOT NULL FK → `users.id` CASCADE | |
| `filename` | `VARCHAR(255)` | NOT NULL | 原始檔名 |
| `format` | `VARCHAR(10)` | NOT NULL, CHECK | `CSV` / `XLSX` |
| `status` | `VARCHAR(20)` | NOT NULL, CHECK | `PENDING` / `COMMITTED` / `CANCELLED` / `EXPIRED` |
| `row_count` `ok_count` `error_count` `dup_count` | `INT` | NOT NULL default 0 | 統計快取 |
| `created_at` | `TIMESTAMPTZ` | NOT NULL default `now()` | |
| `committed_at` | `TIMESTAMPTZ` | NULL | 確認時間 |
| `expires_at` | `TIMESTAMPTZ` | NOT NULL default `now()+24h` | TTL |

### `import_job_rows` (V4)

| 欄位 | 型別 | 限制 | 說明 |
| ---- | ---- | ---- | ---- |
| `id` | `BIGSERIAL` | PK | |
| `job_id` | `BIGINT` | NOT NULL FK → `import_jobs.id` CASCADE | |
| `row_index` | `INT` | NOT NULL, `(job_id, row_index)` UNIQUE | 1-based |
| `raw_json` | `JSONB` | NOT NULL | 原始一列 |
| `parsed_type` `parsed_amount` `parsed_date` `parsed_account_id` `parsed_to_account_id` `parsed_category_id` `parsed_note` | 與 transactions 同 | 所有 FK 用 ON DELETE SET NULL | 預解析結果 |
| `dedup_hash` | `CHAR(64)` | | SHA-256(canonical key) |
| `status` | `VARCHAR(15)` | NOT NULL, CHECK | `OK` / `ERROR` / `DUPLICATE` |
| `error_message` | `VARCHAR(500)` | NULL | 失敗原因 |

`dedup_hash` 算法與 `transactions` 列在 commit 流程內逐筆比對；既有 `transactions` 表**不**新增 hash 欄。
```

- [ ] **Step 13.4: Changelog**

In `docs-site/docs/changelog.md`, demote the prior `[Unreleased]` heading (Sprint 2.5 — change `[Unreleased] — Sprint 2.5 完成` to `[0.3.5] — 2026-06-18 — Sprint 2.5`) and add a new top section:

```markdown
## [Unreleased] — Sprint 3 完成

### 新增（後端）
- Flyway V4：`import_jobs`、`import_job_rows` 雙表 + JSONB raw、SHA-256 dedup hash
- CSV / XLSX 匯入：parser 抽象（Commons CSV + POI streaming）、RowResolver、ImportJobService、ImportCommitter（呼叫既有 TransactionService 完成餘額同步）
- 過期排程：`@Scheduled` 每小時把 24h 未確認的 job 標 EXPIRED
- 整合測試擴充至 39 筆（+19）+ 17 個 parser/resolver 單元測試

### 新增（前端）
- `/import` 頁：拖放上傳、預覽表（OK / 錯誤 / 重複狀態彩色 tag）、checkbox 規則（ERROR/DUPLICATE 不可勾）、部分確認匯入、取消
- 導覽列加「匯入」入口
```

- [ ] **Step 13.5: Status table**

In `docs-site/docs/index.md`, change the `CSV / Excel 匯入` row to:

```markdown
| CSV / Excel 匯入 | ✅ 上線 |
```

- [ ] **Step 13.6: mkdocs nav**

In `docs-site/mkdocs.yml`, add entries for the two new pages (find existing `user-guide:` and `api-reference:` sections and append):

```yaml
  - 使用者手冊:
      ...
      - CSV / Excel 匯入: user-guide/import.md
  - API 參考:
      ...
      - 匯入: api-reference/imports.md
```

- [ ] **Step 13.7: Workbook**

In `docs/workbook.md`, mark Sprint 3 items complete and add a 2026-06-18 changelog entry:

- Mark `S3 | W6–7 | CSV / Excel 匯入` status `✅ 完成`
- In Sprint 3 backlog section, tick all four bullets
- Append a new dated entry similar to prior sprint entries with one-paragraph summary

- [ ] **Step 13.8: Run `mkdocs --strict`**

Run: `cd docs-site && uv run mkdocs build --strict`
Expected: success, no broken cross-links.

- [ ] **Step 13.9: Commit C6**

```bash
git add \
  docs-site/docs/user-guide/import.md \
  docs-site/docs/api-reference/imports.md \
  docs-site/docs/architecture/database.md \
  docs-site/docs/changelog.md \
  docs-site/docs/index.md \
  docs-site/mkdocs.yml \
  docs/workbook.md
git commit -m "$(cat <<'EOF'
docs: capture Sprint 3 CSV/XLSX import flow

User-facing doc spells out the expected columns and error messages so a
new user can build a valid file without reading API docs; API reference
records the new endpoints; database doc adds V4 tables and notes the
deliberate choice not to add a hash column on transactions.
EOF
)"
```

---

## Self-Review

### Spec coverage

| Spec section | Covered by |
| ------------ | ---------- |
| §1 In-scope CSV + XLSX | T2 + T4 |
| §1 Upload → preview → confirm | T5 + T7 + T11 |
| §1 Partial import | T7 + T11 |
| §1 Duplicate detect & reject | T3 (`DedupHash`) + T5 (`computeExistingHashes`) + T11 (disabled checkbox) |
| §1 Account/category name match | T3 (`RowResolver`) + T5 (lookup maps) |
| §1 INCOME/EXPENSE/TRANSFER (same currency) | T3 (`RowResolver` validates) + T7 (commit delegates to `TransactionService`) |
| §3 V4 schema | T1 |
| §3 dedup_hash SHA-256 | T3 |
| §3 expires_at 24h | T1 (default) + T8 |
| §3 Fixed columns table | T2 fixture + T13 user guide |
| §4 POST /imports | T5 |
| §4 GET /imports/{id} | T5 |
| §4 POST /imports/{id}/commit | T7 |
| §4 POST /imports/{id}/cancel | T5 (service) + T5 (controller wiring) |
| §4 RowResolver failure table | T3 |
| §4 Parser interface | T2 + T4 |
| §5 endpoints table | T5 + T7 |
| §5 5 MB cap | T5 step 5.1 multipart config + T5 step 5.6 handler |
| §5 10000-row cap | T5 step 5.5 (`upload` checks `properties.getMaxRows`) + T9 test |
| §5 ImportPage UI | T10 + T11 |
| §5 React Query keys & invalidation | T11 (`['imports', jobId]`, invalidate transactions+accounts on commit) |
| §6 Test counts (39 IT + 17 unit) | T2 (6 CSV) + T3 (8 resolver) + T4 (3 XLSX) + T5 (4 IT) + T6 (1 IT) + T7 (5 IT) + T8 (1 IT) + T9 (1 IT) — total 19 new IT + 17 unit, plus existing 20 → 39 IT ✅ |
| §6 Fixtures | T2 (sample-good.csv, empty-only-header.csv) + T5 (sample-mixed.csv) + T9 (large CSV built inline) + T4 (XLSX built inline) |
| §7 Task slice mapping S3-T01..T13 | Tasks 1–13 map 1:1 |
| §8 Risks: POI OOM, commit fail, dup-hash + note, concurrent imports, FK cascade | Addressed in T4 (XSSFWorkbook noted; for now uses `XSSFWorkbook(in)` which is acceptable for ≤10000 rows; mark as follow-up if memory becomes an issue), T7 (PESSIMISTIC_WRITE lock + re-resolve), T1 (ON DELETE SET NULL) |

Note: spec §4.5 originally called for `XSSFReader` streaming. The implementation here uses `XSSFWorkbook(InputStream)` for simplicity since our 5 MB / 10000-row cap fits comfortably in memory. This is a deliberate scope tradeoff — flag in workbook/risks if future use cases push the cap higher.

### Placeholder scan

No `TBD`, `TODO`, `implement later`, `Similar to Task N`, or hand-wave "add validation" / "appropriate error handling" steps. Every step has either source code, exact commands, or a precise edit instruction.

### Type & name consistency

- `RawRow` shape `(int rowIndex, Map<String,String> fields)` — same in T2, T3, T4, T5.
- `TransactionFileParser` method `supports()` / `parse(InputStream)` — same in T2, T4, T5.
- `RowResolver.resolve(...)` signature with `Map<Long, String> currencyByAccountId` matches T3 implementation and T5 caller.
- `ImportJobRowStatus` enum `{ OK, ERROR, DUPLICATE }` — same in T1, T3, T7.
- `ImportDtos.CommitRequest` field `rowIds` — used the same way in T7 controller and T10 client.
- `lockOkRowsByJobId` in T1 repository matches T7 caller.
- `findVisibleToUser(id, userId)` (existing `CategoryRepository`) — used in T7 `revalidate`.
- `findByUserIdAndTxnDateBetween` — added in T5 step 5.3, used by `ImportJobService.computeExistingHashes`.

### Tweaks made

- T9 dropped the 413 IT (would require per-test multipart override). Manual smoke covers it; handler is still wired.
- T4 uses `XSSFWorkbook(InputStream)` not `XSSFReader` — documented as a deliberate scope tradeoff (≤10000 rows fits in memory).

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-06-18-csv-excel-import.md`. Two execution options:

**1. Subagent-Driven (recommended)** — I dispatch a fresh subagent per task, review between tasks, fast iteration.

**2. Inline Execution** — Execute tasks in this session using executing-plans, batch execution with checkpoints.

Which approach?

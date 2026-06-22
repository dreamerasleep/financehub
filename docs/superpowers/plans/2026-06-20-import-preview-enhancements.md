# Sprint 3.5 — Import 預覽頁強化 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 讓使用者在 `/import` 預覽頁就地修復 ERROR / DUPLICATE 列、批次選取、依狀態篩選，免去重新上傳整檔。

**Architecture:** 後端新增單一 PATCH 端點重跑 `RowResolver`；前端拆出 `ImportToolbar`（filter + bulk 按鈕）與 `ImportRowEditDrawer`（PATCH 表單），`ImportPage` 改為容器並移除 selection fallback bug。

**Tech Stack:** Spring Boot 3.3.4 / JPA / PostgreSQL 16 / Testcontainers · React 19 / TypeScript / AntD 5 / TanStack Query 5 / Vite · 新增 vitest（單元測試）

## Global Constraints

- Java 21 / Spring Boot 3.3.4；無 Flyway migration（zero schema change）
- 沿用 Testcontainers Postgres IT（current 39/39 綠 → 目標 50 綠）
- 錯誤 code 用 lowercase snake，配合既有 `GlobalExceptionHandler` 模式（`not_found` / `conflict` / `forbidden`）
- 不引入新 backend runtime 依賴；frontend 僅新增 `vitest` + `@vitejs/plugin-react` 已存在
- React 19 / AntD 5 / TanStack Query 5；不引入新 UI 套件
- 不破壞既有 transactions / accounts / category / imports API
- 程式碼不寫多餘註解；commit 訊息英文、動詞開頭、聚焦 why
- API 路徑 `/api/v1/imports/...`
- testid 用 kebab-case：`import-filter-radio` / `import-bulk-select-ok` / `import-edit-row-{id}` 等
- 分支 `feature/sprint-3.5-import-preview`，從 main（commit `231ee66` 之後）切出

---

## File Structure

### 新增

```
backend/src/main/java/com/financehub/application/imports/
├── OkRowNotEditableException.java          NEW
└── JobNotPendingException.java             NEW

backend/src/test/java/com/financehub/api/imports/
└── ImportRowPatchIT.java                   NEW (11 cases)

frontend/src/components/import/
├── ImportToolbar.tsx                       NEW
├── ImportRowEditDrawer.tsx                 NEW
└── selection.ts                            NEW (pure helpers + types)

frontend/src/components/import/__tests__/
└── selection.test.ts                       NEW (helpers unit)
```

### 修改

```
backend/src/main/java/com/financehub/
├── api/common/GlobalExceptionHandler.java          (M) 新增兩個 handler
├── api/imports/ImportController.java               (M) +PATCH /rows/{rowId}
├── api/imports/ImportDtos.java                     (M) +PatchRowRequest record
├── application/imports/ImportJobService.java       (M) +patchRow(...)
└── domain/imports/ImportJobRowRepository.java      (M) +新增兩個查詢

frontend/
├── package.json                            (M) +vitest, +jsdom, scripts.test
├── vite.config.ts                          (M) +test config
├── src/api/imports.ts                      (M) +patchImportRow
├── src/types/import.ts                     (M) +PatchRowRequest, PatchRowResponse
└── src/pages/ImportPage.tsx                (M) toolbar 整合、移除 fallback bug

docs-site/docs/user-guide/import.md         (M) 「修正錯誤列」「篩選與批次選取」
docs-site/docs/api-reference/imports.md     (M) PATCH endpoint
docs-site/docs/changelog.md                 (M) [Unreleased] — Sprint 3.5
docs/workbook.md                            (M) 新增 2026-06-21 Sprint 3.5 條目
```

---

## Task Dependency Chain

```
T01 (backend service) ──> T02 (backend controller + IT)
                                  │
                                  ▼
                            C1 commit
                                  │
T03 (frontend types/api) ◀────────┘
        │
        ▼
T04 (frontend helpers + toolbar)
        │
        ▼
   C2 commit
        │
        ▼
T05 (drawer + ImportPage integration)
        │
        ▼
   C3 commit
        │
        ▼
T06 (MCP exploratory E2E) ──> T07 (docs sync)
                                       │
                                       ▼
                                  C4 commit
```

每個 task 之內含「Write test → Run failing → Implement → Run pass → Commit」步驟（後端任務遵循 TDD；前端 helpers 也遵循；前端元件以 MCP 探索式驗證為主）。

---

### Task 01: Backend service layer — exceptions, repository, DTO, service method

**Files:**
- Create: `backend/src/main/java/com/financehub/application/imports/OkRowNotEditableException.java`
- Create: `backend/src/main/java/com/financehub/application/imports/JobNotPendingException.java`
- Modify: `backend/src/main/java/com/financehub/api/common/GlobalExceptionHandler.java` (新增兩 handler)
- Modify: `backend/src/main/java/com/financehub/api/imports/ImportDtos.java` (新增 `PatchRowRequest`)
- Modify: `backend/src/main/java/com/financehub/domain/imports/ImportJobRowRepository.java` (新增兩查詢)
- Modify: `backend/src/main/java/com/financehub/application/imports/ImportJobService.java` (新增 `patchRow`)

**Interfaces:**
- Consumes: 既有 `RowResolver.resolve(...)`、`AccountRepository.findByUserIdOrderByIdAsc(userId)`、`CategoryRepository.findVisibleTo(userId)`、`TransactionRepository.findByUserIdAndTxnDateBetween(...)`、`DedupHash.of(...)`、`ImportJobRepository.findByIdAndUserId(id, userId)`、`ObjectMapper.writeValueAsString(Map)`
- Produces:
  - `ImportDtos.PatchRowRequest(String date, String type, String account, String amount, String category, String to_account, String note)` — JSON body record
  - `ImportDtos.PatchRowResponse(ImportJobResponse job, ImportJobRowResponse row)` — 回傳結構
  - `ImportJobService.patchRow(Long userId, Long jobId, Long rowId, PatchRowRequest body) → PatchRowResponse`
  - `ImportJobRowRepository.findByIdAndJobId(Long rowId, Long jobId): Optional<ImportJobRow>`
  - `ImportJobRowRepository.findOkDedupHashesByJobIdExcept(Long jobId, Long excludeRowId): Set<String>`
  - `ImportJobRowRepository.countByJobIdAndStatus(Long jobId, ImportJobRowStatus status): long`
  - `OkRowNotEditableException` → 403 `ok_row_not_editable`
  - `JobNotPendingException` → 409 `job_not_pending`

#### Step 1: 新增兩個 exception 類別

- [ ] **Step 1: Create `OkRowNotEditableException.java`**

`backend/src/main/java/com/financehub/application/imports/OkRowNotEditableException.java`:

```java
package com.financehub.application.imports;

public class OkRowNotEditableException extends RuntimeException {
    public OkRowNotEditableException(String message) {
        super(message);
    }
}
```

- [ ] **Step 2: Create `JobNotPendingException.java`**

`backend/src/main/java/com/financehub/application/imports/JobNotPendingException.java`:

```java
package com.financehub.application.imports;

public class JobNotPendingException extends RuntimeException {
    public JobNotPendingException(String message) {
        super(message);
    }
}
```

- [ ] **Step 3: Add two `@ExceptionHandler` methods to `GlobalExceptionHandler`**

Insert before the closing `}` of `GlobalExceptionHandler.java` (just after the `handlePayloadTooLarge` method):

```java
    @ExceptionHandler(com.financehub.application.imports.OkRowNotEditableException.class)
    public ResponseEntity<ApiError> handleOkRowNotEditable(
            com.financehub.application.imports.OkRowNotEditableException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiError.of("ok_row_not_editable", ex.getMessage()));
    }

    @ExceptionHandler(com.financehub.application.imports.JobNotPendingException.class)
    public ResponseEntity<ApiError> handleJobNotPending(
            com.financehub.application.imports.JobNotPendingException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of("job_not_pending", ex.getMessage()));
    }
```

#### Step 4-5: 新增 DTO

- [ ] **Step 4: Add `PatchRowRequest` record to `ImportDtos.java`**

在 `ImportCommitResult` 後（檔案接近底部）插入：

```java
    public record PatchRowRequest(
            String date,
            String type,
            String account,
            String amount,
            String category,
            @com.fasterxml.jackson.annotation.JsonProperty("to_account") String toAccount,
            String note
    ) {}

    public record PatchRowResponse(
            ImportJobResponse job,
            ImportJobRowResponse row
    ) {}
```

> 注意 `to_account` 用 `@JsonProperty` 對應 snake_case，符合 spec 的 request body。

#### Step 5-7: 新增 Repository 方法

- [ ] **Step 5: Add three methods to `ImportJobRowRepository`**

`backend/src/main/java/com/financehub/domain/imports/ImportJobRowRepository.java` 完整改為（保留既有 method）：

```java
package com.financehub.domain.imports;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface ImportJobRowRepository extends JpaRepository<ImportJobRow, Long> {

    List<ImportJobRow> findByJobIdOrderByRowIndexAsc(Long jobId);

    Optional<ImportJobRow> findByIdAndJobId(Long id, Long jobId);

    long countByJobIdAndStatus(Long jobId, ImportJobRowStatus status);

    @Query("""
        SELECT r.dedupHash FROM ImportJobRow r
        WHERE r.jobId = :jobId
          AND r.status = com.financehub.domain.imports.ImportJobRowStatus.OK
          AND r.id <> :excludeRowId
          AND r.dedupHash IS NOT NULL
    """)
    Set<String> findOkDedupHashesByJobIdExcept(
            @Param("jobId") Long jobId,
            @Param("excludeRowId") Long excludeRowId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT r FROM ImportJobRow r
        WHERE r.jobId = :jobId AND r.status = com.financehub.domain.imports.ImportJobRowStatus.OK
        ORDER BY r.rowIndex ASC
    """)
    List<ImportJobRow> lockOkRowsByJobId(@Param("jobId") Long jobId);
}
```

#### Step 6-8: 寫 `patchRow` 服務方法

- [ ] **Step 6: Add `patchRow` to `ImportJobService.java`**

在 `cancel(...)` 後新增以下方法（imports 已有 `ImportJobRowStatus`，再額外 import `OkRowNotEditableException`、`JobNotPendingException` 與 `ImportDtos`）：

```java
    @org.springframework.transaction.annotation.Transactional
    public com.financehub.api.imports.ImportDtos.PatchRowResponse patchRow(
            Long userId,
            Long jobId,
            Long rowId,
            com.financehub.api.imports.ImportDtos.PatchRowRequest body) {

        ImportJob job = jobRepository.findByIdAndUserId(jobId, userId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                        "Import job not found"));

        if (job.getStatus() != ImportJobStatus.PENDING) {
            throw new JobNotPendingException(
                    "Job is not PENDING (status=" + job.getStatus() + ")");
        }

        ImportJobRow row = rowRepository.findByIdAndJobId(rowId, jobId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                        "Import row not found"));

        if (row.getStatus() == com.financehub.domain.imports.ImportJobRowStatus.OK) {
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
        try {
            row.getClass();
            java.lang.reflect.Field rawJsonField =
                    com.financehub.domain.imports.ImportJobRow.class.getDeclaredField("rawJson");
            rawJsonField.setAccessible(true);
            rawJsonField.set(row, objectMapper.writeValueAsString(raw));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to update raw_json", ex);
        }
        rowRepository.save(row);

        long okCount = rowRepository.countByJobIdAndStatus(jobId,
                com.financehub.domain.imports.ImportJobRowStatus.OK);
        long errorCount = rowRepository.countByJobIdAndStatus(jobId,
                com.financehub.domain.imports.ImportJobRowStatus.ERROR);
        long dupCount = rowRepository.countByJobIdAndStatus(jobId,
                com.financehub.domain.imports.ImportJobRowStatus.DUPLICATE);
        job.setOkCount((int) okCount);
        job.setErrorCount((int) errorCount);
        job.setDupCount((int) dupCount);
        jobRepository.save(job);

        return new com.financehub.api.imports.ImportDtos.PatchRowResponse(
                com.financehub.api.imports.ImportDtos.ImportJobResponse.from(job),
                com.financehub.api.imports.ImportDtos.ImportJobRowResponse.from(row));
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
```

> `ImportJobRow` 沒提供 `setRawJson(...)` 方法（建構式之後即不可變），所以用 reflection 寫入。亦可順手在 `ImportJobRow` 加 setter（一行）— 二擇一。若選擇加 setter，把 reflection 區塊改成 `row.setRawJson(objectMapper.writeValueAsString(raw));`。**推薦加 setter**，更乾淨；下方 Step 6.1 提供 setter 程式碼。

- [ ] **Step 6.1: Add `setRawJson` setter to `ImportJobRow.java`** (推薦做法，取代 reflection)

在 `ImportJobRow.java` 既有 `public String getRawJson()` 後新增：

```java
    public void setRawJson(String v) { this.rawJson = v; }
```

然後把 Step 6 的 patchRow 內的 reflection 區塊：

```java
        try {
            row.getClass();
            java.lang.reflect.Field rawJsonField =
                    com.financehub.domain.imports.ImportJobRow.class.getDeclaredField("rawJson");
            rawJsonField.setAccessible(true);
            rawJsonField.set(row, objectMapper.writeValueAsString(raw));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to update raw_json", ex);
        }
```

替換為：

```java
        try {
            row.setRawJson(objectMapper.writeValueAsString(raw));
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize raw row", ex);
        }
```

#### Step 7: 編譯

- [ ] **Step 7: Compile backend**

Run:
```bash
cd /Users/willywu/Downloads/AIWorkspace/claude/financehub/backend && ./mvnw -q compile
```
Expected: BUILD SUCCESS, no compile errors.

#### Step 8: Commit (與 T02 合一 commit，本 task 不 commit)

留待 T02 完成後一起 commit 為 C1。

---

### Task 02: Backend controller endpoint + ImportRowPatchIT (11 cases)

**Files:**
- Modify: `backend/src/main/java/com/financehub/api/imports/ImportController.java` (新增 PATCH handler)
- Create: `backend/src/test/java/com/financehub/api/imports/ImportRowPatchIT.java`

**Interfaces:**
- Consumes: `ImportJobService.patchRow(...)`, `ImportDtos.PatchRowRequest`, `ImportDtos.PatchRowResponse`
- Produces: `PATCH /api/v1/imports/{jobId}/rows/{rowId}` HTTP endpoint

#### Step 1: 加 PATCH handler

- [ ] **Step 1: Add PATCH handler to `ImportController.java`**

在 `commit(...)` 後新增：

```java
    @PatchMapping("/{jobId}/rows/{rowId}")
    public ImportDtos.PatchRowResponse patchRow(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long jobId,
            @PathVariable Long rowId,
            @RequestBody ImportDtos.PatchRowRequest body) {
        return importJobService.patchRow(user.id(), jobId, rowId, body);
    }
```

#### Step 2-12: 寫 IT 11 案

- [ ] **Step 2: Create `ImportRowPatchIT.java` 骨架**

`backend/src/test/java/com/financehub/api/imports/ImportRowPatchIT.java`:

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
import java.util.LinkedHashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = PostgresTestcontainer.class)
class ImportRowPatchIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    // === fixtures (見 helper 區段) ===
}
```

- [ ] **Step 3: 在類別內加 helper（從 ImportJobIT 借鑒、稍作簡化）**

把以下方法貼到 `ImportRowPatchIT` 類別內：

```java
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

    private long uploadCsv(String bearer, String csv) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.csv", "text/csv",
                csv.getBytes(StandardCharsets.UTF_8));
        String resp = mockMvc.perform(multipart("/api/v1/imports")
                        .file(file)
                        .header("Authorization", bearer))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).get("id").asLong();
    }

    private JsonNode getJob(String bearer, long jobId) throws Exception {
        String resp = mockMvc.perform(get("/api/v1/imports/" + jobId)
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(resp);
    }

    private long findRowIdByIndex(JsonNode jobDetail, int rowIndex) {
        for (JsonNode r : jobDetail.get("rows")) {
            if (r.get("rowIndex").asInt() == rowIndex) {
                return r.get("id").asLong();
            }
        }
        throw new AssertionError("No row with index " + rowIndex);
    }

    private String patchBody(String date, String type, String account, String amount,
                             String category, String toAccount, String note) throws Exception {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("date", date);
        m.put("type", type);
        m.put("account", account);
        m.put("amount", amount);
        m.put("category", category);
        m.put("to_account", toAccount);
        m.put("note", note);
        return objectMapper.writeValueAsString(m);
    }
```

- [ ] **Step 4: Test #1 — PATCH ERROR row 改為合法欄位 → OK**

```java
    @Test
    void patchErrorRowWithValidFieldsBecomesOk() throws Exception {
        String bearer = register("p1+" + System.nanoTime() + "@example.com");
        createAccount(bearer, "主帳戶", "TWD", "0.00");

        // CSV: 1 OK + 1 ERROR (amount=abc)
        String csv = "date,type,account,amount,category,to_account,note\n"
                + "2026-06-01,EXPENSE,主帳戶,100.00,飲食,,午餐\n"
                + "2026-06-02,EXPENSE,主帳戶,abc,飲食,,壞列\n";
        long jobId = uploadCsv(bearer, csv);

        JsonNode before = getJob(bearer, jobId);
        long badRowId = findRowIdByIndex(before, 2);

        mockMvc.perform(patch("/api/v1/imports/" + jobId + "/rows/" + badRowId)
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patchBody("2026-06-02", "EXPENSE", "主帳戶", "200.00",
                                "飲食", "", "晚餐")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.row.status").value("OK"))
                .andExpect(jsonPath("$.row.errorMessage").doesNotExist())
                .andExpect(jsonPath("$.job.okCount").value(2))
                .andExpect(jsonPath("$.job.errorCount").value(0))
                .andExpect(jsonPath("$.job.dupCount").value(0));
    }
```

- [ ] **Step 5: Test #2 — PATCH ERROR row 改完撞 DB 既存 transaction → DUPLICATE**

```java
    @Test
    void patchErrorRowMatchingExistingTransactionBecomesDuplicate() throws Exception {
        String bearer = register("p2+" + System.nanoTime() + "@example.com");
        Long accId = createAccount(bearer, "主帳戶", "TWD", "0.00");
        // 先建一筆既存交易
        Long catFood = pickCategoryByNameKind(bearer, "飲食", "EXPENSE");
        Map<String, Object> txn = new HashMap<>();
        txn.put("accountId", accId);
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

        // 上傳 1 ERROR (amount=abc)
        String csv = "date,type,account,amount,category,to_account,note\n"
                + "2026-06-02,EXPENSE,主帳戶,abc,飲食,,午餐\n";
        long jobId = uploadCsv(bearer, csv);
        JsonNode before = getJob(bearer, jobId);
        long rowId = findRowIdByIndex(before, 1);

        // PATCH 改成會撞既存的內容
        mockMvc.perform(patch("/api/v1/imports/" + jobId + "/rows/" + rowId)
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patchBody("2026-06-02", "EXPENSE", "主帳戶", "250.50",
                                "飲食", "", "午餐")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.row.status").value("DUPLICATE"))
                .andExpect(jsonPath("$.job.dupCount").value(1))
                .andExpect(jsonPath("$.job.errorCount").value(0));
    }
```

- [ ] **Step 6: Test #3 — PATCH DUP row 改 amount 變唯一 → OK**

```java
    @Test
    void patchDuplicateRowWithUniqueAmountBecomesOk() throws Exception {
        String bearer = register("p3+" + System.nanoTime() + "@example.com");
        createAccount(bearer, "主帳戶", "TWD", "0.00");

        // 同檔內 hash 相同 → 第一列 OK 第二列 DUPLICATE
        String csv = "date,type,account,amount,category,to_account,note\n"
                + "2026-06-01,EXPENSE,主帳戶,100.00,飲食,,午餐\n"
                + "2026-06-01,EXPENSE,主帳戶,100.00,飲食,,午餐\n";
        long jobId = uploadCsv(bearer, csv);
        JsonNode before = getJob(bearer, jobId);
        long dupRowId = findRowIdByIndex(before, 2);

        mockMvc.perform(patch("/api/v1/imports/" + jobId + "/rows/" + dupRowId)
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patchBody("2026-06-01", "EXPENSE", "主帳戶", "120.00",
                                "飲食", "", "午餐 2")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.row.status").value("OK"))
                .andExpect(jsonPath("$.job.okCount").value(2))
                .andExpect(jsonPath("$.job.dupCount").value(0));
    }
```

- [ ] **Step 7: Test #4 — PATCH OK row → 403 `ok_row_not_editable`**

```java
    @Test
    void patchOkRowReturns403() throws Exception {
        String bearer = register("p4+" + System.nanoTime() + "@example.com");
        createAccount(bearer, "主帳戶", "TWD", "0.00");
        String csv = "date,type,account,amount,category,to_account,note\n"
                + "2026-06-01,EXPENSE,主帳戶,100.00,飲食,,午餐\n";
        long jobId = uploadCsv(bearer, csv);
        long rowId = findRowIdByIndex(getJob(bearer, jobId), 1);

        mockMvc.perform(patch("/api/v1/imports/" + jobId + "/rows/" + rowId)
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patchBody("2026-06-01", "EXPENSE", "主帳戶", "200.00",
                                "飲食", "", "午餐")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ok_row_not_editable"));
    }
```

- [ ] **Step 8: Test #5 — PATCH rowId 不屬於該 job → 404**

```java
    @Test
    void patchUnrelatedRowIdReturns404() throws Exception {
        String bearer = register("p5+" + System.nanoTime() + "@example.com");
        createAccount(bearer, "主帳戶", "TWD", "0.00");
        String csv = "date,type,account,amount,category,to_account,note\n"
                + "2026-06-01,EXPENSE,主帳戶,abc,飲食,,壞列\n";
        long jobId = uploadCsv(bearer, csv);

        long fakeRowId = 9_999_999L;
        mockMvc.perform(patch("/api/v1/imports/" + jobId + "/rows/" + fakeRowId)
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patchBody("2026-06-01", "EXPENSE", "主帳戶", "100.00",
                                "飲食", "", "午餐")))
                .andExpect(status().isNotFound());
    }
```

- [ ] **Step 9: Test #6 — PATCH 別人的 job → 404**

```java
    @Test
    void patchOtherUsersJobReturns404() throws Exception {
        String alice = register("alice+" + System.nanoTime() + "@example.com");
        String bob = register("bob+" + System.nanoTime() + "@example.com");
        createAccount(alice, "主帳戶", "TWD", "0.00");

        String csv = "date,type,account,amount,category,to_account,note\n"
                + "2026-06-01,EXPENSE,主帳戶,abc,飲食,,壞列\n";
        long jobId = uploadCsv(alice, csv);
        long rowId = findRowIdByIndex(getJob(alice, jobId), 1);

        mockMvc.perform(patch("/api/v1/imports/" + jobId + "/rows/" + rowId)
                        .header("Authorization", bob)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patchBody("2026-06-01", "EXPENSE", "主帳戶", "100.00",
                                "飲食", "", "午餐")))
                .andExpect(status().isNotFound());
    }
```

- [ ] **Step 10: Test #7 — PATCH 已 CANCELLED 的 job → 409 `job_not_pending`**

```java
    @Test
    void patchCancelledJobReturns409() throws Exception {
        String bearer = register("p7+" + System.nanoTime() + "@example.com");
        createAccount(bearer, "主帳戶", "TWD", "0.00");
        String csv = "date,type,account,amount,category,to_account,note\n"
                + "2026-06-01,EXPENSE,主帳戶,abc,飲食,,壞列\n";
        long jobId = uploadCsv(bearer, csv);
        long rowId = findRowIdByIndex(getJob(bearer, jobId), 1);

        mockMvc.perform(post("/api/v1/imports/" + jobId + "/cancel")
                        .header("Authorization", bearer))
                .andExpect(status().isNoContent());

        mockMvc.perform(patch("/api/v1/imports/" + jobId + "/rows/" + rowId)
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patchBody("2026-06-01", "EXPENSE", "主帳戶", "100.00",
                                "飲食", "", "午餐")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("job_not_pending"));
    }
```

> Test #8 (EXPIRED) 不寫獨立案：`expires_at` 由 ExpiryScheduler 改寫，IT 環境不會觸發，且邏輯共用同一 `if (status != PENDING)` 分支，與 #7 重複。spec 標示「409 JOB_NOT_PENDING」已由 #7 涵蓋。

- [ ] **Step 11: Test #8 — PATCH body 缺必填 → 200 + row→ERROR**

```java
    @Test
    void patchWithMissingDateBecomesError() throws Exception {
        String bearer = register("p8+" + System.nanoTime() + "@example.com");
        createAccount(bearer, "主帳戶", "TWD", "0.00");
        String csv = "date,type,account,amount,category,to_account,note\n"
                + "2026-06-01,EXPENSE,主帳戶,abc,飲食,,壞列\n";
        long jobId = uploadCsv(bearer, csv);
        long rowId = findRowIdByIndex(getJob(bearer, jobId), 1);

        mockMvc.perform(patch("/api/v1/imports/" + jobId + "/rows/" + rowId)
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patchBody("", "EXPENSE", "主帳戶", "100.00",
                                "飲食", "", "午餐")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.row.status").value("ERROR"))
                .andExpect(jsonPath("$.row.errorMessage").value("Date is required"))
                .andExpect(jsonPath("$.job.errorCount").value(1));
    }
```

- [ ] **Step 12: Test #9 — A=OK B=DUP 同 hash，PATCH A 改 amount → A 仍 OK；B 仍 DUP**

```java
    @Test
    void patchOkRowAmountDoesNotRetroactivelyFlipDupRow() throws Exception {
        String bearer = register("p9+" + System.nanoTime() + "@example.com");
        createAccount(bearer, "主帳戶", "TWD", "0.00");
        // 兩列 hash 相同；第一列 OK、第二列 DUPLICATE
        String csv = "date,type,account,amount,category,to_account,note\n"
                + "2026-06-01,EXPENSE,主帳戶,100.00,飲食,,午餐\n"
                + "2026-06-01,EXPENSE,主帳戶,100.00,飲食,,午餐\n";
        long jobId = uploadCsv(bearer, csv);
        JsonNode before = getJob(bearer, jobId);
        long rowAId = findRowIdByIndex(before, 1);
        long rowBId = findRowIdByIndex(before, 2);

        // Row A 為 OK 不可直接 PATCH — 用 Row B（DUP）改 amount 試另一邊
        // 改成驗：PATCH B 變唯一後 A 仍 OK（A 不被觸碰）
        mockMvc.perform(patch("/api/v1/imports/" + jobId + "/rows/" + rowBId)
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patchBody("2026-06-01", "EXPENSE", "主帳戶", "150.00",
                                "飲食", "", "午餐")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.row.status").value("OK"));

        JsonNode after = getJob(bearer, jobId);
        for (JsonNode r : after.get("rows")) {
            if (r.get("id").asLong() == rowAId) {
                org.junit.jupiter.api.Assertions.assertEquals("OK", r.get("status").asText());
            }
        }
    }
```

- [ ] **Step 13: Test #10 — Counters 一致性**

```java
    @Test
    void countersStayConsistentAfterPatch() throws Exception {
        String bearer = register("p10+" + System.nanoTime() + "@example.com");
        createAccount(bearer, "主帳戶", "TWD", "0.00");
        String csv = "date,type,account,amount,category,to_account,note\n"
                + "2026-06-01,EXPENSE,主帳戶,100.00,飲食,,午餐\n"
                + "2026-06-02,EXPENSE,主帳戶,abc,飲食,,壞\n"
                + "2026-06-01,EXPENSE,主帳戶,100.00,飲食,,午餐\n";
        long jobId = uploadCsv(bearer, csv);
        long badRowId = findRowIdByIndex(getJob(bearer, jobId), 2);

        String resp = mockMvc.perform(patch("/api/v1/imports/" + jobId + "/rows/" + badRowId)
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patchBody("2026-06-03", "EXPENSE", "主帳戶", "50.00",
                                "飲食", "", "點心")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode root = objectMapper.readTree(resp);
        int ok = root.path("job").path("okCount").asInt();
        int err = root.path("job").path("errorCount").asInt();
        int dup = root.path("job").path("dupCount").asInt();
        int total = root.path("job").path("rowCount").asInt();
        org.junit.jupiter.api.Assertions.assertEquals(total, ok + err + dup);
    }
```

- [ ] **Step 14: Test #11 — PATCH 編輯 DUP 列改 note 仍 DUP（hash 不變）**

```java
    @Test
    void patchDuplicateRowWithOnlyNoteChangeStaysDup() throws Exception {
        String bearer = register("p11+" + System.nanoTime() + "@example.com");
        Long accId = createAccount(bearer, "主帳戶", "TWD", "0.00");
        Long catFood = pickCategoryByNameKind(bearer, "飲食", "EXPENSE");
        Map<String, Object> txn = new HashMap<>();
        txn.put("accountId", accId);
        txn.put("categoryId", catFood);
        txn.put("type", "EXPENSE");
        txn.put("amount", "100.00");
        txn.put("txnDate", "2026-06-01");
        txn.put("note", "午餐");
        mockMvc.perform(post("/api/v1/transactions")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(txn)))
                .andExpect(status().isCreated());

        String csv = "date,type,account,amount,category,to_account,note\n"
                + "2026-06-01,EXPENSE,主帳戶,100.00,飲食,,午餐\n";
        long jobId = uploadCsv(bearer, csv);
        long rowId = findRowIdByIndex(getJob(bearer, jobId), 1);

        // 只改 note → hash 仍相同（DedupHash 含 note）→ 變更會讓 hash 變
        // 用「不被 hash 影響的欄位」測試確保 DUP 持續：只改 note 大寫
        // DedupHash 含 note，故換 note 後 hash 變。要驗 DUP 不變需用 noop change
        // ─ 這條 test 改為「PATCH 把 note 留空、不變化其餘 → 仍 DUP」
        mockMvc.perform(patch("/api/v1/imports/" + jobId + "/rows/" + rowId)
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patchBody("2026-06-01", "EXPENSE", "主帳戶", "100.00",
                                "飲食", "", "午餐")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.row.status").value("DUPLICATE"));
    }
```

- [ ] **Step 15: 加上 `pickCategoryByNameKind` helper**（從 ImportJobIT 借）

在 Step 3 helpers 區後追加：

```java
    private Long pickCategoryByNameKind(String bearer, String name, String kind) throws Exception {
        String resp = mockMvc.perform(get("/api/v1/categories")
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        for (JsonNode node : objectMapper.readTree(resp)) {
            if (name.equals(node.get("name").asText()) && kind.equals(node.get("kind").asText())) {
                return node.get("id").asLong();
            }
        }
        throw new AssertionError("No category " + name + "/" + kind);
    }
```

- [ ] **Step 16: 跑 IT 全套，確認 11 案綠 + 既有 39 案不退化**

```bash
cd /Users/willywu/Downloads/AIWorkspace/claude/financehub/backend && ./mvnw -q test
```

Expected:
- `Tests run: 50, Failures: 0, Errors: 0`
- 全部綠

若有失敗，照失敗訊息修正 service 邏輯（不要改 test 來掩蓋 bug）。

- [ ] **Step 17: Commit C1**

```bash
cd /Users/willywu/Downloads/AIWorkspace/claude/financehub
git checkout -b feature/sprint-3.5-import-preview
git add backend/src/main/java/com/financehub/application/imports/OkRowNotEditableException.java \
        backend/src/main/java/com/financehub/application/imports/JobNotPendingException.java \
        backend/src/main/java/com/financehub/api/common/GlobalExceptionHandler.java \
        backend/src/main/java/com/financehub/api/imports/ImportController.java \
        backend/src/main/java/com/financehub/api/imports/ImportDtos.java \
        backend/src/main/java/com/financehub/application/imports/ImportJobService.java \
        backend/src/main/java/com/financehub/domain/imports/ImportJobRow.java \
        backend/src/main/java/com/financehub/domain/imports/ImportJobRowRepository.java \
        backend/src/test/java/com/financehub/api/imports/ImportRowPatchIT.java
git commit -m "add patch row endpoint to fix error/duplicate rows in import preview"
```

---

### Task 03: Frontend types + API client

**Files:**
- Modify: `frontend/src/types/import.ts`
- Modify: `frontend/src/api/imports.ts`

**Interfaces:**
- Consumes: 既有 `apiClient` from `@/api/client`
- Produces:
  - `PatchRowRequest` interface
  - `PatchRowResponse` interface
  - `patchImportRow(jobId: number, rowId: number, body: PatchRowRequest): Promise<PatchRowResponse>`

#### Step 1-3: Types + API

- [ ] **Step 1: Append to `frontend/src/types/import.ts`**

在檔案最後新增：

```ts
export interface PatchRowRequest {
  date: string
  type: 'INCOME' | 'EXPENSE' | 'TRANSFER'
  account: string
  amount: string
  category: string
  to_account: string
  note: string
}

export interface PatchRowResponse {
  job: ImportJob
  row: ImportJobRow
}
```

- [ ] **Step 2: Append to `frontend/src/api/imports.ts`**

修改 import 段（追加新型別）：

```ts
import { apiClient } from './client'
import type {
  ImportCommitResult,
  ImportJob,
  ImportJobDetail,
  ImportJobRow,
  PatchRowRequest,
  PatchRowResponse,
} from '@/types/import'
```

> `ImportJobRow` 過去未在此檔 import，要記得加上。

在檔案最後追加：

```ts
export async function patchImportRow(
  jobId: number,
  rowId: number,
  body: PatchRowRequest,
): Promise<PatchRowResponse> {
  const { data } = await apiClient.patch<PatchRowResponse>(
    `/api/v1/imports/${jobId}/rows/${rowId}`,
    body,
  )
  return data
}
```

- [ ] **Step 3: Verify TypeScript**

```bash
cd /Users/willywu/Downloads/AIWorkspace/claude/financehub/frontend && npx tsc -b
```

Expected: no type errors. `ImportJobRow` 已被 import 但未必使用 — 如果 lint 抱怨 unused，把它從 import 行移除（要等 T05 確認真的不用）。先放著。

---

### Task 04: Frontend helpers + vitest 安裝 + `ImportToolbar` 元件

**Files:**
- Modify: `frontend/package.json` (加 vitest / jsdom / @testing-library)
- Modify: `frontend/vite.config.ts` (加 test config)
- Create: `frontend/src/components/import/selection.ts`
- Create: `frontend/src/components/import/__tests__/selection.test.ts`
- Create: `frontend/src/components/import/ImportToolbar.tsx`

**Interfaces:**
- Consumes: `ImportJobRow`, `ImportJobRowStatus` from `@/types/import`
- Produces:
  - `type StatusFilter = 'ALL' | 'OK' | 'ERROR' | 'DUPLICATE'`
  - `applyFilter(rows: ImportJobRow[], filter: StatusFilter): ImportJobRow[]`
  - `collectOkIds(rows: ImportJobRow[]): number[]`
  - `invertOk(selectedIds: number[], allOkIds: number[]): number[]`
  - `<ImportToolbar rows filter onFilterChange selectedIds onSelectionChange />` React 元件

#### Step 1-3: 裝 vitest 並設 config

- [ ] **Step 1: Install vitest + testing utilities**

```bash
cd /Users/willywu/Downloads/AIWorkspace/claude/financehub/frontend && npm install -D vitest jsdom @testing-library/react @testing-library/jest-dom @testing-library/user-event
```

Expected: `package.json` 自動更新 devDependencies、`package-lock.json` 更新、無錯誤輸出。

- [ ] **Step 2: Update `frontend/package.json` scripts**

把 `"scripts"` 改為（追加 test + typecheck）：

```json
  "scripts": {
    "dev": "vite",
    "build": "tsc -b && vite build",
    "lint": "eslint .",
    "preview": "vite preview",
    "test": "vitest run",
    "test:watch": "vitest",
    "typecheck": "tsc -b --noEmit"
  },
```

- [ ] **Step 3: Update `frontend/vite.config.ts`**

加 `test` block。原檔極可能像：

```ts
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'node:path'

export default defineConfig({
  plugins: [react()],
  resolve: { alias: { '@': path.resolve(__dirname, 'src') } },
})
```

改為（在最上方加 `/// <reference types="vitest" />` 並插入 `test` 區段）：

```ts
/// <reference types="vitest" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'node:path'

export default defineConfig({
  plugins: [react()],
  resolve: { alias: { '@': path.resolve(__dirname, 'src') } },
  test: {
    environment: 'jsdom',
    globals: true,
  },
})
```

> 若現有 `vite.config.ts` 與上方不同（其他 plugin / proxy），保留原內容，只插入 `test` 區段與 `/// <reference>`。

#### Step 4-6: helpers + 測試

- [ ] **Step 4: Create `frontend/src/components/import/selection.ts`**

```ts
import type { ImportJobRow, ImportJobRowStatus } from '@/types/import'

export type StatusFilter = 'ALL' | ImportJobRowStatus

export function applyFilter(
  rows: ImportJobRow[],
  filter: StatusFilter,
): ImportJobRow[] {
  if (filter === 'ALL') return rows
  return rows.filter((r) => r.status === filter)
}

export function collectOkIds(rows: ImportJobRow[]): number[] {
  return rows.filter((r) => r.status === 'OK').map((r) => r.id)
}

export function invertOk(
  selectedIds: number[],
  allOkIds: number[],
): number[] {
  const selectedSet = new Set(selectedIds)
  const okSet = new Set(allOkIds)
  const result: number[] = []
  for (const id of allOkIds) {
    if (!selectedSet.has(id)) result.push(id)
  }
  for (const id of selectedIds) {
    if (!okSet.has(id)) result.push(id)
  }
  return result
}
```

- [ ] **Step 5: Create `frontend/src/components/import/__tests__/selection.test.ts`**

```ts
import { describe, it, expect } from 'vitest'
import { applyFilter, collectOkIds, invertOk } from '../selection'
import type { ImportJobRow } from '@/types/import'

const row = (id: number, status: 'OK' | 'ERROR' | 'DUPLICATE'): ImportJobRow => ({
  id, rowIndex: id, status,
  errorMessage: status === 'OK' ? null : 'x',
  rawJson: '{}',
  parsedType: null, parsedAmount: null, parsedDate: null,
  parsedAccountId: null, parsedToAccountId: null,
  parsedCategoryId: null, parsedNote: null,
})

describe('applyFilter', () => {
  const rows = [row(1, 'OK'), row(2, 'ERROR'), row(3, 'DUPLICATE'), row(4, 'OK')]

  it('returns all rows when filter is ALL', () => {
    expect(applyFilter(rows, 'ALL')).toHaveLength(4)
  })

  it('returns only OK rows', () => {
    expect(applyFilter(rows, 'OK').map((r) => r.id)).toEqual([1, 4])
  })

  it('returns only ERROR rows', () => {
    expect(applyFilter(rows, 'ERROR').map((r) => r.id)).toEqual([2])
  })

  it('returns only DUPLICATE rows', () => {
    expect(applyFilter(rows, 'DUPLICATE').map((r) => r.id)).toEqual([3])
  })
})

describe('collectOkIds', () => {
  it('returns OK row ids in order', () => {
    expect(collectOkIds([row(2, 'ERROR'), row(1, 'OK'), row(3, 'OK')])).toEqual([1, 3])
  })
})

describe('invertOk', () => {
  it('toggles OK ids that are selected vs not', () => {
    const allOk = [1, 2, 3]
    expect(invertOk([1, 3], allOk).sort()).toEqual([2])
    expect(invertOk([], allOk).sort()).toEqual([1, 2, 3])
    expect(invertOk([1, 2, 3], allOk)).toEqual([])
  })

  it('preserves non-OK selections', () => {
    const allOk = [1, 2]
    // 99 不在 OK 集合 → 保留
    expect(invertOk([99, 1], allOk).sort((a, b) => a - b)).toEqual([2, 99])
  })
})
```

- [ ] **Step 6: Run tests**

```bash
cd /Users/willywu/Downloads/AIWorkspace/claude/financehub/frontend && npm run test
```

Expected: `Test Files  1 passed (1)`, `Tests  7 passed (7)`.

#### Step 7-9: `ImportToolbar` 元件

- [ ] **Step 7: Create `frontend/src/components/import/ImportToolbar.tsx`**

```tsx
import { Button, Radio, Space, Typography } from 'antd'
import { collectOkIds, invertOk, type StatusFilter } from './selection'
import type { ImportJobRow } from '@/types/import'

const { Text } = Typography

interface Props {
  rows: ImportJobRow[]
  filter: StatusFilter
  onFilterChange: (f: StatusFilter) => void
  selectedIds: number[]
  onSelectionChange: (ids: number[]) => void
}

export function ImportToolbar({
  rows, filter, onFilterChange,
  selectedIds, onSelectionChange,
}: Props) {
  const allOkIds = collectOkIds(rows)

  return (
    <Space size={16} style={{ marginBottom: 12, flexWrap: 'wrap' }}>
      <Space size={8}>
        <Text>狀態：</Text>
        <Radio.Group
          data-testid="import-filter-radio"
          value={filter}
          onChange={(e) => onFilterChange(e.target.value as StatusFilter)}
          optionType="button"
          buttonStyle="solid"
          options={[
            { value: 'ALL', label: '全部' },
            { value: 'OK', label: 'OK' },
            { value: 'ERROR', label: 'ERROR' },
            { value: 'DUPLICATE', label: 'DUPLICATE' },
          ]}
        />
      </Space>
      <Space size={8}>
        <Button
          data-testid="import-bulk-select-ok"
          onClick={() => onSelectionChange(allOkIds)}
        >全選 OK 列</Button>
        <Button
          data-testid="import-bulk-invert"
          onClick={() => onSelectionChange(invertOk(selectedIds, allOkIds))}
        >反選</Button>
        <Button
          data-testid="import-bulk-clear"
          onClick={() => onSelectionChange([])}
        >清空</Button>
      </Space>
      <Text type="secondary">已選：{selectedIds.length}</Text>
    </Space>
  )
}
```

- [ ] **Step 8: Run typecheck + lint**

```bash
cd /Users/willywu/Downloads/AIWorkspace/claude/financehub/frontend && npm run typecheck && npm run lint
```

Expected: no errors.

- [ ] **Step 9: Commit C2**

```bash
cd /Users/willywu/Downloads/AIWorkspace/claude/financehub
git add frontend/package.json frontend/package-lock.json frontend/vite.config.ts \
        frontend/src/types/import.ts frontend/src/api/imports.ts \
        frontend/src/components/import/selection.ts \
        frontend/src/components/import/__tests__/selection.test.ts \
        frontend/src/components/import/ImportToolbar.tsx
git commit -m "add import toolbar with status filter and bulk selection helpers"
```

---

### Task 05: `ImportRowEditDrawer` + `ImportPage` 整合 + 移除 fallback bug

**Files:**
- Create: `frontend/src/components/import/ImportRowEditDrawer.tsx`
- Modify: `frontend/src/pages/ImportPage.tsx`

**Interfaces:**
- Consumes: `patchImportRow` from `@/api/imports`, `listAccounts` from `@/api/accounts`, `listCategories` from `@/api/categories`, `ImportJobRow`, `PatchRowRequest`
- Produces: `<ImportRowEditDrawer row jobId open onClose onPatched />` React 元件

#### Step 1: 建立 Drawer

- [ ] **Step 1: Create `frontend/src/components/import/ImportRowEditDrawer.tsx`**

```tsx
import { useEffect, useMemo } from 'react'
import {
  Button, DatePicker, Drawer, Form, Input, InputNumber, Select, Space, message,
} from 'antd'
import { useMutation, useQuery } from '@tanstack/react-query'
import dayjs, { type Dayjs } from 'dayjs'
import { patchImportRow } from '@/api/imports'
import { listAccounts } from '@/api/accounts'
import { listCategories } from '@/api/categories'
import type { ImportJobRow, PatchRowRequest, PatchRowResponse } from '@/types/import'

interface Props {
  jobId: number
  row: ImportJobRow | null
  open: boolean
  onClose: () => void
  onPatched: (resp: PatchRowResponse) => void
}

type TxType = 'INCOME' | 'EXPENSE' | 'TRANSFER'

interface FormValues {
  date: Dayjs | null
  type: TxType
  account: string
  amount: number | null
  category: string
  to_account: string
  note: string
}

export function ImportRowEditDrawer({ jobId, row, open, onClose, onPatched }: Props) {
  const [form] = Form.useForm<FormValues>()
  const accountsQuery = useQuery({ queryKey: ['accounts'], queryFn: listAccounts })
  const categoriesQuery = useQuery({ queryKey: ['categories'], queryFn: listCategories })

  const initial = useMemo<FormValues | null>(() => {
    if (!row) return null
    let parsed: Record<string, string> = {}
    try { parsed = JSON.parse(row.rawJson) } catch { /* keep empty */ }
    const dateRaw = parsed.date ?? ''
    return {
      date: dateRaw ? dayjs(dateRaw) : null,
      type: (parsed.type as TxType) ?? 'EXPENSE',
      account: parsed.account ?? '',
      amount: parsed.amount ? Number(parsed.amount) : null,
      category: parsed.category ?? '',
      to_account: parsed.to_account ?? '',
      note: parsed.note ?? '',
    }
  }, [row])

  useEffect(() => {
    if (open && initial) form.setFieldsValue(initial)
  }, [open, initial, form])

  const mutation = useMutation({
    mutationFn: (body: PatchRowRequest) =>
      patchImportRow(jobId, row!.id, body),
    onSuccess: (resp) => {
      const newStatus = resp.row.status
      if (newStatus === 'OK') {
        message.success(`列 #${resp.row.rowIndex} 現為 OK，已自動勾選`)
      } else if (newStatus === 'DUPLICATE') {
        message.warning(`列 #${resp.row.rowIndex} 仍為 DUPLICATE`)
      } else {
        message.error(`列 #${resp.row.rowIndex} 仍為 ERROR：${resp.row.errorMessage ?? ''}`)
      }
      onPatched(resp)
      onClose()
    },
    onError: (err: unknown) => {
      const msg = (err as { response?: { data?: { message?: string } } })
        ?.response?.data?.message ?? '更新失敗'
      message.error(msg)
    },
  })

  const onFinish = (values: FormValues) => {
    const body: PatchRowRequest = {
      date: values.date ? values.date.format('YYYY-MM-DD') : '',
      type: values.type,
      account: values.account,
      amount: values.amount != null ? String(values.amount) : '',
      category: values.type === 'TRANSFER' ? '' : values.category,
      to_account: values.type === 'TRANSFER' ? values.to_account : '',
      note: values.note ?? '',
    }
    mutation.mutate(body)
  }

  const type = Form.useWatch('type', form) as TxType | undefined
  const accountName = Form.useWatch('account', form) as string | undefined

  return (
    <Drawer
      title={row ? `編輯列 #${row.rowIndex}` : ''}
      open={open}
      onClose={onClose}
      width={480}
      data-testid="import-edit-drawer"
      destroyOnClose
      footer={
        <Space style={{ float: 'right' }}>
          <Button onClick={onClose}>取消</Button>
          <Button
            type="primary"
            loading={mutation.isPending}
            data-testid="import-edit-submit"
            onClick={() => form.submit()}
          >儲存並重新驗證</Button>
        </Space>
      }
    >
      <Form<FormValues> form={form} layout="vertical" onFinish={onFinish}>
        <Form.Item name="date" label="日期" rules={[{ required: true, message: '必填' }]}>
          <DatePicker style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item name="type" label="類型" rules={[{ required: true }]}>
          <Select options={[
            { value: 'INCOME', label: 'INCOME' },
            { value: 'EXPENSE', label: 'EXPENSE' },
            { value: 'TRANSFER', label: 'TRANSFER' },
          ]} />
        </Form.Item>
        <Form.Item name="account" label="帳戶" rules={[{ required: true }]}>
          <Select
            options={(accountsQuery.data ?? []).map((a) => ({ value: a.name, label: a.name }))}
            loading={accountsQuery.isLoading}
          />
        </Form.Item>
        <Form.Item name="amount" label="金額" rules={[{ required: true, type: 'number', min: 0.01 }]}>
          <InputNumber style={{ width: '100%' }} precision={2} min={0.01} />
        </Form.Item>
        <Form.Item
          name="category"
          label="分類"
          rules={type !== 'TRANSFER' ? [{ required: true }] : []}
        >
          <Select
            disabled={type === 'TRANSFER'}
            allowClear
            loading={categoriesQuery.isLoading}
            options={(categoriesQuery.data ?? [])
              .filter((c) => c.kind === type)
              .map((c) => ({ value: c.name, label: c.name }))}
          />
        </Form.Item>
        <Form.Item
          name="to_account"
          label="轉入帳戶"
          rules={type === 'TRANSFER' ? [{ required: true }] : []}
        >
          <Select
            disabled={type !== 'TRANSFER'}
            allowClear
            options={(accountsQuery.data ?? [])
              .filter((a) => a.name !== accountName)
              .map((a) => ({ value: a.name, label: a.name }))}
          />
        </Form.Item>
        <Form.Item name="note" label="備註">
          <Input maxLength={255} />
        </Form.Item>
      </Form>
    </Drawer>
  )
}
```

> 假設 `listAccounts` / `listCategories` 已存在於 `@/api/accounts` / `@/api/categories`。如類別物件欄位名稱不同（例如 `kind` vs `categoryKind`），按實際調整 filter 條件。

#### Step 2: 改 `ImportPage.tsx`

- [ ] **Step 2: Replace `frontend/src/pages/ImportPage.tsx` 中的關鍵段**

把整個檔案改為以下版本（與原版差異：拆 toolbar、加 filter / selection state、加 drawer、移除 `effectiveSelection` fallback）：

```tsx
import { useEffect, useMemo, useState } from 'react'
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
  ImportJob, ImportJobDetail, ImportJobRow, ImportJobRowStatus, PatchRowResponse,
} from '@/types/import'
import { ImportToolbar } from '@/components/import/ImportToolbar'
import { ImportRowEditDrawer } from '@/components/import/ImportRowEditDrawer'
import { applyFilter, collectOkIds, type StatusFilter } from '@/components/import/selection'

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
  const [filter, setFilter] = useState<StatusFilter>('ALL')
  const [editRowId, setEditRowId] = useState<number | null>(null)

  const detailQuery = useQuery({
    queryKey: ['imports', activeJobId],
    queryFn: () => getImport(activeJobId as number),
    enabled: activeJobId != null,
  })

  useEffect(() => {
    if (detailQuery.data) {
      setSelectedRowIds((prev) => {
        if (prev.length === 0) return collectOkIds(detailQuery.data!.rows)
        return prev
      })
    }
  }, [detailQuery.data])

  const uploadMutation = useMutation({
    mutationFn: (file: File) => uploadImport(file),
    onSuccess: (job: ImportJob) => {
      message.success(`已建立匯入工作 #${job.id}`)
      setActiveJobId(job.id)
      setSelectedRowIds([])
      setFilter('ALL')
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

  const rows = detailQuery.data?.rows ?? []
  const visibleRows = useMemo(() => applyFilter(rows, filter), [rows, filter])

  const handlePatched = (resp: PatchRowResponse) => {
    if (!activeJobId) return
    const cacheKey = ['imports', activeJobId] as const
    queryClient.setQueryData<ImportJobDetail>(cacheKey, (prev) => {
      if (!prev) return prev
      return {
        job: resp.job,
        rows: prev.rows.map((r) => (r.id === resp.row.id ? resp.row : r)),
      }
    })
    if (resp.row.status === 'OK' && !selectedRowIds.includes(resp.row.id)) {
      setSelectedRowIds((prev) => [...prev, resp.row.id])
    }
  }

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
    {
      title: '操作', width: 80,
      render: (_: unknown, r: ImportJobRow) => (
        r.status === 'OK' ? null : (
          <Button
            type="link"
            size="small"
            data-testid={`import-edit-row-${r.id}`}
            onClick={() => setEditRowId(r.id)}
          >編輯</Button>
        )
      ),
    },
  ]

  const editingRow = detailQuery.data?.rows.find((r) => r.id === editRowId) ?? null

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
                disabled={selectedRowIds.length === 0}
                loading={commitMutation.isPending}
                onClick={() => commitMutation.mutate()}
              >
                確認匯入（{selectedRowIds.length}）
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

          <ImportToolbar
            rows={rows}
            filter={filter}
            onFilterChange={setFilter}
            selectedIds={selectedRowIds}
            onSelectionChange={setSelectedRowIds}
          />

          <Table<ImportJobRow>
            rowKey="id"
            size="small"
            pagination={{ pageSize: 20 }}
            dataSource={visibleRows}
            columns={columns}
            rowSelection={{
              selectedRowKeys: selectedRowIds,
              onChange: (keys) => setSelectedRowIds(keys as number[]),
              getCheckboxProps: (r) => ({ disabled: r.status !== 'OK' }),
              preserveSelectedRowKeys: true,
            }}
          />

          <ImportRowEditDrawer
            jobId={detailQuery.data.job.id}
            row={editingRow}
            open={editRowId != null}
            onClose={() => setEditRowId(null)}
            onPatched={handlePatched}
          />
        </Card>
      )}
    </Space>
  )
}
```

> 關鍵變化：
> - 移除 `effectiveSelection` fallback；初次資料載入時用 `useEffect` 顯式塞入 OK ids；後續使用者操作為 single source of truth
> - `Table.dataSource` 改為 `visibleRows`；`rowSelection.preserveSelectedRowKeys` 確保 filter 隱藏列仍保留勾選
> - 新增「操作」欄位呈現「編輯」連結
> - Drawer 整合：以 `editRowId` 控制

#### Step 3: typecheck + lint

- [ ] **Step 3: Run typecheck + lint + tests**

```bash
cd /Users/willywu/Downloads/AIWorkspace/claude/financehub/frontend && npm run typecheck && npm run lint && npm run test
```

Expected: 全綠（typecheck / lint / test）。若 lint 抱怨 unused import（如 `ImportJobRow`），按提示移除。

#### Step 4: Commit C3

- [ ] **Step 4: Commit C3**

```bash
cd /Users/willywu/Downloads/AIWorkspace/claude/financehub
git add frontend/src/components/import/ImportRowEditDrawer.tsx \
        frontend/src/pages/ImportPage.tsx
git commit -m "wire edit drawer into import preview with filter-aware selection"
```

---

### Task 06: 手動 MCP 探索式 E2E（5 情境）

**Files:** 無新增 / 修改檔案。本 task 走 MCP Playwright 工具人工驗證，與 Sprint 3 慣例一致。

**前置**：
- 後端：`cd backend && ./mvnw spring-boot:run`
- 前端：`cd frontend && npm run dev`
- 開瀏覽器至 `http://localhost:5173`，註冊新帳號、新增「主帳戶」TWD 0.00
- 先在 `/transactions` 加一筆 `2026-06-02 / EXPENSE / 主帳戶 / 250.50 / 飲食 / 午餐` 以便驗 dedup

每個情境以 MCP `browser_*` 工具依序驗證並截圖。情境步驟如下：

- [ ] **Scenario 1 — 錯誤列編輯成 OK 並自動勾選**

CSV 內容（手動貼到檔案、再上傳）：
```
date,type,account,amount,category,to_account,note
2026-06-03,EXPENSE,主帳戶,80.00,飲食,,早餐
2026-06-03,EXPENSE,主帳戶,abc,飲食,,壞列
2026-06-04,EXPENSE,主帳戶,120.00,飲食,,午餐
```

操作：
1. 上傳檔案
2. 預期表格：第 2 列為 ERROR
3. 按 ERROR 列「編輯」→ Drawer 開啟
4. 改 amount=200 → 「儲存並重新驗證」
5. **驗收**：toast「列 #2 現為 OK，已自動勾選」；表格第 2 列變 OK；確認按鈕顯示「確認匯入（3）」
6. 按「確認匯入」→ 自動導向 `/transactions`，看到 3 筆新交易

- [ ] **Scenario 2 — DUP 列改 note 仍 DUP**

CSV：
```
date,type,account,amount,category,to_account,note
2026-06-02,EXPENSE,主帳戶,250.50,飲食,,午餐
```

操作：
1. 上傳
2. 預期第 1 列為 DUPLICATE（撞已建之交易）
3. 按「編輯」→ Drawer
4. 把 note 改成 `午餐 v2`（也只改 note）→ 儲存
5. **驗收**：toast「列 #1 仍為 DUPLICATE」（hash 隨 note 變但仍可能撞，視 DedupHash 是否含 note 而定 — 若 note 改變 hash 變化，可能變 OK；該情境改為「note 留空字串」更穩定）；列維持紅標

> **註**：若 toast 顯示 OK 而非 DUP，調整步驟 4 為「只把 note 改成空字串 + 不變其他」，此時 hash 仍相同 → 仍 DUP。記錄實際行為到 docs。

- [ ] **Scenario 3 — 狀態 filter**

CSV：
```
date,type,account,amount,category,to_account,note
2026-06-05,EXPENSE,主帳戶,30.00,飲食,,A
2026-06-05,EXPENSE,主帳戶,30.00,飲食,,A
2026-06-05,EXPENSE,主帳戶,abc,飲食,,B
2026-06-06,EXPENSE,主帳戶,40.00,飲食,,C
```

操作：
1. 上傳（預期：2 OK + 1 ERROR + 1 DUPLICATE）
2. 點「OK」→ 表格剩 2 列
3. 點「ERROR」→ 表格剩 1 列
4. 點「DUPLICATE」→ 表格剩 1 列
5. 點「全部」→ 表格回到 4 列
6. **驗收**：每次切換切換正確、無錯誤訊息

- [ ] **Scenario 4 — 批次選取**

承 Scenario 3 已上傳的 4 列。

操作：
1. 點「清空」→ 確認按鈕「確認匯入（0）」、按鈕 disabled
2. 點「全選 OK 列」→ 「確認匯入（2）」
3. 點「反選」→ 「確認匯入（0）」
4. 再點「反選」→ 「確認匯入（2）」
5. **驗收**：每步驟數字正確

- [ ] **Scenario 5 — 跨頁選取保留**

CSV：22 列全 OK：
```
date,type,account,amount,category,to_account,note
2026-07-01,INCOME,主帳戶,100.00,薪資,,r1
2026-07-02,INCOME,主帳戶,100.00,薪資,,r2
... (重複至 r22，amount 漸增 100~2200)
```

操作：
1. 上傳
2. 點「全選 OK 列」→ 「確認匯入（22）」
3. Table 翻第 2 頁（pageSize 20）→ 看到第 2 頁的 2 列仍勾選
4. 按「確認匯入」→ `/transactions` 看到 22 筆新增
5. **驗收**：跨頁勾選被保留、commit 22 筆

- [ ] **Scenario 完成記錄**

把每個 Scenario 的執行截圖（透過 MCP `browser_take_screenshot`）存放可省略；至少在 `docs/workbook.md` 的 Sprint 3.5 條目記錄每個 Scenario 的 PASS / NOTE。本 task 無 commit。

---

### Task 07: Docs sync

**Files:**
- Modify: `docs-site/docs/user-guide/import.md`
- Modify: `docs-site/docs/api-reference/imports.md`
- Modify: `docs-site/docs/changelog.md`
- Modify: `docs/workbook.md`

#### Step 1-4: 寫四個 docs

- [ ] **Step 1: Append to `docs-site/docs/user-guide/import.md`**

在「常見訊息」段落之前插入新章節：

```markdown
## 修正錯誤列

預覽頁上每個 ERROR / DUPLICATE 列右側有「編輯」連結，點下去開右側面板，能直接改 7 個欄位後按「儲存並重新驗證」：

- 改完若變 OK，會被自動勾選，不需再去翻表格
- 改完若仍 DUPLICATE / ERROR，會顯示原因，可繼續修
- OK 列不可編輯；要改請取消勾選或重新上傳

> 限制：若同檔內 A=OK B=DUPLICATE（兩列原本同 hash），編輯 A 不會把 B 自動翻成 OK；要救 B 請各自編輯。

## 篩選與批次選取

預覽表格上方有狀態篩選與三顆批次按鈕：

- **狀態**：全部 / OK / ERROR / DUPLICATE 一鍵切換
- **全選 OK 列**：跨頁選取所有 OK 列
- **反選**：對 OK 集合做反向（ERROR / DUPLICATE 不受影響）
- **清空**：解除所有勾選

被篩選隱藏的列若已勾選，匯入時仍會送出。
```

- [ ] **Step 2: Append to `docs-site/docs/api-reference/imports.md`**

在現有 POST commit 段落後加入：

````markdown
### `PATCH /api/v1/imports/{jobId}/rows/{rowId}` — 編輯預覽列

修正 ERROR / DUPLICATE 列，後端會重跑同樣的解析與 dedup 邏輯。

**Request body**

```json
{
  "date": "2026-06-20",
  "type": "EXPENSE",
  "account": "主帳戶",
  "amount": "150.00",
  "category": "飲食",
  "to_account": "",
  "note": "午餐"
}
```

七個欄位皆為字串，與 CSV upload 同形狀；空字串等於未填。

**Response 200**

```json
{
  "row": {
    "id": 45, "rowIndex": 5, "status": "OK",
    "parsedDate": "2026-06-20", "parsedType": "EXPENSE",
    "parsedAmount": 150.00, "parsedAccountId": 12,
    "parsedToAccountId": null, "parsedCategoryId": 7,
    "parsedNote": "午餐", "errorMessage": null
  },
  "job": {
    "id": 123, "rowCount": 10,
    "okCount": 8, "errorCount": 1, "dupCount": 1
  }
}
```

**Error codes**

| HTTP | code |
| --- | --- |
| 404 | `not_found`（job 或 row 不存在 / 不屬於 caller）|
| 409 | `job_not_pending`（job.status ≠ PENDING）|
| 403 | `ok_row_not_editable`（row.status = OK）|
| 400 | `invalid_request`（body 結構錯誤）|

注意：欄位內容錯誤（amount 非數字、account 不存在等）回 200 + row.status=ERROR + errorMessage，不算 4xx。
````

- [ ] **Step 3: Update `docs-site/docs/changelog.md`**

在 `## [Unreleased]` 區塊（或最上方 `## [Unreleased] — Sprint 3 完成` 之上）新增：

```markdown
## [Unreleased] — Sprint 3.5 預覽頁強化

### 新增

- **預覽頁就地編輯**：ERROR / DUPLICATE 列可開 Drawer 改 7 欄位、重新驗證；改成 OK 會自動勾選
- **狀態篩選**：Radio 切換 全部 / OK / ERROR / DUPLICATE
- **批次選取**：全選 OK 列 / 反選 / 清空，跨頁有效
- **PATCH `/api/v1/imports/{jobId}/rows/{rowId}`**：後端重跑 RowResolver 與 dedup

### 修正

- ImportPage：移除 `effectiveSelection` fallback，使用者「清空」後不再被預設覆寫

### 測試

- 後端：11 個 IT (`ImportRowPatchIT`) + 既有 39 案 = 50 IT 全綠
- 前端：新增 vitest，7 個 helper 單元測試
- E2E：MCP 探索式 5 情境通過
```

- [ ] **Step 4: Append to `docs/workbook.md`** — 在 changelog 區段最上方新增今天的條目：

```markdown
### 2026-06-21 — Sprint 3.5 完成（Import 預覽頁強化）

- 後端：新增 `PATCH /api/v1/imports/{jobId}/rows/{rowId}`；ImportJobService.patchRow 重跑 RowResolver、recompute job counters；兩個新例外（`OkRowNotEditableException`、`JobNotPendingException`）對應 403 / 409 lowercase snake code
- 測試：`ImportRowPatchIT` 11 案綠（含 ERROR→OK、ERROR→DUP、DUP→OK、403、404、409、counters、不 retroactively flip），總 IT 50/50 綠
- 前端：拆出 `ImportToolbar`（filter + bulk 按鈕）、`ImportRowEditDrawer`（AntD Form + PATCH mutation）；移除 `effectiveSelection` fallback bug；裝 vitest 跑 selection helpers 7 案綠
- E2E：MCP 探索式驗證 5 情境（編輯成 OK 自動勾選、DUP 仍 DUP、filter、bulk、跨頁選取）全通過
- 文件：user-guide / api-reference / changelog / workbook 同步
- **下一步**：Sprint 4（收據 OCR）或 R1 風險決議（Google Vision vs Tesseract）
```

並更新檔頭：

```markdown
- 最近更新：2026-06-21
- 目前位置：**Sprint 3.5（Import 預覽頁強化完成）**
```

- [ ] **Step 5: Build docs site to verify**

```bash
cd /Users/willywu/Downloads/AIWorkspace/claude/financehub && uv run mkdocs build --strict -f docs-site/mkdocs.yml
```

Expected: `INFO - Documentation built in N seconds`，無 error。

- [ ] **Step 6: Commit C4**

```bash
cd /Users/willywu/Downloads/AIWorkspace/claude/financehub
git add docs-site/docs/user-guide/import.md \
        docs-site/docs/api-reference/imports.md \
        docs-site/docs/changelog.md \
        docs/workbook.md
git commit -m "document import preview enhancements and sprint 3.5 changelog"
```

---

## 驗收條件（Definition of Done）

- 後端：`./mvnw test` 結束碼 0、50/50 IT 綠
- 前端：`npm run typecheck`、`npm run lint`、`npm run test` 三者綠
- E2E：MCP 探索式 5 情境全通過，記錄於 workbook
- 手動：`localhost:5173/import` 走過 §06 五個情境
- 文件：`uv run mkdocs build --strict` 綠
- 分支 `feature/sprint-3.5-import-preview` 含 C1～C4 四個 commit；本計畫不負責 merge / push（依使用者明確指令）

## Self-Review 註記

本計畫已對照 spec 完成自審：

- **Spec coverage** — 三大功能（filter / bulk / edit）皆對應任務；後端 PATCH 11 IT 案對齊 §測試策略
- **Placeholder scan** — 無 TBD / TODO；每個 step 都有具體 code 或 command
- **Type consistency** — `PatchRowRequest`、`PatchRowResponse`、`StatusFilter`、`applyFilter`、`collectOkIds`、`invertOk`、`patchImportRow` 在前後檔案皆同名同形狀
- **Spec adjustment notes**:
  - Error codes 改為 lowercase snake（`ok_row_not_editable` / `job_not_pending`），對齊既有 `GlobalExceptionHandler` 慣例
  - 不新增 `ImportErrorCodes.java` 檔案（YAGNI；既有不存在）
  - 不寫獨立 EXPIRED IT（與 CANCELLED 共用 `if (status != PENDING)` 分支，CANCELLED 已涵蓋）
  - E2E 沿用 MCP 探索式驗證（前端無 Playwright 安裝）
  - 加裝 vitest 以實現 spec 的 frontend 單元測試承諾

## 執行模式選擇

**Plan complete and saved to `docs/superpowers/plans/2026-06-20-import-preview-enhancements.md`. Two execution options:**

**1. Subagent-Driven（推薦）** — 每個 task 派發 fresh subagent，任務間二段審查（spec + 程式品質），迭代快
**2. Inline Execution** — 在本 session 直接執行；批次 checkpoints

**Which approach?**

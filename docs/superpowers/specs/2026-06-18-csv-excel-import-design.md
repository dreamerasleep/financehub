# Sprint 3 — CSV / Excel 交易匯入 設計文件

> 日期：2026-06-18
> Sprint：S3（Week 6–7）
> PRD 對應：F-003「CSV/Excel 匯入（自動欄位對應 + 重複偵測）」
> 狀態：設計已核可，待寫 implementation plan

---

## 1. 目的與範圍

讓使用者把自家整理好的 CSV / XLSX 一次匯入多筆交易，避免逐筆手動建檔。匯入流程為 **上傳 → 預覽 → 確認**，後端先解析並落到 staging 表，使用者在前端逐列檢視（含錯誤與重複標記）後再 commit 進 `transactions`。

### In scope

- 自家固定欄位 CSV（UTF-8、UTF-8-BOM、`\r\n`/`\n` 混用）
- 自家固定欄位 XLSX（Excel 序號日期 + 字串日期都接受）
- 上傳 → 預覽（每列含 OK / ERROR / DUPLICATE 狀態與訊息）
- 部分匯入（前端 checkbox，預設只勾 OK）
- 重複偵測（同 user + account + type + amount + date + note 之 SHA-256 hash 比對 DB 既存交易與本批次先前列；DUPLICATE 列不可匯入）
- 帳戶與分類以名稱（case-insensitive trim）對應現有資料
- 三種交易類型：INCOME / EXPENSE / TRANSFER（同幣別）

### Out of scope（後續 sprint）

- 銀行月結單格式（CTBC / 玉山 / 國泰）→ 後續可加 parser 實作
- 使用者拖拉欄位對映 UI
- 跨幣別轉帳（待 Sprint 5 匯率服務）
- 模糊 / AI 分類辨識
- 大檔案分批 / 背景 worker（5 MB / 10000 列上限以內以同步處理為主）
- 自動建立不存在的帳戶或分類（一律回 ERROR，使用者改檔）

---

## 2. 整體架構

```
┌──── Frontend ────┐         ┌──── Backend ────┐         ┌── Postgres ──┐
│ ImportPage       │         │ ImportController │         │ import_jobs  │
│  ├ Upload (.csv  │ multipart│  POST /imports  │  JPA    │ import_job_  │
│  │  / .xlsx)     ├────────►│  GET  /imports/ ├────────►│   rows       │
│  ├ PreviewTable  │   JSON   │       {id}      │         │ transactions │
│  └ ConfirmButton ◄──────────┤  POST /imports/ │         │ accounts     │
│                  │          │   {id}/commit   │         │ categories   │
└──────────────────┘          │  POST /imports/ │         └──────────────┘
                              │   {id}/cancel   │
                              │                 │
                              │ ImportJobService│
                              │  ├ Parser (CSV) │
                              │  ├ Parser (XLSX)│
                              │  ├ RowResolver  │
                              │  └ Committer    │
                              │                 │
                              │ ExpiryScheduler │ @Scheduled cron
                              └─────────────────┘
```

### 新增模組

| 路徑 | 用途 |
| ---- | ---- |
| `backend/.../api/imports/` | `ImportController`、`ImportDtos` |
| `backend/.../application/imports/` | `ImportJobService`、`ImportCommitter`、`ImportExpiryJob` |
| `backend/.../infrastructure/parser/` | `TransactionFileParser` 介面、`CsvTransactionFileParser`、`XlsxTransactionFileParser` |
| `backend/.../domain/imports/` | `ImportJob`、`ImportJobRow` JPA entities + enums |
| `frontend/src/pages/ImportPage.tsx` | 上傳 + 預覽頁 |
| `frontend/src/types/import.ts` | DTO 型別 |

### 後端依賴新增

- `org.apache.commons:commons-csv:1.11.0`
- `org.apache.poi:poi-ooxml:5.3.0`（拉 `poi-ooxml-lite`，避免 Schema 全包）

---

## 3. Schema（V4 migration）

```sql
-- V4__import_jobs.sql
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

### 設計取捨

- **`raw_json` 留原列**：使用者問「為什麼這列錯？」時可顯示原值，不用回查檔案
- **`parsed_*` FK 用 ON DELETE SET NULL**：使用者在 preview 期間若刪了 account/category，列在 commit 時 re-resolve 會轉 ERROR，但 job 本身不會炸
- **`dedup_hash`** = `sha256(user_id|account_id|type|amount|date|note)` 與既存交易同算法比對。**不在 `transactions` 加 hash 欄**（避免回填舊資料），只在 import 流程內檢查
- **`expires_at`** 預設 24h，由 `ExpiryScheduler` 每小時掃 PENDING + `expires_at < now()` 標為 EXPIRED
- **檔案本體不存 DB**：`raw_json` 已含完整資料

### CSV / XLSX 固定欄位

| 欄位 | 必填 | 範例 | 說明 |
| ---- | ---- | ---- | ---- |
| `date` | ✅ | `2026-06-18` | ISO 格式字串；Excel 接 date cell |
| `type` | ✅ | `INCOME` / `EXPENSE` / `TRANSFER` | case-insensitive |
| `account` | ✅ | `中信帳戶` | 比對使用者帳戶名（trim + lower） |
| `amount` | ✅ | `1500.00` | 正數；接受逗號分隔 |
| `category` | INCOME/EXPENSE 必填 | `飲食` | TRANSFER 為空字串或省略 |
| `to_account` | TRANSFER 必填 | `玉山儲蓄` | INCOME/EXPENSE 為空字串或省略 |
| `note` | ⛔ | `午餐` | ≤255 字元 |

---

## 4. 解析與資料流

### 4.1 上傳 → 預覽（POST /imports）

1. 認證 + 驗證副檔名（`.csv` / `.xlsx`）+ 檔案大小 ≤ 5 MB
2. 依副檔名挑 parser 解析為 `List<RawRow>`
3. 若列數 > 10000 回 400
4. 對每列跑 `RowResolver`：
   - 解析 date（ISO 字串 / Excel 序號）
   - 解析 amount（去千分位、parse 為 `BigDecimal`，≤0 或非數字 → ERROR）
   - 解析 type（trim + upper，比對 enum）
   - 解析 account / to_account / category（trim + lower 比對使用者擁有者）
   - 校驗 INCOME/EXPENSE 一定要 category、不能有 to_account；TRANSFER 反之，且 to_account ≠ account 且同幣別
   - 算 `dedup_hash` = SHA-256(canonical key)
   - 與 `transactions` 表（本使用者）既存 hash 比對 → DUPLICATE
   - 與本批次先前列 hash 比對 → DUPLICATE
   - 沒任何失敗 → OK；任何失敗 → ERROR 並記第一個錯誤訊息
5. 在單一 `@Transactional` 內存 `import_jobs` + `import_job_rows`，計算 `ok_count`/`error_count`/`dup_count`
6. 回 `201 ImportJobResponse`

### 4.2 預覽（GET /imports/{id}）

- 驗 ownership
- 回 job header + 完整 rows[]（含 raw_json + parsed_* + status + error_message + 解析過的 account/category 顯示名）

### 4.3 確認匯入（POST /imports/{id}/commit）

1. 驗 ownership + status == PENDING（其他 status 回 409）
2. 取得 `rowIds[]`（空 = 全部 OK 列）；過濾出 status == OK 的 rows
3. 用 `SELECT ... FOR UPDATE` lock 該 job 的 rows
4. **Re-resolve**：對每列重新驗 account / category / to_account 仍存在且屬於本使用者
   - 任何一列轉 ERROR → 整個 commit 回 409，並把該列在 DB 標 ERROR（保留 job 為 PENDING，使用者可重新 commit 剩下 OK 列）
5. 全部仍 OK：逐列呼叫既有 `TransactionService.create(...)`，借用既有的餘額同步 + TRANSFER 雙邊邏輯
6. 更新 job：`status = COMMITTED`、`committed_at = now()`
7. 回 `200 ImportCommitResult`（committedCount + transactionIds）

### 4.4 取消（POST /imports/{id}/cancel）

- PENDING → CANCELLED；非 PENDING 回 409

### 4.5 Parser 介面

```java
public interface TransactionFileParser {
    Format supports();           // CSV or XLSX
    List<RawRow> parse(InputStream in) throws IOException;
}
public record RawRow(int rowIndex, Map<String, String> fields) {}
```

- **CsvTransactionFileParser**：Commons CSV `withFirstRecordAsHeader().withTrim().withIgnoreEmptyLines()`，BOM 自動去除
- **XlsxTransactionFileParser**：POI `XSSFReader` streaming，避免 OOM；date cell 用 `DateUtil.getJavaDate` 轉 `LocalDate` 後 `format(ISO_LOCAL_DATE)` 寫入 `fields`；數字 cell 用 `BigDecimal.valueOf(numeric).toPlainString()` 寫入。**所有 cell 在 parser 內統一序列化為字串**，RowResolver 看到的永遠是 `Map<String, String>`

### 4.6 RowResolver 失敗碼

| 失敗條件 | error_message |
| -------- | ------------- |
| `date` 無法解析 | `Invalid date format: <raw>` |
| `type` 不在 enum | `Unknown type: <raw>` |
| `amount` ≤ 0 或非數字 | `Amount must be positive number` |
| account 名找不到 | `Account not found: <name>` |
| category 名找不到 / kind 不符 | `Category not found or kind mismatch: <name>` |
| TRANSFER 無 to_account | `Transfer requires to_account` |
| TRANSFER 跨幣別 | `Cross-currency transfer not supported` |
| INCOME/EXPENSE 帶 to_account | `to_account only allowed for TRANSFER` |
| account == to_account | `Transfer source and destination must differ` |
| DB 既存 hash 相同 | status = `DUPLICATE`（非 ERROR） |
| 批次內 hash 相同 | status = `DUPLICATE` |

---

## 5. API 與前端

### 5.1 Backend endpoints

| Method | Path | Body / Param | 回應 |
| ------ | ---- | ------------ | ---- |
| `POST` | `/api/v1/imports` | `multipart/form-data file` | `201 ImportJobResponse` |
| `GET` | `/api/v1/imports/{id}` | — | `200 ImportJobDetailResponse`（header + rows[]） |
| `GET` | `/api/v1/imports` | `?status=PENDING` 可選 | `200 ImportJobResponse[]`（最近 20 筆） |
| `POST` | `/api/v1/imports/{id}/commit` | `{ rowIds?: number[] }` 為空 = 全部 OK | `200 ImportCommitResult` |
| `POST` | `/api/v1/imports/{id}/cancel` | — | `204` |

### 5.2 錯誤碼

- `400`：檔案缺 header / 空檔 / 列數 > 10000
- `401`：未登入
- `404`：job 不存在或非本人
- `409`：commit 時 job 非 PENDING、或 re-resolve 發現新 ERROR（body 含 updated rows）
- `413`：檔案 > 5 MB
- `415`：副檔名非 `.csv` / `.xlsx`

### 5.3 設定

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 5MB
      max-request-size: 5MB
financehub:
  import:
    max-rows: 10000
    job-ttl: PT24H
    expiry-cron: "0 0 * * * *"  # 每小時整點
```

### 5.4 Frontend

新增 `frontend/src/pages/ImportPage.tsx`（`/import`，包在 ProtectedRoute 內）：

```
┌─ 上半部：AntD Upload.Dragger ─────────────────────┐
│  拖放 .csv / .xlsx 或點擊上傳                       │
│  下方 hint：「期望欄位：date, type, account, ...」  │
│  支援格式說明連結 → docs 對應頁                     │
└────────────────────────────────────────────────────┘
┌─ 下半部：Preview Table（jobId 有值時顯示）─────────┐
│  Statistic 卡：總列數 / OK / 錯誤 / 重複            │
│  Table：狀態 tag | row# | date | type | account |  │
│         amount | category/to_account | note | err  │
│  顏色：OK 綠 / ERROR 紅 / DUPLICATE 橘             │
│  Checkbox 預設只勾 OK；ERROR / DUPLICATE 不可勾    │
│  右上：[取消]  [確認匯入（N 筆）]                  │
└────────────────────────────────────────────────────┘
```

導覽列加「匯入」入口，放在「交易」右側。Commit 成功 toast「已匯入 N 筆」並跳 `/transactions`。

### 5.5 React Query

- `['imports']` — 列表
- `['imports', jobId]` — 預覽
- Mutations：`uploadImport`、`commitImport`、`cancelImport`；commit 成功後 `invalidateQueries(['transactions'])` + `['accounts']`

---

## 6. 測試策略

### 6.1 Backend（Testcontainers + Spring Boot Test）

**TransactionFileParserTest（單元）**
1. CSV：標準 7 欄完整檔（5 列）解析成功
2. CSV：BOM 開頭 / `\r\n` / `\n` 混用都接受
3. CSV：缺欄、空檔、只剩 header
4. XLSX：日期 cell（Excel 序號）正確轉 LocalDate
5. XLSX：千位逗號 / 字串型數字都接受
6. XLSX：streaming 讀大檔（mock 1 萬列）不 OOM

**ImportJobServiceIT**

7. 上傳 OK：3 INCOME + 2 EXPENSE + 1 TRANSFER → row_count=6, ok=6
8. 混合：5 OK + 2 ERROR + 1 DUPLICATE → 各計數正確、raw_json 保留
9. 帳戶名找不到 → row.status=ERROR
10. 分類 kind 不符 → ERROR
11. TRANSFER 跨幣別 → ERROR
12. 批次內 hash 重複 → 第二列 DUPLICATE
13. 與 DB 既存交易重複 → DUPLICATE
14. GET 非本人 job → 404
15. 列數 > 10000 → 400

**ImportCommitIT**

16. Commit 全 OK：transactions 正確建立、餘額同步（含 TRANSFER 雙邊）
17. Commit 指定 rowIds：只插指定列
18. Commit 後 job → COMMITTED、再次 commit 回 409
19. Commit re-resolve 帳戶被刪 → 409 + 該列轉 ERROR
20. CANCEL：job → CANCELLED、不可再 commit
21. ExpiryScheduler：mock clock 推 25h → PENDING → EXPIRED

**ImportControllerIT**

22. 未登入 → 401
23. 跨使用者讀別人 job → 404
24. 副檔名 `.txt` → 415
25. 檔案 > 5 MB → 413

### 6.2 Frontend

- 既有 lint + typecheck CI
- Playwright E2E：
  - 上傳 fixture CSV → 預覽 3 OK + 1 ERROR + 1 DUPLICATE
  - 取消勾選一列 → commit → 交易頁筆數 + 帳戶餘額正確
  - 再上傳同份 CSV → 所有列 DUPLICATE

### 6.3 Fixtures

- `backend/src/test/resources/imports/sample-mixed.csv`
- `backend/src/test/resources/imports/sample-transfer.xlsx`
- `backend/src/test/resources/imports/sample-large.csv`（10001 列邊界）

### 6.4 目標

- Backend IT：20 → 39（+19：ImportJobServiceIT 9、ImportCommitIT 6、ImportControllerIT 4）
- 新增 Parser 單元測試 6 個（不算 IT）
- 不增前端自動化測試（後續 sprint 統一補 Vitest）

---

## 7. 任務切片

依依賴排序，建議 6 個 commit。

| # | 切片 | 動作 |
| - | ---- | ---- |
| **S3-T01** | V4 migration + JPA entities + repositories |
| **S3-T02** | `TransactionFileParser` 介面 + CsvParser + commons-csv 依賴 |
| **S3-T03** | RowResolver（account/category 比對、type 規則、dedup hash） |
| **S3-T04** | XlsxParser + poi-ooxml 依賴 |
| **S3-T05** | ImportJobService 建檔（POST /imports） |
| **S3-T06** | 查詢 API（GET /imports、GET /imports/{id}、ownership） |
| **S3-T07** | Commit + Cancel（含 re-resolve、呼叫 TransactionService） |
| **S3-T08** | ExpiryScheduler |
| **S3-T09** | API 限制（5MB、10000 列、415/413） |
| **S3-T10** | Frontend Upload 元件 + 路由 + 導覽列入口 |
| **S3-T11** | Frontend Preview Table（statistic、tag、checkbox 規則） |
| **S3-T12** | E2E 驗證（Playwright + fixture） |
| **S3-T13** | 文件同步（user-guide/import、api-reference/imports、database V4、changelog、index） |

**依賴鏈**：T01 → T02 / T03 / T04 → T05 → T06 / T07 / T08 → T09 → T10 → T11 → T12 → T13

**Commit 切點**：
- C1：T01–T03（schema + CSV parser + resolver）
- C2：T04–T05（XLSX + import 建檔）
- C3：T06–T09（API 完整 + scheduler + 限制）
- C4：T10–T11（前端 UI）
- C5：T12（E2E + 修正）
- C6：T13（docs）

**估算**：5–6 個工作日。

---

## 8. 已知風險

| 風險 | 緩解 |
| ---- | ---- |
| POI 在大 XLSX 預設讀法吃記憶體 | 用 `XSSFReader` streaming + SAX |
| Commit 中途 transaction 失敗 | 既有 `TransactionService.create` 在自己的 `@Transactional`；commit 整批包外層 transaction，部分失敗整批 rollback；回前端顯示哪列卡住 |
| 重複 hash 在使用者改了 note 後失效 | 是 feature：使用者改了 note 即視為不同交易，避免擋住「真的同金額同日不同事」 |
| 並發匯入兩個重疊檔 | commit 時 re-check hash + `SELECT ... FOR UPDATE` lock job rows；最壞情況一邊先成功、另一邊把那列轉 DUPLICATE |
| 帳戶刪除 cascade 至 import_job_rows | FK ON DELETE SET NULL，列在 commit 時 re-resolve 會抓到 |

---

## 9. 後續延伸（非本 sprint）

- 銀行月結單 parser（per-bank 實作 `TransactionFileParser`）
- 使用者自訂欄位對映 UI
- 跨幣別轉帳（Sprint 5 匯率服務上線後）
- 自動分類規則引擎（note 關鍵字 → category）
- 大檔背景 worker（>5 MB 切片）

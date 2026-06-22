# Sprint 3.5 — Import 預覽頁強化 Design

> **狀態**：草稿（待使用者複核）
> **日期**：2026-06-20
> **依賴**：Sprint 3（CSV/XLSX import）已上線（main 已含 `import_jobs` / `import_job_rows` V4）

## 目標

讓使用者在 `/import` 預覽頁直接修復 ERROR 與 DUPLICATE 列，無需重新上傳整個檔案；同時補上批次選取與狀態篩選兩個常用操作，降低 20+ 列匯入時的點擊成本。

不在範圍：欄位拖拉對映、銀行月結單格式、模糊分類比對、commit 後的撤銷功能。這些保留給未來 sprint。

## 範圍三件事

1. **批次選取**：toolbar 加「全選 OK 列」「反選」「清空」三顆按鈕，跨頁有效；移除既有的隱式 fallback bug。
2. **狀態篩選**：Radio.Group 在 OK / ERROR / DUPLICATE / 全部 之間切換；client-side 過濾，不影響選取集合。
3. **錯誤列就地編輯**：ERROR / DUPLICATE 列尾加「編輯」開右側 Drawer；儲存後後端重跑 `RowResolver` 並回傳更新後的 row + job counters。

OK 列**不可編輯**（避免使用者誤改本來就正確的資料）。

## 架構

### 後端：新增單一 PATCH 端點

`PATCH /api/v1/imports/{jobId}/rows/{rowId}`

| 元件 | 動作 |
| --- | --- |
| `ImportController` | 新增 PATCH handler，驗 auth、呼叫 service、回 200 |
| `ImportJobService.patchRow` | `@Transactional`；取 job + row、驗狀態、載 lookup maps、跑 RowResolver、寫回 row、重算 job counters |
| `RowResolver` | 不改動，直接重用 |
| `DedupHash` | 不改動 |

不新增 Flyway migration。`raw_json` 欄位在每次 PATCH 時覆寫成新 body 以便日後查詢；`parsed_*` / `status` / `error_message` / `dedup_hash` 隨 resolver 結果重寫。

### 前端：拆出兩個元件 + 強化 ImportPage

| 元件 | 責任 |
| --- | --- |
| `ImportToolbar` (NEW) | Radio.Group filter + 三顆 bulk 按鈕；接收 rows、selection state 及 setters |
| `ImportRowEditDrawer` (NEW) | AntD Drawer + Form；包 PATCH mutation；成功時更新 query cache |
| `ImportPage` (M) | 容器；持有 filter / selection / activeEditRowId state；移除 `effectiveSelection` fallback bug |

不引入新 runtime 依賴。沿用 TanStack Query、AntD 5、react-router-dom。

## API 契約

### Request

```http
PATCH /api/v1/imports/123/rows/45
Content-Type: application/json
Authorization: Bearer <jwt>

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

七個欄位皆為字串，與 CSV upload 的 raw row 同形狀。null 與空字串等價，全交給 `RowResolver` 解。

### Response 200

```json
{
  "row": {
    "id": 45,
    "rowIndex": 5,
    "status": "OK",
    "parsedDate": "2026-06-20",
    "parsedType": "EXPENSE",
    "parsedAmount": 150.00,
    "parsedAccountId": 12,
    "parsedToAccountId": null,
    "parsedCategoryId": 7,
    "parsedNote": "午餐",
    "errorMessage": null
  },
  "job": {
    "id": 123,
    "filename": "june.csv",
    "format": "CSV",
    "status": "PENDING",
    "rowCount": 10,
    "okCount": 8,
    "errorCount": 1,
    "dupCount": 1,
    "createdAt": "...",
    "expiresAt": "..."
  }
}
```

### Error responses

| HTTP | code | 觸發條件 |
| --- | --- | --- |
| 404 | `JOB_NOT_FOUND` | job 不存在或不屬於 caller（不洩漏存在） |
| 404 | `ROW_NOT_FOUND` | rowId 不屬於該 job |
| 409 | `JOB_NOT_PENDING` | job.status ≠ PENDING（COMMITTED / CANCELLED / EXPIRED）|
| 403 | `OK_ROW_NOT_EDITABLE` | 嘗試編輯 status=OK 的列 |
| 400 | `VALIDATION_ERROR` | request body 結構錯誤（缺欄位、非字串）|

注意：欄位內容錯誤（amount 非數字、account 不存在等）**不**回 4xx，而是 200 + row.status=ERROR + error_message，與上傳行為一致。

## 後端細節

### `ImportJobService.patchRow(userId, jobId, rowId, body)`

```text
1. job = jobRepo.findById(jobId).orElseThrow → 404
2. if job.userId ≠ userId → 404 (avoid enumeration)
3. if job.status ≠ PENDING → 409 JOB_NOT_PENDING
4. row = rowRepo.findByIdAndJobId(rowId, jobId).orElseThrow → 404
5. if row.status == OK → 403 OK_ROW_NOT_EDITABLE
6. accountsByNameLower = accountRepo.findAllByUserId(userId) → map name.lower → id
7. currencyByAccountId = ... → map id → ISO currency
8. categoriesByKeyLower = categoryRepo.findVisibleTo(userId)
       → map (name.lower + "|" + kind) → id
9. existingDbHashes = txnRepo.findHashesByUserId(userId)  // 同 upload 流程
10. batchHashesSoFar = rowRepo.findOkHashesByJobIdExcept(jobId, rowId)
11. raw = new RawRow(row.rowIndex, body)
12. resolved = rowResolver.resolve(userId, raw, ...all 6 maps...)
13. row.applyResolved(resolved)  // 寫 parsed_*, status, error_message, dedup_hash
14. row.rawJson = body (覆寫)
15. recomputeCounters(job):
       row_count 不變
       ok_count = rowRepo.countByJobIdAndStatus(jobId, OK)
       error_count = ... ERROR
       dup_count = ... DUPLICATE
16. return { row, job }  // Spring 自動 flush
```

### 不 retroactively re-resolve 其他列的取捨

若 A 與 B 在同 job 內 dedup_hash 相同（A=OK、B=DUP）：使用者編輯 A 改了 amount 後 A 的 hash 變了，但 B 仍維持 DUP。**設計上接受**，原因：

- 重跑全 job 成本與語意複雜度都不合算
- 使用者要救 B 可以自己再點編輯
- 使用者文件須註明

若日後資料顯示這情境很常見，可加 `POST /api/v1/imports/{jobId}/revalidate-all` 補救，但目前 YAGNI。

### Repository 新方法

```java
// ImportJobRowRepository
Set<String> findOkHashesByJobIdExcept(Long jobId, Long excludeRowId);
long countByJobIdAndStatus(Long jobId, ImportJobRowStatus status);
```

兩個都用 JPQL，避免 N+1。

## 前端細節

### Filter（client-side）

```ts
type StatusFilter = 'ALL' | 'OK' | 'ERROR' | 'DUPLICATE'

const visibleRows = useMemo(
  () => filter === 'ALL' ? rows : rows.filter(r => r.status === filter),
  [rows, filter],
)
```

`Table` 拿 `visibleRows` 渲染；但 `rowSelection.selectedRowKeys` 仍用 component state 的 `selectedRowIds`（與 filter 解耦）。被 filter 隱藏的列若已勾選，commit 時照送。

### 批次選取按鈕

```ts
const allOkIds = rows.filter(r => r.status === 'OK').map(r => r.id)

selectAllOk:  () => setSelectedRowIds(allOkIds)
invertOk:     () => setSelectedRowIds(
                xorById(selectedRowIds, allOkIds))
clearAll:     () => setSelectedRowIds([])
```

「反選」只在 OK 集合內做 XOR；ERROR / DUP 永遠不可選，不被反選影響。

### 移除 effectiveSelection bug

現況：
```ts
const effectiveSelection = selectedRowIds.length > 0
    ? selectedRowIds
    : defaultSelectedKeys  // ← bug：使用者按「清空」後仍會被預設覆寫
```

改成：上傳成功 callback 直接 `setSelectedRowIds(okRowsFromResponse)`，自此使用者的選取狀態為 single source of truth。`commitMutation` 直接讀 `selectedRowIds`，不再 fallback。

### Edit Drawer

```
ImportRowEditDrawer
├── open: activeEditRowId !== null
├── row: rows.find(r => r.id === activeEditRowId)
├── Form initialValues: 從 row.rawJson 反推（json field → form field）
├── 欄位
│   ├── date           DatePicker      required
│   ├── type           Select          required, options=[INCOME, EXPENSE, TRANSFER]
│   ├── account        Select          required, options=來自 useQuery(['accounts'])
│   ├── amount         InputNumber     required, precision=2, min=0.01
│   ├── category       Select          options=來自 useQuery(['categories'])，
│   │                                  依 type 過濾 kind；type=TRANSFER 時 disabled+清空
│   ├── to_account     Select          options=同 account 但排除 account 值；
│   │                                  type=TRANSFER 時 required，其他 disabled+清空
│   └── note           Input           max=255
├── onFinish → useMutation(patchImportRow)
│   ├── success: queryClient.setQueryData(['imports', jobId], { job, rows: 替換該列 })
│   │            若 updated.status === 'OK'：setSelectedRowIds(prev => [...prev, updated.id])
│   │            toast 顯示新狀態
│   │            關 Drawer
│   └── error: message.error，Drawer 不關
└── Footer: [取消] [儲存並重新驗證]
```

### Category API

`GET /api/v1/categories` 已存在（`CategoryController`）、前端 `api/categories.ts` 的 `listCategories()` 已可用。Drawer 直接 `useQuery({ queryKey: ['categories'], queryFn: listCategories })`，依 `type` 過濾顯示。

### testid 規劃

| testid | 元件 |
| --- | --- |
| `import-filter-radio` | Radio.Group |
| `import-bulk-select-ok` | 「全選 OK 列」按鈕 |
| `import-bulk-invert` | 「反選」按鈕 |
| `import-bulk-clear` | 「清空」按鈕 |
| `import-edit-row-{rowId}` | 每列「編輯」連結 |
| `import-edit-drawer` | Drawer 容器 |
| `import-edit-submit` | 「儲存並重新驗證」按鈕 |

## 測試策略

### Backend IT — `ImportRowPatchIT`

| # | 情境 | 期望 |
| --- | --- | --- |
| 1 | PATCH ERROR 列改成合法欄位 | row→OK；job.okCount +1 / errorCount -1 |
| 2 | PATCH ERROR 列改完撞 DB 既存 transaction | row→DUPLICATE；dupCount +1 / errorCount -1 |
| 3 | PATCH DUP 列改 amount 變唯一 | row→OK；okCount +1 / dupCount -1 |
| 4 | PATCH OK 列 | 403 OK_ROW_NOT_EDITABLE，DB 不變 |
| 5 | PATCH 不屬於該 job 的 rowId | 404 ROW_NOT_FOUND |
| 6 | PATCH 別人的 job | 404 JOB_NOT_FOUND（不洩漏存在） |
| 7 | PATCH COMMITTED 的 job | 409 JOB_NOT_PENDING |
| 8 | PATCH EXPIRED 的 job | 409 JOB_NOT_PENDING |
| 9 | PATCH body 缺必填（date 空字串） | 200；row→ERROR；error_message="Date is required" |
| 10 | A=OK B=DUP 同 hash，改 A 的 amount | A 仍 OK；B 仍 DUP（驗證不 retroactively flip）|
| 11 | counters 一致性 | okCount + errorCount + dupCount = rowCount |

### Backend Unit — `RowResolverPatchHashTest`

- 同 user/account/type/amount/date/note 兩列 hash 相等
- note=null 與 note="" hash 相等（canonicalization）

### Frontend Unit — `ImportPage.helpers.test.ts`

- `applyFilter(rows, 'OK')` 只回 OK 列
- `selectAllOk(rows)` 跨頁返回所有 OK rowKey
- `invertOk(selected, allOkIds)` 在 OK 集合內 XOR
- 被 filter 隱藏但已勾選的 row id 仍出現在 commit payload

### E2E — `frontend/e2e/imports-edit.spec.ts`

1. **錯誤列編輯成 OK 並自動勾選**：上傳 2 OK + 1 ERROR（amount=`abc`）→ 編輯 → 改 amount=100 → toast「列 #N 現為 OK，已自動勾選」→ commit → /transactions 看到 3 筆
2. **DUP 列改 note 仍 DUP**：上傳含撞 transaction 列 → 編輯但只改 note → toast「仍為 DUPLICATE」→ row 仍紅
3. **狀態 filter**：上傳 2 OK + 1 ERR + 1 DUP → 切到 OK → 表格 2 列 → 切回全部 → 4 列
4. **批次選取**：3 OK + 1 ERR → 「全選 OK 列」→ 3 勾 → 「清空」→ 0 → 「全選 OK 列」→ commit 按鈕「確認匯入（3）」
5. **跨頁選取保留**：mock 22 列全 OK → 全選 OK → 翻第 2 頁 → 第 2 頁 2 列仍勾 → commit 22 筆全進帳

## 檔案結構與任務切片

### 檔案異動

```
backend/src/main/java/com/financehub/
├── api/imports/
│   ├── ImportController.java               (M) +PATCH /rows/{rowId}
│   ├── ImportDtos.java                     (M) +PatchRowRequest record
│   └── ImportErrorCodes.java               (M) +OK_ROW_NOT_EDITABLE, JOB_NOT_PENDING
├── application/imports/
│   └── ImportJobService.java               (M) +patchRow(...)
└── domain/imports/
    └── ImportJobRowRepository.java         (M) +findOkHashesByJobIdExcept, countByJobIdAndStatus

backend/src/test/java/com/financehub/
├── api/imports/ImportRowPatchIT.java       (NEW) 11 案
└── application/imports/RowResolverPatchHashTest.java  (NEW) 2 案

frontend/src/
├── api/imports.ts                          (M) +patchImportRow()
├── types/import.ts                         (M) +PatchRowRequest, PatchRowResponse
├── pages/ImportPage.tsx                    (M) toolbar/filter/移除 fallback bug
├── components/import/
│   ├── ImportToolbar.tsx                   (NEW)
│   └── ImportRowEditDrawer.tsx             (NEW)
└── pages/__tests__/ImportPage.helpers.test.ts  (NEW)

frontend/e2e/imports-edit.spec.ts           (NEW)

docs-site/docs/user-guide/import.md         (M) 「修正錯誤列」「篩選與批次選取」段落
docs-site/docs/api-reference/imports.md     (M) PATCH endpoint
docs-site/docs/changelog.md                 (M) [Unreleased] — Sprint 3.5
docs/workbook.md                            (M) S3.5 條目
```

### 任務切片（7 task / 4 commit）

| Task | 內容 | Commit |
| --- | --- | --- |
| T01 | Backend: DTO + error codes + ImportJobService.patchRow + RowResolverPatchHashTest 2 案 | C1 |
| T02 | Backend: PATCH controller + ImportRowPatchIT 11 案 | C1 |
| T03 | Frontend: api/imports.ts +patchImportRow + types/import.ts | C2 |
| T04 | Frontend: ImportToolbar 元件 + helpers 單元測試 | C2 |
| T05 | Frontend: ImportRowEditDrawer + ImportPage 整合 + 移除 fallback bug | C3 |
| T06 | E2E: imports-edit.spec.ts 5 案 | C4 |
| T07 | Docs sync（4 檔） | C4 |

### 分支與整合

- 分支：`feature/sprint-3.5-import-preview`
- 從 `main`（231ee66 後）切出
- 完成後 FF merge 回 main、推 origin（依使用者明確同意）

## Global Constraints

- Java 21 / Spring Boot 3.3.4 / Spring Security 6 / JPA / Flyway — 無 schema 變動
- Testcontainers Postgres IT 模式不變（current 39/39 綠，目標 39+11=50 綠）
- React 19 / AntD 5 / TanStack Query 5 / Vite — 不引入新 runtime 依賴
- 程式碼註解預設不寫；commit 訊息英文、動詞開頭；使用者預設繁中
- 不新增 Flyway migration
- 不破壞 Playwright 既有 5 案（4 + 5 新 = 9 案）
- API 路徑保持 `/api/v1/imports/...`，與既有同 prefix
- 錯誤 code 維持 SCREAMING_SNAKE_CASE 命名

## 驗收條件

- Backend：所有 IT（含新增 11 案）綠；`./mvnw test` 結束碼 0
- Frontend：`npm run test` + `npm run typecheck` + `npm run lint` 三者綠
- E2E：`npx playwright test` 全 9 案綠
- 手動：在 `localhost:5173/import` 操作走過 §3 的 5 個 E2E 情境，行為一致
- 文件：`uv run mkdocs build --strict` 綠
